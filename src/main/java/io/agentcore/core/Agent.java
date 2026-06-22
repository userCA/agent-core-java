package io.agentcore.core;

import io.agentcore.core.Content.TextContent;
import io.agentcore.core.Message.*;
import io.agentcore.providers.*;
import io.agentcore.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * High-level Agent facade — stateful wrapper around AgentLoop.
 *
 * <p>Mirrors Python {@code agent_core/core/agent.py} Agent class.
 */
public class Agent {

    private static final Logger log = LoggerFactory.getLogger(Agent.class);

    private final AgentLoopConfig baseConfig;
    private final AgentContext context;
    private final AtomicBoolean abortSignal = new AtomicBoolean(false);
    private final List<Consumer<AgentEvent>> subscribers = new CopyOnWriteArrayList<>();

    private final List<Function<Map<String, Object>, Map<String, Object>>> beforeHooks = new CopyOnWriteArrayList<>();
    private final List<Function<Map<String, Object>, Map<String, Object>>> afterHooks = new CopyOnWriteArrayList<>();
    private final List<AgentLoopConfig.TransformContext> transformHooks = new CopyOnWriteArrayList<>();
    private final List<java.util.function.BiFunction<String, String, Map<String, Object>>> beforeAgentStartHooks = new CopyOnWriteArrayList<>();

    private final PendingMessageQueue steeringQueue;
    private final PendingMessageQueue followUpQueue;

    private final Set<String> pendingToolCalls = Collections.synchronizedSet(new LinkedHashSet<>());

    public Agent(AgentLoopConfig config) {
        this(config, new AgentContext());
    }

    public Agent(AgentLoopConfig config, AgentContext context) {
        this.baseConfig = config;
        this.context = context;
        this.steeringQueue = new PendingMessageQueue();
        this.followUpQueue = new PendingMessageQueue();
    }

    // ── Subscribe ──────────────────────────────────────────

    public Runnable subscribe(Consumer<AgentEvent> listener) {
        subscribers.add(listener);
        return () -> subscribers.remove(listener);
    }

    // ── Prompt / Continue / Abort ──────────────────────────

    public List<Message> prompt(String text, Consumer<AgentEvent> onEvent) {
        return prompt(text, onEvent, null);
    }

    public List<Message> prompt(String text, Consumer<AgentEvent> onEvent,
                                AgentLoopConfig.CompactCallback compactCallback) {
        abortSignal.set(false);
        context.setErrorMessage(null);
        context.tryStartStreaming();

        UserMessage userMsg = new UserMessage(
                List.of(new TextContent(text)),
                System.currentTimeMillis() / 1000.0);

        Consumer<AgentEvent> emitter = createEmitter(onEvent);
        AgentLoopConfig config = buildConfigWithHooks(compactCallback);

        // Run before-agent-start hooks
        if (!beforeAgentStartHooks.isEmpty()) {
            String sysPrompt = context.systemPrompt();
            for (var hook : beforeAgentStartHooks) {
                try {
                    Map<String, Object> result = hook.apply(text, sysPrompt);
                    if (result != null && result.get("system_prompt") instanceof String sp) {
                        context.setSystemPrompt(sp);
                        sysPrompt = sp;
                    }
                } catch (Exception e) {
                    log.warn("beforeAgentStartHook failed", e);
                }
            }
        }

        AgentLoop loop = new AgentLoop(config, context);

        List<AgentEvent> allEvents = new ArrayList<>();
        try {
            loop.run(List.of(userMsg), abortSignal, evt -> {
                allEvents.add(evt);
                trackState(evt);
                emitter.accept(evt);
            });
        } catch (Exception e) {
            context.setErrorMessage(e.getMessage());
        } finally {
            context.stopStreaming();
            pendingToolCalls.clear();
        }

        return extractAssistantMessages(allEvents);
    }

    public List<Message> continue_(Consumer<AgentEvent> onEvent) {
        return continue_(onEvent, null);
    }

    public List<Message> continue_(Consumer<AgentEvent> onEvent,
                                   AgentLoopConfig.CompactCallback compactCallback) {
        abortSignal.set(false);
        context.setErrorMessage(null);
        context.tryStartStreaming();

        Consumer<AgentEvent> emitter = createEmitter(onEvent);
        AgentLoopConfig config = buildConfigWithHooks(compactCallback);
        AgentLoop loop = new AgentLoop(config, context);

        List<AgentEvent> allEvents = new ArrayList<>();
        try {
            loop.run(List.of(), abortSignal, evt -> {
                allEvents.add(evt);
                trackState(evt);
                emitter.accept(evt);
            });
        } catch (Exception e) {
            context.setErrorMessage(e.getMessage());
        } finally {
            context.stopStreaming();
            pendingToolCalls.clear();
        }

        return extractAssistantMessages(allEvents);
    }

