package io.agentcore.core.context;

import java.util.Map;

/**
 * Typed result from a {@link BeforeToolCallHook}.
 *
 * @param block       if true, prevent the tool from executing
 * @param reason      human-readable reason when blocking (may be null)
 * @param mutatedArgs replacement arguments (may be null to keep originals)
 */
public record BeforeToolCallResult(
        boolean block,
        String reason,
        Map<String, Object> mutatedArgs
) {
    /** Convenience: proceed normally (no block, no mutation). */
    public static BeforeToolCallResult proceed() {
        return new BeforeToolCallResult(false, null, null);
    }

    /** Convenience: block the tool call with the given reason. */
    public static BeforeToolCallResult blocked(String reason) {
        return new BeforeToolCallResult(true, reason, null);
    }

    /** Convenience: proceed with mutated arguments. */
    public static BeforeToolCallResult withArgs(Map<String, Object> mutatedArgs) {
        return new BeforeToolCallResult(false, null, mutatedArgs);
    }
}
