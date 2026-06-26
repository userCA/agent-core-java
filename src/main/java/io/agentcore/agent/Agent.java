package io.agentcore.agent;

import io.agentcore.model.Content.TextContent;
import io.agentcore.model.Message.*;
import io.agentcore.extensions.Extension;
import io.agentcore.extensions.ExtensionLoader;
import io.agentcore.extensions.ExtensionRunner;
import io.agentcore.extensions.HookTypes.BeforeAgentStartResult;
import io.agentcore.extensions.SelfHealingExtension;
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

    // Extension-based hook system (unified — all lifecycle hooks + event observation)
    private final ExtensionRunner extensionRunner;

    private final PendingMessageQueue steeringQueue;
    private final PendingMessageQueue followUpQueue;

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

    /**
     * Register an event subscriber by wrapping it as an anonymous Extension.
     * The subscriber participates in the unified onEvent hook pipeline with
     * lowest priority (Integer.MAX_VALUE = runs last).
     *
     * @return a cancellation runnable that removes the subscriber
     */
    public Runnable subscribe(Consumer<AgentEvent> listener) {
        Extension sub = new Extension() {
            @Override public String name() {
                return "subscriber-" + System.identityHashCode(this);
            }
            @Override public int order() { return Integer.MAX_VALUE; }
            @Override public void onEvent(AgentEvent event) {
                listener.accept(event);
            }
        };
        extensionRunner.addExtension(sub);
        return () -> extensionRunner.removeExtension(sub);
    }

    /**
     * Register additional extensions at runtime (e.g. from AgentSession).
     * Rebuilds config on next run to pick up new hooks.
     */
    public void addExtensions(List<Extension> additional) {
        extensionRunner.addExtensions(additional);
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
     * Clears messages, queues, signals, pending tool calls, and extension state.
     */
    public void reset() {
        context.resetState();
        abortSignal.set(false);
        running.set(false);
        toolCallTracker.clear();
        steeringQueue.clear();
        followUpQueue.clear();
        // Clear stateful extension data
        for (var ext : extensionRunner.extensions()) {
            if (ext instanceof SelfHealingExtension she) she.clearState();
        }
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
        // Load SPI extensions automatically
        List<Extension> extensions = ExtensionLoader.load();
        return new Agent(config, context, extensions,
                PendingMessageQueue.DrainMode.ONE_AT_A_TIME,
                PendingMessageQueue.DrainMode.ONE_AT_A_TIME);
    }

    // ── Internal ───────────────────────────────────────────

    /**
     * Common loop execution logic shared by prompt() and continueLoop().
     * Orchestrates the harness layer: state reset → event pipeline → loop preparation → execution.
     *
     * The per-call onEvent consumer is wrapped as an ephemeral Extension and registered
     * to the ExtensionRunner for the duration of this run. All events are dispatched
     * through extensionRunner.onEvent() — the single unified dispatch point.
     */
    private List<Message> runLoop(List<Message> newMessages,
                                  Consumer<AgentEvent> onEvent,
                                  AgentLoopConfig.ContextCompactor compactCallback) {
        // Concurrency guard (mirrors pi-mono activeRun)
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException(
                    "Agent is already processing a prompt. Use steer() or followUp() to queue messages.");
        }
        // Per-call consumer as ephemeral extension (lowest order = runs first)
        Extension callExt = wrapAsExtension(onEvent);
        extensionRunner.addExtension(callExt);
        try {
            resetRunState();
            String originalSysPrompt = context.systemPrompt();
            try {
                AgentLoop loop = prepareLoop(compactCallback);
                return executeLoop(loop, newMessages);
            } finally {
                context.setSystemPrompt(originalSysPrompt);
            }
        } finally {
            extensionRunner.removeExtension(callExt);
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
     * Wrap a per-call Consumer as an ephemeral Extension for the unified event pipeline.
     * Runs with lowest order (Integer.MIN_VALUE) to execute before other extensions.
     */
    private Extension wrapAsExtension(Consumer<AgentEvent> consumer) {
        return new Extension() {
            @Override public String name() { return "call-scope"; }
            @Override public int order() { return Integer.MIN_VALUE; }
            @Override public void onEvent(AgentEvent event) {
                if (consumer != null) consumer.accept(event);
            }
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
     * All events are dispatched through extensionRunner.onEvent() — the single unified dispatch point.
     * On success, returns assistant messages from AgentEnd.
     * On failure, synthesizes an error event sequence (MessageStart→MessageEnd→TurnEnd→AgentEnd).
     */
    private List<Message> executeLoop(AgentLoop loop, List<Message> newMessages) {
        AtomicReference<List<Message>> producedMessages = new AtomicReference<>(List.of());
        AtomicBoolean agentEndEmitted = new AtomicBoolean(false);
        try {
            loop.run(newMessages, abortSignal, evt -> {
                toolCallTracker.onEvent(evt);
                extensionRunner.onEvent(evt);  // unified event dispatch
                if (evt instanceof AgentEvent.AgentEnd ae) {
                    producedMessages.set(ae.messages());
                    agentEndEmitted.set(true);
                }
            });
        } catch (Exception e) {
            handleRunFailure(e, agentEndEmitted);
        }
        return producedMessages.get().stream()
                .filter(m -> m instanceof AssistantMessage)
                .toList();
    }

    /**
     * Synthesize a failure event sequence when the loop throws an exception.
     * Only emits if AgentEnd has not yet been sent (guarded by agentEndEmitted).
     * Events are dispatched through extensionRunner.onEvent() for unified observation.
     */
    private void handleRunFailure(Exception e, AtomicBoolean agentEndEmitted) {
        context.setErrorMessage(e.getMessage());
        if (!agentEndEmitted.get()) {
            AssistantMessage failMsg = AssistantMessage.builder()
                    .stopReason(StopReason.ERROR)
                    .errorMessage(e.getMessage())
                    .provider(baseConfig.model().provider())
                    .model(baseConfig.model().id())
                    .timestamp(Message.nowEpochSeconds())
                    .build();
            extensionRunner.onEvent(new AgentEvent.MessageStart(failMsg));
            extensionRunner.onEvent(new AgentEvent.MessageEnd(failMsg));
            extensionRunner.onEvent(new AgentEvent.TurnEnd(failMsg, List.of()));
            extensionRunner.onEvent(new AgentEvent.AgentEnd(List.of(failMsg)));
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
        AgentLoopConfig base = buildBaseConfigWithHooks();
        return compactCallback != null
                ? base.withCompactCallback(compactCallback)
                : base;
    }

    /**
     * Build the base config with all hooks (extensions + queue suppliers).
     * Rebuilt on each run to pick up dynamically added extensions.
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
