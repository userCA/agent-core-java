package io.agentcore.tools.operations;

/**
 * Result of a bash command execution.
 */
public record BashResult(
        String stdout,
        String stderr,
        int returnCode,
        boolean truncated,
        String fullOutputPath,
        Object truncationInfo
) {
    public BashResult(String stdout, String stderr, int returnCode) {
        this(stdout, stderr, returnCode, false, null, null);
    }
}
