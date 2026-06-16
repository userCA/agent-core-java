package io.agentcore.core.messages;

public record Usage(
        int inputTokens,
        int outputTokens,
        int cacheReadTokens,
        int cacheWriteTokens
) {
    public Usage {
        // defaults
    }

    public Usage() {
        this(0, 0, 0, 0);
    }

    /**
     * Total billable tokens: input + output.
     * Cache tokens are a sub-breakdown of input and are NOT added separately
     * to avoid double-counting (providers vary in whether cache tokens
     * are included in inputTokens or reported separately).
     */
    public int totalTokens() {
        return inputTokens + outputTokens;
    }

    /**
     * Total tokens including cache breakdowns.
     * Use this for capacity/estimation purposes where all token categories matter.
     */
    public int totalTokensWithCache() {
        return inputTokens + outputTokens + cacheReadTokens + cacheWriteTokens;
    }
}
