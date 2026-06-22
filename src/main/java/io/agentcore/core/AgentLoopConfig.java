package io.agentcore.core;

import io.agentcore.extensions.HookTypes.*;
import io.agentcore.providers.AuthSource;
import io.agentcore.providers.ModelInfo;
import io.agentcore.providers.ProviderAuth;
import io.agentcore.tools.ToolRegistry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Configuration snapshot passed into the agent loop.
 *
 * <p>Mirrors Python {@code agent_core/core/context.py} AgentLoopConfig dataclass.
 * Uses functional interfaces for the callback hooks.
 */
public final class AgentLoopConfig {

    /**
     * Tool execution mode: sequential or parallel.
     */
    public enum ToolExecutionMode {
        SEQUENTIAL, PARALLEL;
        
        public static ToolExecutionMode fromString(String value) {
            if (value == null) return PARALLEL;
            return switch (value.toLowerCase()) {
                case "sequential" -> SEQUENTIAL;
                case "parallel" -> PARALLEL;
                default -> PARALLEL;
            };
        }
    }

    // ── Required fields ────────────────────────────────────────
    private final ModelInfo model;
    private final StreamFunction streamFn;
    private final ConvertToLlm convertToLlm;
    private final AuthResolver authResolver;

    // ── Optional fields ────────────────────────────────────────
    private final TransformContext transformContext;
    private final String thinkingLevel;
    private final ToolExecutionMode toolExecution;
    private final Double temperature;
    private final Integer maxTokens;
    private final ToolRegistry toolRegistry;
    private final BeforeToolCallHook beforeToolCall;
    private final AfterToolCallHook afterToolCall;
    private final Double toolTimeout;
    private final Integer maxTurns;
    private final int maxRetries;
    private final double retryBaseDelay;
    private final double retryMaxDelay;
    private final int toolResultMaxChars;
    private final CompactCallback compactCallback;
    private final Object mutationQueue;  // FileMutationQueue
    private final MessageDrainer getSteeringMessages;
    private final MessageDrainer getFollowUpMessages;
    private final HumanInputGate humanInputGate;

    private AgentLoopConfig(Builder b) {
        this.model = b.model;
        this.streamFn = b.streamFn;
        this.convertToLlm = b.convertToLlm;
        this.authResolver = b.authResolver;
        this.transformContext = b.transformContext;
        this.thinkingLevel = b.thinkingLevel != null ? b.thinkingLevel : "off";
        this.toolExecution = b.toolExecution != null ? b.toolExecution : ToolExecutionMode.PARALLEL;
        this.temperature = b.temperature;
        this.maxTokens = b.maxTokens;
        this.toolRegistry = b.toolRegistry;
        this.beforeToolCall = b.beforeToolCall;
        this.afterToolCall = b.afterToolCall;
        this.toolTimeout = b.toolTimeout != null ? b.toolTimeout : 120.0;
        this.maxTurns = b.maxTurns;
        this.maxRetries = b.maxRetries;
        this.retryBaseDelay = b.retryBaseDelay;
        this.retryMaxDelay = b.retryMaxDelay;
        this.toolResultMaxChars = b.toolResultMaxChars;
        this.compactCallback = b.compactCallback;
        this.mutationQueue = b.mutationQueue;
        this.getSteeringMessages = b.getSteeringMessages;
        this.getFollowUpMessages = b.getFollowUpMessages;
        this.humanInputGate = b.humanInputGate;
    }

    // ── Functional interfaces ──────────────────────────────────

    /**
     * Stream LLM response events.
     */
    @FunctionalInterface
    public interface StreamFunction {
        java.util.Iterator<io.agentcore.providers.StreamEvent> stream(
            ModelInfo model, List<Map<String,Object>> messages,
            List<Map<String,Object>> tools, String systemPrompt,
            String thinkingLevel, Double temperature, Integer maxTokens,
            AtomicBoolean signal, ProviderAuth auth);
    }

    /**
     * Convert internal messages to LLM-specific format.
     */
    @FunctionalInterface
    public interface ConvertToLlm {
        List<Map<String,Object>> convert(List<Message> messages);
    }

    /**
     * Resolve auth credentials for a provider.
     */
    @FunctionalInterface
    public interface AuthResolver {
        ProviderAuth resolve(String providerName);
    }

    /**
     * Transform the LLM message context before each call.
     */
    @FunctionalInterface
    public interface TransformContext {
        List<Map<String,Object>> transform(List<Map<String,Object>> messages, AtomicBoolean signal);
    }

    /**
     * Before-tool-call hook using typed context and result.
     */
    @FunctionalInterface
    public interface BeforeToolCallHook {
        ToolCallHookResult apply(ToolCallContext context);
    }

    /**
     * After-tool-call hook using typed context and result.
     */
    @FunctionalInterface
    public interface AfterToolCallHook {
        AfterToolCallHookResult apply(AfterToolCallContext context);
    }

    /**
     * Drain pending steering/follow-up messages.
     */
    @FunctionalInterface
    public interface MessageDrainer {
        List<Message> drain();
    }

    /**
     * Trigger context compaction on overflow.
     */
    @FunctionalInterface
    public interface CompactCallback {
        boolean compact(List<Message> messages);
    }

