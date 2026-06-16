package io.agentcore.tools.operations;

import java.util.List;

/**
 * Sandbox resource quota configuration.
 */
public record SandboxQuota(
        double cpuCores,
        int memoryMb,
        int timeoutSeconds,
        int diskMb,
        boolean networkAllowed,
        List<String> allowWritePaths,
        List<String> denyPaths
) {
    public SandboxQuota() {
        this(1.0, 512, 120, 100, true, List.of(), List.of());
    }
}
