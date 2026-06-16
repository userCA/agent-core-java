package io.agentcore.core;

import io.agentcore.core.content.ImageContent;
import io.agentcore.core.content.TextContent;
import io.agentcore.core.context.*;
import io.agentcore.core.events.*;
import io.agentcore.core.humaninput.HumanInputGate;
import io.agentcore.core.loop.AgentLoop;
import io.agentcore.core.messages.*;
import io.agentcore.core.queue.PendingMessageQueue;
import io.agentcore.core.queue.QueueMode;
import io.agentcore.core.state.AgentState;
import io.agentcore.core.state.ThinkingLevel;
import io.agentcore.providers.auth.AuthSource;
import io.agentcore.providers.base.ModelProvider;
import io.agentcore.providers.base.StreamRequest;
import io.agentcore.providers.message_converter.DefaultMessageConverter;
import io.agentcore.providers.types.Model;
import io.agentcore.providers.types.ProviderAuth;
import io.agentcore.providers.types.StreamEvent;
import io.agentcore.tools.base.ToolRegistry;
import io.agentcore.tools.mutation.FileMutationQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Stateful wrapper around the pure agent loop.
 * Manages state, listeners, hooks, queues, and HITL integration.
 */
public class Agent {
    private static final Logger log = LoggerFactory.getLogger(Agent.class);

    private final AgentState state;
    private final ModelProvider provider;
    private final AuthSource authSource;
    private final ConvertToLlm convertToLlm;
    private ToolRegistry toolRegistry;
    private final List<BeforeToolCallHook> beforeHooks = new CopyOnWriteArrayList<>();
    private final List<AfterToolCallHook> afterHooks = new CopyOnWriteArrayList<>();
    private final List<TransformContext> transformHooks = new CopyOnWriteArrayList<>();
    private final List<BeforeAgentStartHook> beforeAgentStartHooks = new CopyOnWriteArrayList<>();
    private final List<Consumer<AgentEvent>> listeners = new CopyOnWriteArrayList<>();
    private final PendingMessageQueue steeringQueue;
    private final PendingMessageQueue followUpQueue;
    private final HumanInputGate humanInputGate;
    private volatile Thread activeThread;
    private volatile CompletableFuture<Void> activeRun;
    private volatile AtomicBoolean abortEvent;
    private final Set<String> pendingToolCalls = ConcurrentHashMap.newKeySet();

    // Config fields
    private ToolExecutionMode toolExecution = ToolExecutionMode.PARALLEL;
    private Double toolTimeout = 120.0;
    private Integer maxTurns;
    private int maxRetries = 3;
    private double retryBaseDelay = 1.0;
    private double retryMaxDelay = 60.0;
    private int toolResultMaxChars = 4000;
    private CompactCallback compactCallback;
    private FileMutationQueue mutationQueue;

    private Agent(Builder builder) {
        this.provider = builder.provider;
        this.authSource = builder.authSource;
        this.state = builder.initialState != null ? builder.initialState : new AgentState();
        this.toolRegistry = builder.toolRegistry;
        this.steeringQueue = new PendingMessageQueue(builder.steeringMode);
        this.followUpQueue = new PendingMessageQueue(builder.followupMode);
        this.humanInputGate = new HumanInputGate();
        this.abortEvent = new AtomicBoolean(false);

        // Set up message converter
        if (builder.convertToLlm != null) {
            this.convertToLlm = builder.convertToLlm;
        } else {
            this.convertToLlm = new DefaultMessageConverter(toolResultMaxChars)::convert;
        }

        // Apply builder config
        this.toolExecution = builder.toolExecution;
        this.toolTimeout = builder.toolTimeout;
        this.maxTurns = builder.maxTurns;
        this.maxRetries = builder.maxRetries;
        this.retryBaseDelay = builder.retryBaseDelay;
        this.retryMaxDelay = builder.retryMaxDelay;
        this.toolResultMaxChars = builder.toolResultMaxChars;
        this.compactCallback = builder.compactCallback;

        if (builder.beforeToolCall != null) beforeHooks.add(builder.beforeToolCall);
        if (builder.afterToolCall != null) afterHooks.add(builder.afterToolCall);
    }

    public static Builder builder() { return new Builder(); }

    // === Public API ===

    public AgentState state() { return state; }