    public void abort() {
        abortSignal.set(true);
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

    // ── Hook chains ────────────────────────────────────────

    public void addBeforeToolCallHook(Function<Map<String, Object>, Map<String, Object>> hook) {
        beforeHooks.add(hook);
    }

    public void removeBeforeToolCallHook(Function<Map<String, Object>, Map<String, Object>> hook) {
        beforeHooks.remove(hook);
    }

    public void addAfterToolCallHook(Function<Map<String, Object>, Map<String, Object>> hook) {
        afterHooks.add(hook);
    }

    public void removeAfterToolCallHook(Function<Map<String, Object>, Map<String, Object>> hook) {
        afterHooks.remove(hook);
    }

    public void addTransformContextHook(AgentLoopConfig.TransformContext hook) {
        transformHooks.add(hook);
    }

    /**
     * Add a hook called before the agent loop starts.
     * Hook receives (prompt, systemPrompt) and can return:
     * - "system_prompt" → modified system prompt
     * - "message" → a Message to inject
     */
    public void addBeforeAgentStartHook(java.util.function.BiFunction<String, String, Map<String, Object>> hook) {
        beforeAgentStartHooks.add(hook);
    }

    // ── State accessors ────────────────────────────────────

    public boolean isStreaming() { return context.isStreaming(); }
    public String errorMessage() { return context.errorMessage(); }
    public Set<String> pendingToolCalls() { return Set.copyOf(pendingToolCalls); }
    public List<Message> messages() { return List.copyOf(context.messages()); }
    public AgentContext context() { return context; }

    public void reset() {
        context.resetState();
        abortSignal.set(false);
        pendingToolCalls.clear();
        steeringQueue.clear();
        followUpQueue.clear();
    }

    // ── Factory ────────────────────────────────────────────

    public static Agent create(
            ModelProvider provider,
            ModelInfo model,
            AuthSource authSource,
            ToolRegistry toolRegistry,
            String systemPrompt) {

        MessageConverter converter = new MessageConverter();
        AgentLoopConfig config = AgentLoopConfig.builder()
                .model(model)
                .streamFn(provider::stream)
                .convertToLlm(converter::convert)
                .authResolver(authSource::resolve)
                .toolRegistry(toolRegistry)
                .build();

        AgentContext context = new AgentContext(systemPrompt, new ArrayList<>(), new ArrayList<>());
        return new Agent(config, context);
    }

    // ── Internal ───────────────────────────────────────────

    private AgentLoopConfig buildConfigWithHooks(AgentLoopConfig.CompactCallback compactCallback) {
        AgentLoopConfig.Builder b = baseConfig.toBuilder();

        if (!beforeHooks.isEmpty()) {
            b.beforeToolCall(this::chainBeforeHooks);
        }
        if (!afterHooks.isEmpty()) {
            b.afterToolCall(this::chainAfterHooks);
        }
        if (!transformHooks.isEmpty()) {
            b.transformContext(this::chainTransformHooks);
        }

        b.getSteeringMessages(() -> steeringQueue.drain());
        b.getFollowUpMessages(() -> followUpQueue.drain());

        if (compactCallback != null) {
            b.compactCallback(compactCallback);
        }

        return b.build();
    }

    private Map<String, Object> chainBeforeHooks(Map<String, Object> callCtx) {
        Map<String, Object> merged = null;
        for (var hook : beforeHooks) {
            try {
                Map<String, Object> result = hook.apply(callCtx);
                if (result != null) {
                    if (Boolean.TRUE.equals(result.get("block"))) return result;
                    if (merged == null) merged = new HashMap<>();
                    if (result.containsKey("inject_metadata")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> meta = (Map<String, Object>) result.get("inject_metadata");
                        if (meta != null) merged.putAll(meta);
                    }
                    if (result.containsKey("mutated_args")) {
                        merged.put("mutated_args", result.get("mutated_args"));
                    }
                }
            } catch (Exception e) {
                log.warn("beforeToolCall hook failed", e);
            }
        }
        return merged;
    }

    private Map<String, Object> chainAfterHooks(Map<String, Object> callCtx) {
        Map<String, Object> lastResult = null;
        for (var hook : afterHooks) {
            try {
                Map<String, Object> result = hook.apply(callCtx);
                if (result != null && result.containsKey("result")) {
                    lastResult = result;
                }
            } catch (Exception e) {
                log.warn("afterToolCall hook failed", e);
            }
        }
        return lastResult;
    }

    private List<Map<String, Object>> chainTransformHooks(List<Map<String, Object>> msgs, AtomicBoolean signal) {
        List<Map<String, Object>> result = msgs;
        for (var hook : transformHooks) {
            try { result = hook.transform(result, signal); } catch (Exception e) { log.warn("transformContext hook failed", e); }
        }
        return result;
    }

    private void trackState(AgentEvent evt) {
        if (evt instanceof AgentEvent.ToolExecutionStart tes) {
            pendingToolCalls.add(tes.toolCallId());
        } else if (evt instanceof AgentEvent.ToolExecutionEnd tee) {
            pendingToolCalls.remove(tee.toolCallId());
        } else if (evt instanceof AgentEvent.AgentEnd) {
            context.stopStreaming();
        }
    }

    private Consumer<AgentEvent> createEmitter(Consumer<AgentEvent> override) {
        if (override != null) return override;
        return evt -> {
            for (Consumer<AgentEvent> sub : subscribers) {
                sub.accept(evt);
            }
        };
    }

    private List<Message> extractAssistantMessages(List<AgentEvent> events) {
        List<Message> result = new ArrayList<>();
        for (AgentEvent evt : events) {
            if (evt instanceof AgentEvent.AgentEnd ae) {
                result.addAll(ae.messages());
            }
        }
        return result;
    }
}
