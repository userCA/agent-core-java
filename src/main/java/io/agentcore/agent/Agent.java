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
import java.util.function.Consumer;
import io.agentcore.model.AgentEvent;
import io.agentcore.model.HumanInputGate;
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

    // Message assembler — wraps provider converter with Agent-layer enrichment
    private final AgentLoopConfig.MessageAssembler messageAssembler;

    private final ToolCallTracker toolCallTracker = new ToolCallTracker();

    private final AgentResources resources = new AgentResources();

    // Concurrency guard: prevents simultaneous prompt/continue calls
    private final AtomicBoolean running = new AtomicBoolean(false);

    public Agent(AgentLoopConfig config, AgentContext context) {
        this(config, context, List.of(),
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
        this.messageAssembler = config.messageAssembler();
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
                                AgentLoopConfig.ContextCompactor compactCallback) {
        UserMessage userMsg = new UserMessage(
                List.of(new TextContent(text)),
                Message.nowEpochSeconds());

        // Run before-agent-start hooks via extensions
        runBeforeAgentStartHooks(text);

        return runLoop(List.of(userMsg), onEvent, compactCallback);
    }

    public List<Message> continueLoop(Consumer<AgentEvent> onEvent,
                                      AgentLoopConfig.ContextCompactor compactCallback) {
        // Validate preconditions (mirrors pi-mono agentLoopContinue)
        List<Message> snapshot = context.messagesSnapshot();
        if (snapshot.isEmpty()) {
            throw new IllegalStateException("Cannot continue: no messages in context");
        }
        Message last = snapshot.getLast();
        if (last instanceof AssistantMessage) {
            throw new IllegalStateException("Cannot continue from message role: assistant");
        }
        return runLoop(List.of(), onEvent, compactCallback);
    }

    public void abort() {
        abortSignal.set(true);
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

    /**
     * Enqueue a steering message to be injected before the next LLM call.
     * Used for mid-conversation guidance, course correction, or follow-up questions.
     */
    public void steer(Message message) {
        steeringQueue.enqueue(message);
    }

    /**
     * Enqueue a follow-up message to be processed after the current turn completes.
     * Used for queuing the next user question while the agent is still running.
     */
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
    public Set<String> pendingToolCallIds() { return toolCallTracker.snapshot(); }
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
        resources.close();
    }

    // ── Factory ────────────────────────────────────────────

    public static Agent create(
            ModelProvider provider,
            ModelInfo model,
            AuthSource authSource,
            ToolRegistry toolRegistry,
            String systemPrompt) {

        // Provider-level message converter → wrapped as MessageAssembler at Agent layer
        AgentLoopConfig.MessageAssembler assembler = provider.createMessageConverter()::apply;

        AgentLoopConfig config = AgentLoopConfig.builder()
                .model(model)
                .streamFn(provider::stream)
                .messageAssembler(assembler)
                .authResolver(authSource::resolve)
                .toolRegistry(toolRegistry)
                .build();

        AgentContext context = new AgentContext(systemPrompt, new ArrayList<>());
        return new Agent(config, context);
    }

    // ── Internal ───────────────────────────────────────────

    /**
     * Common loop execution logic shared by prompt() and continueLoop().
     * Orchestrates the harness layer: state reset → event pipeline → loop preparation → execution.
     */
    private List<Message> runLoop(List<Message> newMessages,
                                  Consumer<AgentEvent> onEvent,
                                  AgentLoopConfig.ContextCompactor compactCallback) {
        // Concurrency guard (mirrors pi-mono activeRun)
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException(
                    "Agent is already processing a prompt. Use steer() or followUp() to queue messages.");
        }
        try {
            resetRunState();
            AtomicBoolean agentEndEmitted = new AtomicBoolean(false);
            Consumer<AgentEvent> guardedEmitter = buildEventPipeline(onEvent, agentEndEmitted);
            AgentLoop loop = prepareLoop(compactCallback);
            return executeLoop(loop, newMessages, guardedEmitter, agentEndEmitted);
        } finally {
            finalizeRun();
        }
    }

    /**
     * Reset per-run state: abort signal, error message, and streaming flag.
     */
    private void resetRunState() {
        abortSignal.set(false);
        context.setErrorMessage(null);
        context.tryStartStreaming();
    }

    /**
     * Build the event dispatch pipeline with AgentEnd deduplication guard.
     * The guardedEmitter wraps the raw emitter and suppresses duplicate AgentEnd events.
     */
    private Consumer<AgentEvent> buildEventPipeline(Consumer<AgentEvent> onEvent,
                                                     AtomicBoolean agentEndEmitted) {
        Consumer<AgentEvent> emitter = eventDispatcher.createEmitter(onEvent);
        return evt -> {
            if (evt instanceof AgentEvent.AgentEnd) {
                if (!agentEndEmitted.compareAndSet(false, true)) {
                    log.warn("Duplicate AgentEnd event suppressed");
                    return;
                }
            }
            emitter.accept(evt);
        };
    }

    /**
     * Prepare the AgentLoop: build config with hooks, acquire resources, sync tool hooks.
     */
    private AgentLoop prepareLoop(AgentLoopConfig.ContextCompactor compactCallback) {
        AgentLoopConfig config = buildConfigWithHooks(compactCallback);
        ToolRunner runner = resources.getOrCreateToolRunner(config);
        // Always sync hooks — ToolRunner is shared, but config/hooks may change per run
        if (runner != null) {
            runner.updateHooks(config.beforeToolCall(), config.afterToolCall());
        }
        StreamAccumulator accumulator = resources.getOrCreateStreamAccumulator(config);
        return new AgentLoop(config, context, runner, accumulator);
    }

    /**
     * Execute the agent loop with failure handling.
     * On success, returns assistant messages from AgentEnd.
     * On failure, synthesizes an error event sequence (MessageStart→MessageEnd→TurnEnd→AgentEnd).
     */
    private List<Message> executeLoop(AgentLoop loop, List<Message> newMessages,
                                       Consumer<AgentEvent> guardedEmitter,
                                       AtomicBoolean agentEndEmitted) {
        AtomicReference<List<Message>> producedMessages = new AtomicReference<>(List.of());
        try {
            loop.run(newMessages, abortSignal, evt -> {
                toolCallTracker.onEvent(evt);
                guardedEmitter.accept(evt);
                if (evt instanceof AgentEvent.AgentEnd ae) {
                    producedMessages.set(ae.messages());
                }
            });
        } catch (Exception e) {
            handleRunFailure(e, guardedEmitter, agentEndEmitted);
        }
        return producedMessages.get().stream()
                .filter(m -> m instanceof AssistantMessage)
                .toList();
    }

    /**
     * Synthesize a failure event sequence when the loop throws an exception.
     * Only emits if AgentEnd has not yet been sent (guarded by agentEndEmitted).
     */
    private void handleRunFailure(Exception e, Consumer<AgentEvent> guardedEmitter,
                                   AtomicBoolean agentEndEmitted) {
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
    }

    /**
     * Finalize a run: stop streaming, clear tool tracker, release concurrency guard.
     */
    private void finalizeRun() {
        context.stopStreaming();
        toolCallTracker.clear();
        running.set(false);
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

    private AgentLoopConfig buildConfigWithHooks(AgentLoopConfig.ContextCompactor compactCallback) {
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

        // Wrap assembler with Agent-layer enrichment (future: memory injection, context enhancement)
        b.messageAssembler(messages -> messageAssembler.assemble(messages));

        // Wire extension runner hooks (typed)
        if (extensionRunner.hasExtensions()) {
            b.beforeToolCall(extensionRunner::onBeforeToolCall);
            b.afterToolCall(extensionRunner::onAfterToolCall);
        }

        b.steeringMessageSupplier(() -> steeringQueue.drain());
        b.followUpMessageSupplier(() -> followUpQueue.drain());

        return b.build();
    }

}
