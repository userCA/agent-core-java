package io.agentcore.core.context;

import io.agentcore.core.humaninput.HumanInputGate;
import io.agentcore.core.messages.AgentMessage;
import io.agentcore.core.state.ThinkingLevel;
import io.agentcore.providers.base.StreamRequest;
import io.agentcore.providers.types.Model;
import io.agentcore.providers.types.ProviderAuth;
import io.agentcore.providers.types.StreamEvent;
import io.agentcore.tools.base.ToolDefinition;
import io.agentcore.tools.base.ToolRegistry;
import io.agentcore.tools.mutation.FileMutationQueue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Immutable per-run configuration for the agent loop.
 * Use {@link Builder} to construct instances.
 */
public class AgentLoopConfig {
    // Required
    private final Model model;
    private final Function<StreamRequest, Flow.Publisher<StreamEvent>> streamFn;
    private final ConvertToLlm convertToLlm;
    private final Function<String, CompletableFuture<ProviderAuth>> authResolver;

    // Optional
    private final TransformContext transformContext;
    private final ThinkingLevel thinkingLevel;
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
    private final FileMutationQueue mutationQueue;
    private final MessageDrainer getSteeringMessages;
    private final MessageDrainer getFollowUpMessages;
    private final HumanInputGate humanInputGate;
    private final Function<List<ToolDefinition>, List<Map<String, Object>>> toolFormatConverter;

    private AgentLoopConfig(Builder b) {
        this.model = b.model;
        this.streamFn = b.streamFn;
        this.convertToLlm = b.convertToLlm;
        this.authResolver = b.authResolver;
        this.transformContext = b.transformContext;
        this.thinkingLevel = b.thinkingLevel;
        this.toolExecution = b.toolExecution;
        this.temperature = b.temperature;
        this.maxTokens = b.maxTokens;
        this.toolRegistry = b.toolRegistry;
        this.beforeToolCall = b.beforeToolCall;
        this.afterToolCall = b.afterToolCall;
        this.toolTimeout = b.toolTimeout;
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
        this.toolFormatConverter = b.toolFormatConverter;
    }

    // --- Getters ---
    public Model model() { return model; }
    public Function<StreamRequest, Flow.Publisher<StreamEvent>> streamFn() { return streamFn; }
    public ConvertToLlm convertToLlm() { return convertToLlm; }
    public Function<String, CompletableFuture<ProviderAuth>> authResolver() { return authResolver; }
    public TransformContext transformContext() { return transformContext; }
    public ThinkingLevel thinkingLevel() { return thinkingLevel; }
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
    public FileMutationQueue mutationQueue() { return mutationQueue; }
    public MessageDrainer getSteeringMessages() { return getSteeringMessages; }
    public MessageDrainer getFollowUpMessages() { return getFollowUpMessages; }
    public HumanInputGate humanInputGate() { return humanInputGate; }
    public Function<List<ToolDefinition>, List<Map<String, Object>>> toolFormatConverter() {
        return toolFormatConverter;
    }

    /**
     * Builder for constructing immutable {@link AgentLoopConfig} instances.
     */
    public static class Builder {
        // Required
        private final Model model;
        private final Function<StreamRequest, Flow.Publisher<StreamEvent>> streamFn;
        private final ConvertToLlm convertToLlm;
        private final Function<String, CompletableFuture<ProviderAuth>> authResolver;

        // Optional — defaults
        private TransformContext transformContext;
        private ThinkingLevel thinkingLevel = ThinkingLevel.OFF;
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
        private FileMutationQueue mutationQueue;
        private MessageDrainer getSteeringMessages;
        private MessageDrainer getFollowUpMessages;
        private HumanInputGate humanInputGate;
        private Function<List<ToolDefinition>, List<Map<String, Object>>> toolFormatConverter;

        public Builder(Model model,
                       Function<StreamRequest, Flow.Publisher<StreamEvent>> streamFn,
                       ConvertToLlm convertToLlm,
                       Function<String, CompletableFuture<ProviderAuth>> authResolver) {
            this.model = model;
            this.streamFn = streamFn;
            this.convertToLlm = convertToLlm;
            this.authResolver = authResolver;
        }

        public Builder transformContext(TransformContext v) { this.transformContext = v; return this; }
        public Builder thinkingLevel(ThinkingLevel v) { this.thinkingLevel = v; return this; }
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
        public Builder compactCallback(CompactCallback v) { this.compactCallback = v; return this; }
        public Builder mutationQueue(FileMutationQueue v) { this.mutationQueue = v; return this; }
        public Builder getSteeringMessages(MessageDrainer v) { this.getSteeringMessages = v; return this; }
        public Builder getFollowUpMessages(MessageDrainer v) { this.getFollowUpMessages = v; return this; }
        public Builder humanInputGate(HumanInputGate v) { this.humanInputGate = v; return this; }
        public Builder toolFormatConverter(Function<List<ToolDefinition>, List<Map<String, Object>>> v) {
            this.toolFormatConverter = v;
            return this;
        }

        public AgentLoopConfig build() {
            return new AgentLoopConfig(this);
        }
    }
}
