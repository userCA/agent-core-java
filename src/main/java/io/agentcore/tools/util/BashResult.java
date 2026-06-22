package io.agentcore.tools.util;

/**
 * Result of a bash command execution.
 *
 * @param stdout          captured standard output (may be truncated)
 * @param stderr          captured standard error (may be truncated)
 * @param returnCode      exit code (-1 if timed out or failed to start)
 * @param truncated       whether output was truncated due to size limits
 * @param fullOutputPath  path to full output file if output was saved to disk, else null
 * @param truncationInfo  human-readable truncation details, else null
 */
public record BashResult(
        String stdout,
        String stderr,
        int returnCode,
        boolean truncated,
        String fullOutputPath,
        Object truncationInfo
) {
    /** Convenience constructor for non-truncated results. */
    public BashResult(String stdout, String stderr, int returnCode) {
        this(stdout, stderr, returnCode, false, null, null);
    }
}
