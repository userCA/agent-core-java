package io.agentcore.core;

import io.agentcore.core.Content.TextContent;
import io.agentcore.core.Message.*;
import io.agentcore.extensions.Extension;
import io.agentcore.extensions.ExtensionRunner;
import io.agentcore.providers.*;
import io.agentcore.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * High-level Agent facade — stateful wrapper around AgentLoop.
 *
 * <p>Mirrors Python {@code agent_core/core/agent.py} Agent class.
 * Integrates {@link ExtensionRunner} for unified hook dispatch via the
 * Extension SPI (both manual registration and ServiceLoader discovery).
 */
public class Agent {

    private static final Logger log = LoggerFactory.getLogger(Agent.class);

    private final AgentLoopConfig baseConfig;
    private final AgentContext context;
    private final AtomicBoolean abortSignal = new AtomicBoolean(false);
    private final List<Consumer<AgentEvent>> subscribers = new CopyOnWriteArrayList<>();

    // Extension-based hook system (unified)
    private final ExtensionRunner extensionRunner;
    private final List<AgentLoopConfig.TransformContext> transformHooks = new CopyOnWriteArrayList<>();

    private final PendingMessageQueue steeringQueue;
    private final PendingMessageQueue followUpQueue;

    private final Set<String> pendingToolCalls = Collections.synchronizedSet(new LinkedHashSet<>());

    public Agent(AgentLoopConfig config) {
        this(config, new AgentContext(), List.of());
    }

    public Agent(AgentLoopConfig config, AgentContext context) {
        this(config, context, List.of());
    }

    public Agent(AgentLoopConfig config, AgentContext context, List<Extension> extensions) {
        this.baseConfig = config;
        this.context = context;
        this.extensionRunner = new ExtensionRunner(
                extensions != null ? extensions : List.of());
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
        UserMessage userMsg = new UserMessage(
                List.of(new TextContent(text)),
                System.currentTimeMillis() / 1000.0);

        // Run before-agent-start hooks via extensions
        runBeforeAgentStartHooks(text);

        return runLoop(List.of(userMsg), onEvent, compactCallback);
    }

    public List<Message> continue_(Consumer<AgentEvent> onEvent) {
        return continue_(onEvent, null);
    }

    public List<Message> continue_(Consumer<AgentEvent> onEvent,
                                   AgentLoopConfig.CompactCallback compactCallback) {
        return runLoop(List.of(), onEvent, compactCallback);
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

    // ── Extension / Hook registration ──────────────────────

    /**
     * Add a transform-context hook (called before each LLM call).
     */
    public void addTransformContextHook(AgentLoopConfig.TransformContext hook) {
        transformHooks.add(hook);
    }

    // ── State accessors ────────────────────────────────────

    public boolean isStreaming() { return context.isStreaming(); }
    public String errorMessage() { return context.errorMessage(); }
    public Set<String> pendingToolCalls() { return Set.copyOf(pendingToolCalls); }
    public List<Message> messages() { return List.copyOf(context.messages()); }
    public AgentContext context() { return context; }
    public ExtensionRunner extensionRunner() { return extensionRunner; }

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

    /**
     * Common loop execution logic shared by prompt() and continue_().
     */
    private List<Message> runLoop(List<Message> newMessages,
                                  Consumer<AgentEvent> onEvent,
                                  AgentLoopConfig.CompactCallback compactCallback) {
        abortSignal.set(false);
        context.setErrorMessage(null);
        context.tryStartStreaming();

        Consumer<AgentEvent> emitter = createEmitter(onEvent);
        AgentLoopConfig config = buildConfigWithHooks(compactCallback);
        AgentLoop loop = new AgentLoop(config, context);

        List<AgentEvent> allEvents = new ArrayList<>();
        try {
            loop.run(newMessages, abortSignal, evt -> {
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

    /**
     * Run before-agent-start hooks via ExtensionRunner.
     */
    private void runBeforeAgentStartHooks(String prompt) {
        if (!extensionRunner.hasExtensions()) return;
        String sysPrompt = context.systemPrompt();
        Map<String, Object> result = extensionRunner.onBeforeAgentStart(prompt, sysPrompt);
        if (result != null && result.get("system_prompt") instanceof String sp) {
            context.setSystemPrompt(sp);
        }
    }

    private AgentLoopConfig buildConfigWithHooks(AgentLoopConfig.CompactCallback compactCallback) {
        AgentLoopConfig.Builder b = baseConfig.toBuilder();

        // Wire extension runner hooks (typed)
        if (extensionRunner.hasExtensions()) {
            b.beforeToolCall(extensionRunner::beforeToolCall);
            b.afterToolCall(extensionRunner::afterToolCall);
        }

        // Wire transform hooks (ad-hoc + extension)
        boolean hasExtTransform = extensionRunner.hasExtensions();
        boolean hasAdHocTransform = !transformHooks.isEmpty();
        if (hasExtTransform || hasAdHocTransform) {
            b.transformContext(this::chainAllTransformHooks);
        }

        b.getSteeringMessages(() -> steeringQueue.drain());
        b.getFollowUpMessages(() -> followUpQueue.drain());

        if (compactCallback != null) {
            b.compactCallback(compactCallback);
        }

        return b.build();
    }

    private List<Map<String, Object>> chainAllTransformHooks(
            List<Map<String, Object>> msgs, AtomicBoolean signal) {
        List<Map<String, Object>> result = msgs;
        // Extension transforms first
        if (extensionRunner.hasExtensions()) {
            try {
                result = extensionRunner.transformContext(result, signal);
            } catch (Exception e) {
                log.warn("Extension transformContext failed", e);
            }
        }
        // Then ad-hoc transforms
        for (var hook : transformHooks) {
            try {
                result = hook.transform(result, signal);
            } catch (Exception e) {
                log.warn("transformContext hook failed", e);
            }
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
