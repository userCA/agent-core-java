package io.agentcore.agent;

import io.agentcore.model.Content.TextContent;
import io.agentcore.model.Message.*;
import io.agentcore.extensions.Extension;
import io.agentcore.extensions.ExtensionRunner;
import io.agentcore.extensions.HookTypes.BeforeAgentStartResult;
import io.agentcore.llm.*;
import io.agentcore.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import io.agentcore.model.AgentEvent;
import io.agentcore.model.Message;

/**
 * High-level Agent facade — stateful wrapper around AgentLoop.
 *
 * <p>Mirrors Python {@code agent_core/core/agent.py} Agent class.
 * Integrates {@link ExtensionRunner} for unified hook dispatch via the
 * Extension SPI (both manual registration and ServiceLoader discovery).
 */
public class Agent implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(Agent.class);

    private final AgentLoopConfig baseConfig;
    private final AgentContext context;
    private final AtomicBoolean abortSignal = new AtomicBoolean(false);
    private final AgentEventDispatcher eventDispatcher = new AgentEventDispatcher();

    // Extension-based hook system (unified)
    private final ExtensionRunner extensionRunner;

    private final PendingMessageQueue steeringQueue;
    private final PendingMessageQueue followUpQueue;

    // Cached base config with hooks — reused across runs
    private volatile AgentLoopConfig cachedBaseConfig;

    private final ToolCallTracker toolCallTracker = new ToolCallTracker();

    // Shared ToolRunner with pooled ExecutorService (lazy-initialized)
    private volatile ToolRunner sharedToolRunner;

    // Shared StreamAccumulator (lazy-initialized, config updated per run)
    private volatile StreamAccumulator sharedStreamAccumulator;

    // Concurrency guard: prevents simultaneous prompt/continue calls
    private final AtomicBoolean running = new AtomicBoolean(false);

    public Agent(AgentLoopConfig config) {
        this(config, new AgentContext(), List.of());
    }

    public Agent(AgentLoopConfig config, AgentContext context) {
        this(config, context, List.of());
    }

    public Agent(AgentLoopConfig config, AgentContext context, List<Extension> extensions) {
        this(config, context, extensions,
                PendingMessageQueue.DrainMode.ONE_AT_A_TIME,
                PendingMessageQueue.DrainMode.ONE_AT_A_TIME);
    }

    /**
     * Full constructor with configurable queue drain modes.
     *
     * @param config        agent loop configuration
     * @param context       agent context (state)
     * @param extensions    extension SPI list
     * @param steeringMode  drain mode for steering messages (mirrors pi-mono steeringMode)
     * @param followUpMode  drain mode for follow-up messages (mirrors pi-mono followUpMode)
     */
    public Agent(AgentLoopConfig config, AgentContext context, List<Extension> extensions,
                 PendingMessageQueue.DrainMode steeringMode,
                 PendingMessageQueue.DrainMode followUpMode) {
        this.baseConfig = config;
        this.context = context;
        this.extensionRunner = new ExtensionRunner(
                extensions != null ? extensions : List.of());
        this.steeringQueue = new PendingMessageQueue(steeringMode);
        this.followUpQueue = new PendingMessageQueue(followUpMode);
    }

    // ── Subscribe ──────────────────────────────────────────

    public Runnable subscribe(Consumer<AgentEvent> listener) {
        return eventDispatcher.subscribe(listener);
    }

    // ── Prompt / Continue / Abort ──────────────────────────

    public List<Message> prompt(String text, Consumer<AgentEvent> onEvent) {
        return prompt(text, onEvent, null);
    }

    public List<Message> prompt(String text, Consumer<AgentEvent> onEvent,
                                AgentLoopConfig.CompactCallback compactCallback) {
        UserMessage userMsg = new UserMessage(
                List.of(new TextContent(text)),
                Message.nowEpochSeconds());

        // Run before-agent-start hooks via extensions
        runBeforeAgentStartHooks(text);

        return runLoop(List.of(userMsg), onEvent, compactCallback);
    }

    /**
     * Send a pre-built message (e.g. multi-modal with images) and run the agent loop.
     *
     * @param message  the message to send (typically a UserMessage with rich content)
     * @param onEvent  event consumer (nullable)
     * @return assistant messages produced during this run
     */
    public List<Message> prompt(Message message, Consumer<AgentEvent> onEvent) {
        return prompt(message, onEvent, null);
    }

    /**
     * Send a pre-built message with optional compaction callback.
     */
    public List<Message> prompt(Message message, Consumer<AgentEvent> onEvent,
                                AgentLoopConfig.CompactCallback compactCallback) {
        runBeforeAgentStartHooks(message instanceof UserMessage um
                ? um.content().stream()
                    .filter(c -> c instanceof TextContent)
                    .map(c -> ((TextContent) c).text())
                    .findFirst().orElse("")
                : "");
        return runLoop(List.of(message), onEvent, compactCallback);
    }

    public List<Message> continueLoop(Consumer<AgentEvent> onEvent) {
        return continueLoop(onEvent, null);
    }

    @SuppressWarnings("deprecation") // single atomic ops on synchronizedList are safe
    public List<Message> continueLoop(Consumer<AgentEvent> onEvent,
                                      AgentLoopConfig.CompactCallback compactCallback) {
        // Validate preconditions (mirrors pi-mono agentLoopContinue)
        if (context.messages().isEmpty()) {
            throw new IllegalStateException("Cannot continue: no messages in context");
        }
        Message last = context.messages().getLast();
        if (last instanceof AssistantMessage) {
            throw new IllegalStateException("Cannot continue from message role: assistant");
        }
        return runLoop(List.of(), onEvent, compactCallback);
    }

    public void abort() {
        abortSignal.set(true);
    }

    // ── Async variants (virtual-thread backed) ──────────────────

    private static final java.util.concurrent.ExecutorService VIRTUAL_EXECUTOR =
            Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Async version of {@link #prompt(String, Consumer)}.
     * Runs the agent loop on a virtual thread.
     */
    public CompletableFuture<List<Message>> promptAsync(String text, Consumer<AgentEvent> onEvent) {
        return promptAsync(text, onEvent, null);
    }

    /**
     * Async version of {@link #prompt(String, Consumer, AgentLoopConfig.CompactCallback)}.
     */
    public CompletableFuture<List<Message>> promptAsync(String text, Consumer<AgentEvent> onEvent,
                                                         AgentLoopConfig.CompactCallback compactCallback) {
        return CompletableFuture.supplyAsync(() -> prompt(text, onEvent, compactCallback), VIRTUAL_EXECUTOR);
    }

    /**
     * Async version of {@link #prompt(Message, Consumer)}.
     */
    public CompletableFuture<List<Message>> promptAsync(Message message, Consumer<AgentEvent> onEvent) {
        return promptAsync(message, onEvent, null);
    }

    /**
     * Async version of {@link #prompt(Message, Consumer, AgentLoopConfig.CompactCallback)}.
     */
    public CompletableFuture<List<Message>> promptAsync(Message message, Consumer<AgentEvent> onEvent,
                                                         AgentLoopConfig.CompactCallback compactCallback) {
        return CompletableFuture.supplyAsync(() -> prompt(message, onEvent, compactCallback), VIRTUAL_EXECUTOR);
    }

    /**
     * Async version of {@link #continueLoop(Consumer)}.
     */
    public CompletableFuture<List<Message>> continueLoopAsync(Consumer<AgentEvent> onEvent) {
        return continueLoopAsync(onEvent, null);
    }

    /**
     * Async version of {@link #continueLoop(Consumer, AgentLoopConfig.CompactCallback)}.
     */
    public CompletableFuture<List<Message>> continueLoopAsync(Consumer<AgentEvent> onEvent,
                                                               AgentLoopConfig.CompactCallback compactCallback) {
        return CompletableFuture.supplyAsync(() -> continueLoop(onEvent, compactCallback), VIRTUAL_EXECUTOR);
    }

    /**
     * Provide human input for a pending HITL (Human-in-the-Loop) request.
     * Resolves the blocking future created by {@link HumanInputGate#requireInput(String)}.
     *
     * @param toolCallId  the tool_call_id from the HumanInputRequired event
     * @param values      the user-provided input values
     * @return true if a pending request was found and resolved
     */
    public boolean provideHumanInput(String toolCallId, Map<String, Object> values) {
        HumanInputGate gate = baseConfig.humanInputGate();
        if (gate == null) {
            log.warn("provideHumanInput called but no HumanInputGate configured");
            return false;
        }
        return gate.provideInput(toolCallId, values);
    }

    // ── Runtime message injection ──────────────────────────

    public void steer(Message message) {
        steeringQueue.enqueue(message);
    }

    public void followUp(Message message) {
        followUpQueue.enqueue(message);
    }

    public boolean hasQueuedMessages() {
        return steeringQueue.hasItems() || followUpQueue.hasItems();
    }

    public void clearAllQueues() {
        steeringQueue.clear();
        followUpQueue.clear();
    }

    /**
     * Get the current steering queue drain mode.
     */
    public PendingMessageQueue.DrainMode steeringMode() {
        return steeringQueue.mode();
    }

    /**
     * Get the current follow-up queue drain mode.
     */
    public PendingMessageQueue.DrainMode followUpMode() {
        return followUpQueue.mode();
    }

    /**
     * Change the steering queue drain mode at runtime.
     */
    public void setSteeringMode(PendingMessageQueue.DrainMode mode) {
        steeringQueue.setMode(mode);
    }

    /**
     * Change the follow-up queue drain mode at runtime.
     */
    public void setFollowUpMode(PendingMessageQueue.DrainMode mode) {
        followUpQueue.setMode(mode);
    }

    // ── State accessors ────────────────────────────────────

    public boolean isStreaming() { return context.isStreaming(); }
    public String errorMessage() { return context.errorMessage(); }
    public Set<String> pendingToolCalls() { return toolCallTracker.snapshot(); }
    public List<Message> messages() { return context.messagesSnapshot(); }
    public AgentContext context() { return context; }
    public ExtensionRunner extensionRunner() { return extensionRunner; }

    /**
     * Reset state for a new conversation, preserving ToolRunner for reuse.
     * Clears messages, queues, signals, and pending tool calls.
     */
    public void reset() {
        context.resetState();
        abortSignal.set(false);
        running.set(false);
        toolCallTracker.clear();
        steeringQueue.clear();
        followUpQueue.clear();
        // ToolRunner intentionally NOT closed — reused by subsequent prompt calls
    }

    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Permanently close the agent and release all resources.
     * After close(), the agent cannot be used again.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        reset();
        closeToolRunner();
    }

    // ── Factory ────────────────────────────────────────────

    public static Agent create(
            ModelProvider provider,
            ModelInfo model,
            AuthSource authSource,
            ToolRegistry toolRegistry,
            String systemPrompt) {

        // Use polymorphic converter selection — each provider supplies its own
        AgentLoopConfig.ConvertToLlm converter = provider.createMessageConverter()::apply;

        AgentLoopConfig config = AgentLoopConfig.builder()
                .model(model)
                .streamFn(provider::stream)
                .convertToLlm(converter)
                .authResolver(authSource::resolve)
                .toolRegistry(toolRegistry)
                .build();

        AgentContext context = new AgentContext(systemPrompt, new ArrayList<>());
        return new Agent(config, context);
    }

    // ── Internal ───────────────────────────────────────────

    /**
     * Common loop execution logic shared by prompt() and continueLoop().
     */
    private List<Message> runLoop(List<Message> newMessages,
                                  Consumer<AgentEvent> onEvent,
                                  AgentLoopConfig.CompactCallback compactCallback) {
        // Concurrency guard (mirrors pi-mono activeRun)
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException(
                    "Agent is already processing a prompt. Use steer() or followUp() to queue messages.");
        }

        abortSignal.set(false);
        context.setErrorMessage(null);
        context.tryStartStreaming();

        // Guard against duplicate AgentEnd emission
        AtomicBoolean agentEndEmitted = new AtomicBoolean(false);
        Consumer<AgentEvent> emitter = eventDispatcher.createEmitter(onEvent);
        Consumer<AgentEvent> guardedEmitter = evt -> {
            if (evt instanceof AgentEvent.AgentEnd) {
                if (!agentEndEmitted.compareAndSet(false, true)) {
                    log.warn("Duplicate AgentEnd event suppressed");
                    return;
                }
            }
            emitter.accept(evt);
        };

        AgentLoopConfig config = buildConfigWithHooks(compactCallback);
        ToolRunner runner = getOrCreateToolRunner(config);
        // Always sync hooks — ToolRunner is shared, but config/hooks may change per run
        if (runner != null) {
            runner.updateHooks(config.beforeToolCall(), config.afterToolCall());
        }
        StreamAccumulator accumulator = getOrCreateStreamAccumulator(config);
        AgentLoop loop = new AgentLoop(config, context, runner, accumulator);

        AtomicReference<List<Message>> produced = new AtomicReference<>(List.of());
        try {
            loop.run(newMessages, abortSignal, evt -> {
                toolCallTracker.onEvent(evt);
                guardedEmitter.accept(evt);
                if (evt instanceof AgentEvent.AgentEnd ae) {
                    produced.set(ae.messages());
                }
            });
        } catch (Exception e) {
            // Synthesize failure event sequence only if AgentEnd was not yet emitted
            context.setErrorMessage(e.getMessage());
            if (!agentEndEmitted.get()) {
                AssistantMessage failMsg = AssistantMessage.builder()
                        .stopReason(StopReason.ERROR)
                        .errorMessage(e.getMessage())
                        .provider(baseConfig.model().provider())
                        .model(baseConfig.model().id())
                        .timestamp(Message.nowEpochSeconds())
                        .build();
                guardedEmitter.accept(new AgentEvent.MessageStart(failMsg));
                guardedEmitter.accept(new AgentEvent.MessageEnd(failMsg));
                guardedEmitter.accept(new AgentEvent.TurnEnd(failMsg, List.of()));
                guardedEmitter.accept(new AgentEvent.AgentEnd(List.of(failMsg)));
            }
        } finally {
            context.stopStreaming();
            toolCallTracker.clear();
            running.set(false);
        }

        return produced.get().stream()
                .filter(m -> m instanceof AssistantMessage)
                .toList();
    }

    /**
     * Run before-agent-start hooks via ExtensionRunner.
     */
    private void runBeforeAgentStartHooks(String prompt) {
        if (!extensionRunner.hasExtensions()) return;
        String sysPrompt = context.systemPrompt();
        BeforeAgentStartResult result = extensionRunner.onBeforeAgentStart(prompt, sysPrompt);
        if (result instanceof BeforeAgentStartResult.ModifySystemPrompt msp) {
            context.setSystemPrompt(msp.systemPrompt());
        }
    }

    private AgentLoopConfig buildConfigWithHooks(AgentLoopConfig.CompactCallback compactCallback) {
        if (cachedBaseConfig == null) {
            cachedBaseConfig = buildBaseConfigWithHooks();
        }

        return compactCallback != null
                ? cachedBaseConfig.withCompactCallback(compactCallback)
                : cachedBaseConfig;
    }

    /**
     * Build the base config with all static hooks (extensions + ad-hoc transforms).
     * Cached and reused across runs to avoid full config reconstruction.
     */
    private AgentLoopConfig buildBaseConfigWithHooks() {
        AgentLoopConfig.Builder b = baseConfig.toBuilder();

        // Wire extension runner hooks (typed)
        if (extensionRunner.hasExtensions()) {
            b.beforeToolCall(extensionRunner::beforeToolCall);
            b.afterToolCall(extensionRunner::afterToolCall);
        }

        b.getSteeringMessages(() -> steeringQueue.drain());
        b.getFollowUpMessages(() -> followUpQueue.drain());

        return b.build();
    }

    /**
     * Get or lazily create the shared ToolRunner.
     * Reuses the ExecutorService across calls for efficiency.
     */
    private ToolRunner getOrCreateToolRunner(AgentLoopConfig config) {
        if (config.toolRegistry() == null) return null;
        ToolRunner runner = sharedToolRunner;
        if (runner == null) {
            synchronized (this) {
                runner = sharedToolRunner;
                if (runner == null) {
                    runner = new ToolRunner(config.toolRegistry(), config.toolConfig(),
                            config.beforeToolCall(), config.afterToolCall());
                    sharedToolRunner = runner;
                }
            }
        }
        return runner;
    }

    /**
     * Get or lazily create the shared StreamAccumulator.
     * Config parameters are updated per run via updateConfig().
     */
    private StreamAccumulator getOrCreateStreamAccumulator(AgentLoopConfig config) {
        StreamAccumulator acc = sharedStreamAccumulator;
        if (acc == null) {
            synchronized (this) {
                acc = sharedStreamAccumulator;
                if (acc == null) {
                    acc = new StreamAccumulator(
                            config.streamFn(), config.model(),
                            config.thinkingLevel(), config.temperature(), config.maxTokens());
                    sharedStreamAccumulator = acc;
                }
            }
        }
        // Sync config for this run
        acc.updateConfig(config.model(), config.thinkingLevel(), config.temperature());
        return acc;
    }

    // No synchronized needed: close() guards via AtomicBoolean, ensuring single invocation.
    // sharedToolRunner is volatile, so the null-write is visible to any concurrent reader.
    private void closeToolRunner() {
        ToolRunner runner = sharedToolRunner;
        if (runner != null) {
            sharedToolRunner = null;
            runner.close();
        }
    }

    // ── Inner classes ───────────────────────────────────────

    /**
     * Tracks pending tool calls via agent events.
     * Separates tool-call state tracking from event dispatch logic (SRP).
     */
    private static final class ToolCallTracker {
        private final Set<String> pending = Collections.synchronizedSet(new LinkedHashSet<>());

        void onEvent(AgentEvent evt) {
            if (evt instanceof AgentEvent.ToolExecutionStart tes) {
                pending.add(tes.toolCallId());
            } else if (evt instanceof AgentEvent.ToolExecutionEnd tee) {
                pending.remove(tee.toolCallId());
            }
        }

        Set<String> snapshot() { return Set.copyOf(pending); }
        void clear() { pending.clear(); }
    }
}
