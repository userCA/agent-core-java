package io.agentcore.tools.util;

import java.util.List;

/**
 * Sandbox resource quota configuration for tool execution.
 *
 * @param cpuCores        CPU core limit (currently advisory)
 * @param memoryMb        memory limit in MB (currently advisory)
 * @param timeoutSeconds  max command execution time in seconds
 * @param diskMb          disk usage limit in MB (currently advisory)
 * @param networkAllowed  whether network access is permitted
 * @param allowWritePaths if non-null and non-empty, only these directories are writable
 * @param denyPaths       path prefixes that are always denied for read/write
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
    /** Default sandbox: 1 CPU, 512 MB, 120 s timeout, network allowed, no path restrictions. */
    public SandboxQuota() {
        this(1.0, 512, 120, 100, true, List.of(), List.of());
    }
}
