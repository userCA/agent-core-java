package io.agentcore.core.context;

import io.agentcore.tools.base.ToolResult;

/**
 * Typed result from an {@link AfterToolCallHook}.
 *
 * @param resultOverride if non-null, replaces the original tool result
 */
public record AfterToolCallResult(
        ToolResult resultOverride
) {
    /** Convenience: keep the original result. */
    public static AfterToolCallResult keepOriginal() {
        return new AfterToolCallResult(null);
    }

    /** Convenience: override with a new result. */
    public static AfterToolCallResult override(ToolResult result) {
        return new AfterToolCallResult(result);
    }
}
