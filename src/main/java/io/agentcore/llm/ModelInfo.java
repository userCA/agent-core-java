package io.agentcore.llm;

/**
 * Provider-neutral model metadata.
 *
 * <p>Mirrors Python {@code agent_core/providers/types.py} Model class.
 *
 * @param provider         provider name (e.g. "openai", "anthropic")
 * @param id               model identifier (e.g. "gpt-4o", "claude-sonnet-4-6")
 * @param contextWindow    maximum context window in tokens
 * @param maxOutputTokens  maximum output tokens per response
 * @param supportsReasoning whether the model supports extended thinking
 * @param supportsXhighThinking whether the model supports xhigh thinking level
 * @param cost             per-token cost information
 */
public record ModelInfo(
    String provider,
    String id,
    int contextWindow,
    int maxOutputTokens,
    boolean supportsReasoning,
    boolean supportsXhighThinking,
    ModelCost cost
) {
    public ModelInfo {
        if (provider == null) throw new IllegalArgumentException("provider must not be null");
        if (id == null) throw new IllegalArgumentException("id must not be null");
        if (cost == null) cost = new ModelCost();
    }

    /**
     * Convenience constructor without cost.
     */
    public ModelInfo(String provider, String id, int contextWindow, int maxOutputTokens) {
        this(provider, id, contextWindow, maxOutputTokens, false, false, new ModelCost());
    }

    /**
     * Convenience constructor with reasoning support.
     */
    public ModelInfo(String provider, String id, int contextWindow, int maxOutputTokens,
                     boolean supportsReasoning, boolean supportsXhighThinking) {
        this(provider, id, contextWindow, maxOutputTokens, supportsReasoning, supportsXhighThinking, new ModelCost());
    }

    /**
     * Per-token cost breakdown.
     */
    public record ModelCost(double input, double output, double cacheRead, double cacheWrite) {
        public ModelCost() { this(0.0, 0.0, 0.0, 0.0); }
    }
}