    // ── Builder ────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    /**
     * Create a builder pre-populated with this config's values.
     */
    public Builder toBuilder() {
        Builder b = new Builder();
        b.model = this.model;
        b.streamFn = this.streamFn;
        b.convertToLlm = this.convertToLlm;
        b.authResolver = this.authResolver;
        b.transformContext = this.transformContext;
        b.thinkingLevel = this.thinkingLevel;
        b.toolExecution = this.toolExecution;
        b.temperature = this.temperature;
        b.maxTokens = this.maxTokens;
        b.toolRegistry = this.toolRegistry;
        b.beforeToolCall = this.beforeToolCall;
        b.afterToolCall = this.afterToolCall;
        b.toolTimeout = this.toolTimeout;
        b.maxTurns = this.maxTurns;
        b.maxRetries = this.maxRetries;
        b.retryBaseDelay = this.retryBaseDelay;
        b.retryMaxDelay = this.retryMaxDelay;
        b.toolResultMaxChars = this.toolResultMaxChars;
        b.compactCallback = this.compactCallback;
        b.mutationQueue = this.mutationQueue;
        b.getSteeringMessages = this.getSteeringMessages;
        b.getFollowUpMessages = this.getFollowUpMessages;
        b.humanInputGate = this.humanInputGate;
        return b;
    }

    public static final class Builder {
        private ModelInfo model;
        private StreamFunction streamFn;
        private ConvertToLlm convertToLlm;
        private AuthResolver authResolver;
        private TransformContext transformContext;
        private String thinkingLevel = "off";
        private ToolExecutionMode toolExecution = ToolExecutionMode.PARALLEL;
        private Double temperature;
        private Integer maxTokens;
        private ToolRegistry toolRegistry;
        private BeforeToolCallHook beforeToolCall;
        private AfterToolCallHook afterToolCall;
        private Double toolTimeout = 120.0;
        private Integer maxTurns;
        private int maxRetries = 3;
        private double retryBaseDelay = 1.0;
        private double retryMaxDelay = 60.0;
        private int toolResultMaxChars = 4000;
        private CompactCallback compactCallback;
        private Object mutationQueue;
        private MessageDrainer getSteeringMessages;
        private MessageDrainer getFollowUpMessages;
        private HumanInputGate humanInputGate;

        public Builder model(ModelInfo v) { this.model = v; return this; }
        public Builder streamFn(StreamFunction v) { this.streamFn = v; return this; }
        public Builder convertToLlm(ConvertToLlm v) { this.convertToLlm = v; return this; }
        public Builder authResolver(AuthResolver v) { this.authResolver = v; return this; }
        public Builder transformContext(TransformContext v) { this.transformContext = v; return this; }
        public Builder thinkingLevel(String v) { this.thinkingLevel = v; return this; }
        public Builder toolExecution(ToolExecutionMode v) { this.toolExecution = v; return this; }
        public Builder toolExecution(String v) { this.toolExecution = ToolExecutionMode.fromString(v); return this; }
        public Builder temperature(Double v) { this.temperature = v; return this; }
        public Builder maxTokens(Integer v) { this.maxTokens = v; return this; }
        public Builder toolRegistry(ToolRegistry v) { this.toolRegistry = v; return this; }
        public Builder beforeToolCall(BeforeToolCallHook v) { this.beforeToolCall = v; return this; }
        public Builder afterToolCall(AfterToolCallHook v) { this.afterToolCall = v; return this; }
        public Builder toolTimeout(Double v) { this.toolTimeout = v; return this; }
        public Builder maxTurns(Integer v) { this.maxTurns = v; return this; }
        public Builder maxRetries(int v) { this.maxRetries = v; return this; }
        public Builder retryBaseDelay(double v) { this.retryBaseDelay = v; return this; }
        public Builder retryMaxDelay(double v) { this.retryMaxDelay = v; return this; }
        public Builder toolResultMaxChars(int v) { this.toolResultMaxChars = v; return this; }
        public Builder compactCallback(CompactCallback v) { this.compactCallback = v; return this; }
        public Builder mutationQueue(Object v) { this.mutationQueue = v; return this; }
        public Builder getSteeringMessages(MessageDrainer v) { this.getSteeringMessages = v; return this; }
        public Builder getFollowUpMessages(MessageDrainer v) { this.getFollowUpMessages = v; return this; }
        public Builder humanInputGate(HumanInputGate v) { this.humanInputGate = v; return this; }

        public AgentLoopConfig build() {
            if (model == null) throw new IllegalStateException("model is required");
            if (streamFn == null) throw new IllegalStateException("streamFn is required");
            if (convertToLlm == null) throw new IllegalStateException("convertToLlm is required");
            if (authResolver == null) throw new IllegalStateException("authResolver is required");
            return new AgentLoopConfig(this);
        }
    }

    // ── Getters ────────────────────────────────────────────────

    public ModelInfo model() { return model; }
    public StreamFunction streamFn() { return streamFn; }
    public ConvertToLlm convertToLlm() { return convertToLlm; }
    public AuthResolver authResolver() { return authResolver; }
    public TransformContext transformContext() { return transformContext; }
    public String thinkingLevel() { return thinkingLevel; }
    public ToolExecutionMode toolExecution() { return toolExecution; }
    public Double temperature() { return temperature; }
    public Integer maxTokens() { return maxTokens; }
    public ToolRegistry toolRegistry() { return toolRegistry; }
    public BeforeToolCallHook beforeToolCall() { return beforeToolCall; }
    public AfterToolCallHook afterToolCall() { return afterToolCall; }
    public Double toolTimeout() { return toolTimeout; }
    public Integer maxTurns() { return maxTurns; }
    public int maxRetries() { return maxRetries; }
    public double retryBaseDelay() { return retryBaseDelay; }
    public double retryMaxDelay() { return retryMaxDelay; }
    public int toolResultMaxChars() { return toolResultMaxChars; }
    public CompactCallback compactCallback() { return compactCallback; }
    public Object mutationQueue() { return mutationQueue; }
    public MessageDrainer getSteeringMessages() { return getSteeringMessages; }
    public MessageDrainer getFollowUpMessages() { return getFollowUpMessages; }
    public HumanInputGate humanInputGate() { return humanInputGate; }
}
