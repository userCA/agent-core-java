package io.agentcore.hooks;

import java.util.Map;

/**
 * Tool hook interfaces and their associated result types.
 *
 * <p>Before-hooks run before each tool call and may block execution or mutate arguments.
 * After-hooks run after each tool call completes and may inject metadata into the event stream.
 *
 * <p>Both hook types are designed for cross-cutting concerns such as:
 * <ul>
 *   <li>Permission checks and audit logging</li>
 *   <li>Argument sanitization or validation</li>
 *   <li>Result post-processing and telemetry</li>
 * </ul>
 *
 * @see io.agentcore.middleware.ToolHooksMiddleware
 */
public final class ToolHook {

    private ToolHook() {}

    // ==================== Before ====================

    /**
     * Hook invoked before a tool call is executed.
     *
     * <p>Multiple before-hooks are executed in registration order. If any hook
     * returns a blocking result, subsequent hooks are skipped and the tool call
     * is prevented.
     */
    @FunctionalInterface
    public interface BeforeToolHook {
        BeforeResult apply(String toolCallId, String toolName, Map<String, Object> input);
    }

    /**
     * Result of a {@link BeforeToolHook} invocation.
     *
     * <ul>
     *   <li>{@link #proceed()} — allow the tool call to execute (default)</li>
     *   <li>{@link #block(String)} — prevent execution and return an error result</li>
     *   <li>{@link #mutateArgs(Map)} — override specific input arguments before execution</li>
     * </ul>
     */
    public record BeforeResult(
            boolean shouldBlock,
            String blockReason,
            Map<String, Object> argOverrides) {

        /** Allow the tool call to proceed without modifications. */
        public static BeforeResult proceed() {
            return new BeforeResult(false, null, null);
        }

        /**
         * Block the tool call. The tool will not execute; instead, an error result
         * with the given reason is returned to the model.
         *
         * @param reason human-readable explanation for why the call was blocked
         */
        public static BeforeResult block(String reason) {
            return new BeforeResult(true, reason, null);
        }

        /**
         * Allow the tool call but override specific input arguments.
         *
         * <p>Only the keys present in {@code overrides} are replaced; all other
         * arguments pass through unchanged. Multiple before-hooks may each contribute
         * overrides; later hooks' overrides take precedence.
         *
         * @param overrides map of argument names to replacement values
         */
        public static BeforeResult mutateArgs(Map<String, Object> overrides) {
            return new BeforeResult(false, null, overrides);
        }
    }

    // ==================== After ====================

    /**
     * Hook invoked after a tool call has completed.
     *
     * <p>Multiple after-hooks are executed in registration order. Each receives
     * the same tool call information and result text.
     */
    @FunctionalInterface
    public interface AfterToolHook {
        AfterResult apply(
                String toolCallId, String toolName, String resultText, boolean isError);
    }

    /**
     * Result of an {@link AfterToolHook} invocation.
     *
     * <p>The {@code metadata} map, when non-null, is emitted as a custom event
     * of type {@code agent_core.tool_hook_after} so that downstream consumers (SSE clients,
     * logging systems) can observe hook activity.
     */
    public record AfterResult(Map<String, Object> metadata) {

        /** No post-processing; pass the tool result through unchanged. */
        public static AfterResult passthrough() {
            return new AfterResult(null);
        }

        /**
         * Attach metadata to the tool call result event stream.
         *
         * @param metadata arbitrary key-value pairs emitted as a custom event
         */
        public static AfterResult withMetadata(Map<String, Object> metadata) {
            return new AfterResult(metadata);
        }
    }
}
