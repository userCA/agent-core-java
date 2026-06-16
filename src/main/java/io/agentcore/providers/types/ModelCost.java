package io.agentcore.providers.types;

public record ModelCost(
        double input,
        double output,
        double cacheRead,
        double cacheWrite
) {
    public ModelCost() { this(0.0, 0.0, 0.0, 0.0); }
}
