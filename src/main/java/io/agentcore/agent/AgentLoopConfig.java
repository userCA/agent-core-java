package io.agentcore.agent;

import io.agentcore.extensions.HookTypes.*;
import io.agentcore.model.Message.AssistantMessage;
import io.agentcore.model.Message.ToolResultMessage;
import io.agentcore.llm.AuthSource;
import io.agentcore.llm.ModelInfo;
import io.agentcore.llm.ProviderAuth;
import io.agentcore.tools.ToolRegistry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import io.agentcore.model.Message;
import io.agentcore.model.Content;

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
        SEQUENTIAL, PARALLEL
    }

    /**
     * Retry configuration: max attempts and exponential backoff parameters.
     */
    public record RetryConfig(int maxRetries, double baseDelay, double maxDelay) {
        public static final RetryConfig DEFAULT = new RetryConfig(3, 1.0, 60.0);
    }

    /**
     * Tool execution configuration: timeout, result size, and execution mode.
     */
    public record ToolConfig(Double timeout, int resultMaxChars, ToolExecutionMode execution, int maxParallelTools) {
        public static final ToolConfig DEFAULT = new ToolConfig(120.0, 4000, ToolExecutionMode.PARALLEL, 10);

        /** Backward-compatible constructor without maxParallelTools. */
        public ToolConfig(Double timeout, int resultMaxChars, ToolExecutionMode execution) {
            this(timeout, resultMaxChars, execution, 10);
        }
    }

    // ── Required fields ────────────────────────────────────────
    private final ModelInfo model;
    private final StreamFunction streamFn;
    private final ConvertToLlm convertToLlm;
    private final AuthResolver authResolver;

    // ── Sub-configs (grouped by concern) ───────────────────────
    private final RetryConfig retryConfig;
    private final ToolConfig toolConfig;

    // ── Optional fields ────────────────────────────────────────
    private final TransformContext transformContext;
    private final String thinkingLevel;
    private final Double temperature;
    private final Integer maxTokens;
    private final ToolRegistry toolRegistry;
    private final BeforeToolCallHook beforeToolCall;
    private final AfterToolCallHook afterToolCall;
    private final Integer maxTurns;
    private final CompactCallback compactCallback;
    private final MessageDrainer getSteeringMessages;
    private final MessageDrainer getFollowUpMessages;
    private final HumanInputGate humanInputGate;
    private final PrepareNextTurn prepareNextTurn;
    private final ShouldStopAfterTurn shouldStopAfterTurn;

    private AgentLoopConfig(Builder b) {
        this.model = b.model;
        this.streamFn = b.streamFn;
        this.convertToLlm = b.convertToLlm;
        this.authResolver = b.authResolver;
        this.retryConfig = new RetryConfig(b.maxRetries, b.retryBaseDelay, b.retryMaxDelay);
        this.toolConfig = new ToolConfig(b.toolTimeout, b.toolResultMaxChars,
                b.toolExecution != null ? b.toolExecution : ToolExecutionMode.PARALLEL,
                b.maxParallelTools);
        this.transformContext = b.transformContext;
        this.thinkingLevel = b.thinkingLevel != null ? b.thinkingLevel : "off";
        this.temperature = b.temperature;
        this.maxTokens = b.maxTokens;
        this.toolRegistry = b.toolRegistry;
        this.beforeToolCall = b.beforeToolCall;
        this.afterToolCall = b.afterToolCall;
        this.maxTurns = b.maxTurns;
        this.compactCallback = b.compactCallback;
        this.getSteeringMessages = b.getSteeringMessages;
        this.getFollowUpMessages = b.getFollowUpMessages;
        this.humanInputGate = b.humanInputGate;
        this.prepareNextTurn = b.prepareNextTurn;
        this.shouldStopAfterTurn = b.shouldStopAfterTurn;
    }

    // ── Functional interfaces ──────────────────────────────────

    /**
     * Stream LLM response events.
     */
    @FunctionalInterface
    public interface StreamFunction {
        java.util.Iterator<io.agentcore.llm.StreamEvent> stream(
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

    /**
     * Callback invoked after each turn to allow dynamic config adjustment.
     * Return null to keep current config, or a NextTurnSnapshot to update.
     */
    @FunctionalInterface
    public interface PrepareNextTurn {
        NextTurnSnapshot apply(TurnContext context);
    }

    /**
     * Callback invoked after each turn to decide if the loop should stop.
     * Return true to stop the agent loop.
     */
    @FunctionalInterface
    public interface ShouldStopAfterTurn {
        boolean apply(TurnContext context);
    }

    // ── Context/Snapshot records ───────────────────────────────

    /**
     * Context provided to both prepareNextTurn and shouldStopAfterTurn callbacks.
     * Represents the completed turn's state including assistant response,
     * tool results, and full message history.
     */
    public record TurnContext(
            AssistantMessage message,
            List<ToolResultMessage> toolResults,
            List<Message> allMessages,
            List<Message> newMessages
    ) {}

    /**
     * Snapshot returned by prepareNextTurn to update config for the next turn.
     * Null fields mean "keep current value".
     */
    public record NextTurnSnapshot(
            ModelInfo model,
            String thinkingLevel,
            Double temperature
    ) {}

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
        b.toolExecution = this.toolConfig.execution();
        b.temperature = this.temperature;
        b.maxTokens = this.maxTokens;
        b.toolRegistry = this.toolRegistry;
        b.beforeToolCall = this.beforeToolCall;
        b.afterToolCall = this.afterToolCall;
        b.toolTimeout = this.toolConfig.timeout();
        b.maxTurns = this.maxTurns;
        b.maxRetries = this.retryConfig.maxRetries();
        b.retryBaseDelay = this.retryConfig.baseDelay();
        b.retryMaxDelay = this.retryConfig.maxDelay();
        b.toolResultMaxChars = this.toolConfig.resultMaxChars();
        b.maxParallelTools = this.toolConfig.maxParallelTools();
        b.compactCallback = this.compactCallback;
        b.getSteeringMessages = this.getSteeringMessages;
        b.getFollowUpMessages = this.getFollowUpMessages;
        b.humanInputGate = this.humanInputGate;
        b.prepareNextTurn = this.prepareNextTurn;
        b.shouldStopAfterTurn = this.shouldStopAfterTurn;
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
        private int maxParallelTools = 10;
        private CompactCallback compactCallback;
        private MessageDrainer getSteeringMessages;
        private MessageDrainer getFollowUpMessages;
        private HumanInputGate humanInputGate;
        private PrepareNextTurn prepareNextTurn;
        private ShouldStopAfterTurn shouldStopAfterTurn;

        public Builder model(ModelInfo v) { this.model = v; return this; }
        public Builder streamFn(StreamFunction v) { this.streamFn = v; return this; }
        public Builder convertToLlm(ConvertToLlm v) { this.convertToLlm = v; return this; }
        public Builder authResolver(AuthResolver v) { this.authResolver = v; return this; }
        public Builder transformContext(TransformContext v) { this.transformContext = v; return this; }
        public Builder thinkingLevel(String v) { this.thinkingLevel = v; return this; }
        public Builder toolExecution(ToolExecutionMode v) { this.toolExecution = v; return this; }
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
        public Builder maxParallelTools(int v) { this.maxParallelTools = v; return this; }
        public Builder compactCallback(CompactCallback v) { this.compactCallback = v; return this; }
        public Builder getSteeringMessages(MessageDrainer v) { this.getSteeringMessages = v; return this; }
        public Builder getFollowUpMessages(MessageDrainer v) { this.getFollowUpMessages = v; return this; }
        public Builder humanInputGate(HumanInputGate v) { this.humanInputGate = v; return this; }
        public Builder prepareNextTurn(PrepareNextTurn v) { this.prepareNextTurn = v; return this; }
        public Builder shouldStopAfterTurn(ShouldStopAfterTurn v) { this.shouldStopAfterTurn = v; return this; }

        /**
         * Set all retry parameters from a RetryConfig (preferred over individual setters).
         */
        public Builder retryConfig(RetryConfig c) {
            this.maxRetries = c.maxRetries();
            this.retryBaseDelay = c.baseDelay();
            this.retryMaxDelay = c.maxDelay();
            return this;
        }

        /**
         * Set all tool parameters from a ToolConfig (preferred over individual setters).
         */
        public Builder toolConfig(ToolConfig c) {
            this.toolTimeout = c.timeout();
            this.toolResultMaxChars = c.resultMaxChars();
            this.toolExecution = c.execution();
            this.maxParallelTools = c.maxParallelTools();
            return this;
        }

        public AgentLoopConfig build() {
            if (model == null) throw new IllegalStateException("model is required");
            if (streamFn == null) throw new IllegalStateException("streamFn is required");
            if (convertToLlm == null) throw new IllegalStateException("convertToLlm is required");
            if (authResolver == null) throw new IllegalStateException("authResolver is required");
            if (maxRetries < 0) throw new IllegalStateException("maxRetries must be >= 0, got " + maxRetries);
            if (maxTurns != null && maxTurns <= 0) throw new IllegalStateException("maxTurns must be > 0, got " + maxTurns);
            if (toolTimeout != null && toolTimeout <= 0) throw new IllegalStateException("toolTimeout must be > 0, got " + toolTimeout);
            if (retryBaseDelay <= 0) throw new IllegalStateException("retryBaseDelay must be > 0, got " + retryBaseDelay);
            if (retryMaxDelay < retryBaseDelay) throw new IllegalStateException("retryMaxDelay must be >= retryBaseDelay");
            if (toolResultMaxChars < 0) throw new IllegalStateException("toolResultMaxChars must be >= 0, got " + toolResultMaxChars);
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
    public Double temperature() { return temperature; }
    public Integer maxTokens() { return maxTokens; }
    public ToolRegistry toolRegistry() { return toolRegistry; }
    public BeforeToolCallHook beforeToolCall() { return beforeToolCall; }
    public AfterToolCallHook afterToolCall() { return afterToolCall; }
    public Integer maxTurns() { return maxTurns; }
    public CompactCallback compactCallback() { return compactCallback; }
    public MessageDrainer getSteeringMessages() { return getSteeringMessages; }
    public MessageDrainer getFollowUpMessages() { return getFollowUpMessages; }
    public HumanInputGate humanInputGate() { return humanInputGate; }
    public PrepareNextTurn prepareNextTurn() { return prepareNextTurn; }
    public ShouldStopAfterTurn shouldStopAfterTurn() { return shouldStopAfterTurn; }

    // ── Sub-config getters (preferred) ─────────────────────────

    public RetryConfig retryConfig() { return retryConfig; }
    public ToolConfig toolConfig() { return toolConfig; }
}