    public Runnable subscribe(Consumer<AgentEvent> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public CompletableFuture<Void> prompt(String text) {
        return prompt(text, null);
    }

    public CompletableFuture<Void> prompt(String text, List<ImageContent> images) {
        List<io.agentcore.core.content.Content> content = new ArrayList<>();
        content.add(new TextContent(text));
        if (images != null) content.addAll(images);
        var userMsg = new UserMessage(content);
        return run(List.of(userMsg), false);
    }

    public CompletableFuture<Void> continueFrom() {
        return run(List.of(), true);
    }

    public void steer(AgentMessage message) {
        steeringQueue.enqueue(message);
    }

    public void followUp(AgentMessage message) {
        followUpQueue.enqueue(message);
    }

    public boolean provideHumanInput(String toolCallId, Map<String, Object> values) {
        return humanInputGate.provideInput(toolCallId, values);
    }

    public void abort() {
        if (abortEvent != null) abortEvent.set(true);
        humanInputGate.cancelAll();
    }

    public CompletableFuture<Void> waitForIdle() {
        var run = activeRun;
        return run != null ? run : CompletableFuture.completedFuture(null);
    }

    public synchronized void reset() {
        log.info("Agent state reset");
        state.resetState();
        steeringQueue.clear();
        followUpQueue.clear();
    }

    public void clearAllQueues() {
        steeringQueue.clear();
        followUpQueue.clear();
    }

    public Set<String> pendingToolCalls() { return Collections.unmodifiableSet(pendingToolCalls); }

    // Hook registration
    public void addBeforeToolCallHook(BeforeToolCallHook hook) { beforeHooks.add(hook); }
    public void addAfterToolCallHook(AfterToolCallHook hook) { afterHooks.add(hook); }
    public void addBeforeAgentStartHook(BeforeAgentStartHook hook) { beforeAgentStartHooks.add(hook); }
    public void addTransformContextHook(TransformContext hook) { transformHooks.add(hook); }

    // === Internal ===

    private CompletableFuture<Void> run(List<AgentMessage> newMessages, boolean continuation) {
        synchronized (this) {
            if (activeRun != null && !activeRun.isDone()) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("Agent is already running. Call abort() first."));
            }

            abortEvent.set(false);
            state.streaming(true);
            state.errorMessage(null);

            CompletableFuture<Void> future = new CompletableFuture<>();
            activeRun = future;

            activeThread = Thread.ofVirtual().name("agent-loop").start(() -> {
                try {
                    // Build context
                    AgentContext context = new AgentContext(
                            state.systemPrompt(),
                            new ArrayList<>(state.messages()),
                            new ArrayList<>(state.tools())
                    );

                    // Run before-agent-start hooks
                    List<AgentMessage> effectiveMessages = newMessages;
                    for (var hook : beforeAgentStartHooks) {
                        try {
                            var result = hook.apply(state.systemPrompt()).get();
                            if (result != null) {
                                if (result.containsKey("system_prompt")) {
                                    context.systemPrompt((String) result.get("system_prompt"));
                                }
                                if (result.containsKey("message") && result.get("message") instanceof AgentMessage msg) {
                                    effectiveMessages = new ArrayList<>(effectiveMessages);
                                    effectiveMessages.add(msg);
                                }
                            }
                        } catch (Exception e) {
                            log.warn("before_agent_start hook failed: {}", e.getMessage());
                        }
                    }

                    // Build config
                    AgentLoopConfig config = buildConfig(context);

                    // Run the loop
                    AgentLoop.run(effectiveMessages, context, config, abortEvent, evt -> handleEvent(evt, context));

                    // Sync messages back to state
                    state.replaceMessages(context.messages());
                    future.complete(null);

                } catch (Exception e) {
                    log.error("Agent run failed: {}", e.getMessage(), e);
                    state.errorMessage(e.getMessage());
                    // Create error assistant message
                    var errorMsg = new AssistantMessage(
                            List.of(new TextContent("Error: " + e.getMessage())),
                            new Usage(), StopReason.ERROR, e.getMessage(),
                            false, false, null, null,
                            System.currentTimeMillis() / 1000.0
                    );
                    state.addMessage(errorMsg);
                    listeners.forEach(l -> {
                        try { l.accept(new MessageEnd(errorMsg)); } catch (Exception e2) {
                            log.warn("Event listener failed during error dispatch: {}", e2.getMessage());
                        }
                    });
                    future.completeExceptionally(e);
                } finally {
                    state.stopStreaming();
                    activeThread = null;
                }
            });

            return future;
        }
    }

    private AgentLoopConfig buildConfig(AgentContext context) {
        Function<StreamRequest, Flow.Publisher<StreamEvent>> streamFn =
                request -> provider.stream(request);

        var builder = new AgentLoopConfig.Builder(
                state.model(), streamFn, convertToLlm,
                providerName -> authSource.resolve(providerName)
        );

        builder.thinkingLevel(state.thinkingLevel())
              .toolExecution(toolExecution)
              .toolTimeout(toolTimeout)
              .maxTurns(maxTurns)
              .maxRetries(maxRetries)
              .retryBaseDelay(retryBaseDelay)
              .retryMaxDelay(retryMaxDelay)
              .toolResultMaxChars(toolResultMaxChars)
              .toolRegistry(toolRegistry)
              .compactCallback(compactCallback)
              .mutationQueue(mutationQueue)
              .humanInputGate(humanInputGate)
              .getSteeringMessages(() -> CompletableFuture.completedFuture(steeringQueue.drain()))
              .getFollowUpMessages(() -> CompletableFuture.completedFuture(followUpQueue.drain()));

        // Chain before hooks
        if (!beforeHooks.isEmpty()) {
            builder.beforeToolCall(callCtx -> {
                BeforeToolCallResult lastResult = BeforeToolCallResult.proceed();
                for (var hook : beforeHooks) {
                    try {
                        BeforeToolCallResult result = hook.apply(callCtx).get();
                        if (result != null) lastResult = result;
                    } catch (Exception e) {
                        log.warn("before_tool_call hook failed: {}", e.getMessage());
                    }
                    if (lastResult.block()) break;
                }
                return CompletableFuture.completedFuture(lastResult);
            });
        }

        // Chain after hooks
        if (!afterHooks.isEmpty()) {
            builder.afterToolCall(callCtx -> {
                AfterToolCallResult lastResult = AfterToolCallResult.keepOriginal();
                for (var hook : afterHooks) {
                    try {
                        AfterToolCallResult result = hook.apply(callCtx).get();
                        if (result != null) lastResult = result;
                    } catch (Exception e) {
                        log.warn("after_tool_call hook failed: {}", e.getMessage());
                    }
                }
                return CompletableFuture.completedFuture(lastResult);
            });
        }

        // Chain transform hooks
        if (!transformHooks.isEmpty()) {
            builder.transformContext((msgs, signal) -> {
                List<Map<String, Object>> current = msgs;
                for (var hook : transformHooks) {
                    try {
                        current = hook.apply(current, signal).get();
                    } catch (Exception e) {
                        log.warn("transform_context hook failed: {}", e.getMessage());
                    }
                }
                return CompletableFuture.completedFuture(current);
            });
        }

        return builder.build();
    }

    private void handleEvent(AgentEvent evt, AgentContext context) {
        // Update state based on event type
        if (evt instanceof MessageStart ms && ms.message() instanceof AssistantMessage) {
            state.streamingMessage((AssistantMessage) ms.message());
        } else if (evt instanceof MessageUpdate mu && mu.message() instanceof AssistantMessage) {
            state.streamingMessage((AssistantMessage) mu.message());
        } else if (evt instanceof MessageEnd me) {
            if (me.message() instanceof AssistantMessage) {
                state.streamingMessage(null);
            }
        } else if (evt instanceof ToolExecutionStart tes) {
            pendingToolCalls.add(tes.toolCallId());
        } else if (evt instanceof ToolExecutionEnd tee) {
            pendingToolCalls.remove(tee.toolCallId());
        } else if (evt instanceof AgentEnd) {
            state.streamingMessage(null);
        }

        // Fan out to listeners
        for (var listener : listeners) {
            try {
                listener.accept(evt);
            } catch (Exception e) {
                log.warn("Event listener failed: {}", e.getMessage());
            }
        }
    }

    /**
     * Hook interface for before-agent-start callbacks.
     */
    @FunctionalInterface
    public interface BeforeAgentStartHook {
        CompletableFuture<Map<String, Object>> apply(String systemPrompt);
    }

    // === Builder ===

    public static class Builder {
        private ModelProvider provider;
        private AuthSource authSource;
        private AgentState initialState;
        private ConvertToLlm convertToLlm;
        private ToolRegistry toolRegistry;
        private BeforeToolCallHook beforeToolCall;
        private AfterToolCallHook afterToolCall;
        private ToolExecutionMode toolExecution = ToolExecutionMode.PARALLEL;
        private Double toolTimeout = 120.0;
        private Integer maxTurns;
        private int maxRetries = 3;
        private double retryBaseDelay = 1.0;
        private double retryMaxDelay = 60.0;
        private int toolResultMaxChars = 4000;
        private CompactCallback compactCallback;
        private QueueMode steeringMode = QueueMode.ONE_AT_A_TIME;
        private QueueMode followupMode = QueueMode.ONE_AT_A_TIME;

        public Builder provider(ModelProvider v) { this.provider = v; return this; }
        public Builder authSource(AuthSource v) { this.authSource = v; return this; }
        public Builder initialState(AgentState v) { this.initialState = v; return this; }
        public Builder convertToLlm(ConvertToLlm v) { this.convertToLlm = v; return this; }
        public Builder toolRegistry(ToolRegistry v) { this.toolRegistry = v; return this; }
        public Builder beforeToolCall(BeforeToolCallHook v) { this.beforeToolCall = v; return this; }
        public Builder afterToolCall(AfterToolCallHook v) { this.afterToolCall = v; return this; }
        public Builder toolExecution(ToolExecutionMode v) { this.toolExecution = v; return this; }
        public Builder toolTimeout(Double v) { this.toolTimeout = v; return this; }
        public Builder maxTurns(Integer v) { this.maxTurns = v; return this; }
        public Builder maxRetries(int v) { this.maxRetries = v; return this; }
        public Builder retryBaseDelay(double v) { this.retryBaseDelay = v; return this; }
        public Builder retryMaxDelay(double v) { this.retryMaxDelay = v; return this; }
        public Builder toolResultMaxChars(int v) { this.toolResultMaxChars = v; return this; }
        public Builder compactCallback(CompactCallback v) { this.compactCallback = v; return this; }
        public Builder steeringMode(QueueMode v) { this.steeringMode = v; return this; }
        public Builder followupMode(QueueMode v) { this.followupMode = v; return this; }

        public Agent build() {
            Objects.requireNonNull(provider, "provider is required");
            Objects.requireNonNull(authSource, "authSource is required");
            return new Agent(this);
        }
    }
}
