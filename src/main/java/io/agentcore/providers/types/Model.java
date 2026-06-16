package io.agentcore.providers.types;

/**
 * Provider-neutral model descriptor.
 */
public record Model(
        String provider,
        String id,
        int contextWindow,
        int maxOutputTokens,
        boolean supportsReasoning,
        boolean supportsXhighThinking,
        ModelCost cost
) {
    public Model {
        if (cost == null) cost = new ModelCost();
    }

    public Model(String provider, String id, int contextWindow, int maxOutputTokens) {
        this(provider, id, contextWindow, maxOutputTokens, false, false, new ModelCost());
    }

    public Model(String provider, String id, int contextWindow, int maxOutputTokens,
                 boolean supportsReasoning, boolean supportsXhighThinking) {
        this(provider, id, contextWindow, maxOutputTokens, supportsReasoning, supportsXhighThinking, new ModelCost());
    }
}
