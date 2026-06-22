package io.agentcore.extensions;

import io.agentcore.core.Content.ToolCallContent;
import io.agentcore.tools.ToolResult;

import java.util.List;
import java.util.Map;

/**
 * Strongly-typed hook context and result types for the Extension SPI.
 *
 * <p>Replaces the previous {@code Map<String, Object>} convention with
 * records and sealed interfaces, enabling compile-time safety and
 * exhaustive pattern matching.
 */
public final class HookTypes {

    private HookTypes() {}

    // ── Before-tool-call ──────────────────────────────────────

    /**
     * Context passed to {@link Extension#beforeToolCall(ToolCallContext)}.
     */
    public record ToolCallContext(
            ToolCallContent toolCall,
            Map<String, Object> arguments
    ) {
        public ToolCallContext {
            if (toolCall == null) throw new IllegalArgumentException("toolCall must not be null");
            if (arguments == null) arguments = Map.of();
        }

        /** Convenience: tool name. */
        public String toolName() { return toolCall.name(); }

        /** Convenience: tool call ID. */
        public String toolCallId() { return toolCall.id(); }
    }

    /**
     * Result returned from {@link Extension#beforeToolCall(ToolCallContext)}.
     *
     * <p>Sealed interface with three variants:
     * <ul>
     *   <li>{@link Block} — prevent the tool call from executing</li>
     *   <li>{@link Proceed} — allow execution, optionally with mutated args</li>
     *   <li>{@link InjectMetadata} — attach metadata (e.g. sandbox quota)</li>
     * </ul>
     */
    public sealed interface ToolCallHookResult {

        /** Block the tool call with a reason. */
        record Block(String reason) implements ToolCallHookResult {
            public Block {
                if (reason == null) reason = "Blocked by hook.";
            }
        }

        /** Proceed with execution, optionally mutating arguments. */
        record Proceed(Map<String, Object> mutatedArguments) implements ToolCallHookResult {
            /** No-op: proceed with original arguments unchanged. */
            public static final Proceed NO_CHANGE = new Proceed(null);
        }

        /** Attach metadata (e.g. sandbox quota) without blocking or mutating args. */
        record InjectMetadata(Map<String, Object> metadata) implements ToolCallHookResult {
            public InjectMetadata {
                if (metadata == null) metadata = Map.of();
                else metadata = Map.copyOf(metadata);
            }
        }
    }

    // ── After-tool-call ───────────────────────────────────────

    /**
     * Context passed to {@link Extension#afterToolCall(AfterToolCallContext)}.
     */
    public record AfterToolCallContext(
            ToolCallContent toolCall,
            Map<String, Object> arguments,
            ToolResult result,
            boolean isError
    ) {
        public AfterToolCallContext {
            if (toolCall == null) throw new IllegalArgumentException("toolCall must not be null");
            if (arguments == null) arguments = Map.of();
        }

        /** Convenience: tool name. */
        public String toolName() { return toolCall.name(); }

        /** Convenience: tool call ID. */
        public String toolCallId() { return toolCall.id(); }
    }

    /**
     * Result returned from {@link Extension#afterToolCall(AfterToolCallContext)}.
     *
     * <p>Sealed interface with two variants:
     * <ul>
     *   <li>{@link ModifyResult} — replace the tool result content</li>
     *   <li>{@link NoOp} — no modification</li>
     * </ul>
     */
    public sealed interface AfterToolCallHookResult {

        /** Replace the tool result with modified content. */
        record ModifyResult(List<io.agentcore.core.Content> content) implements AfterToolCallHookResult {
            public ModifyResult {
                if (content == null) content = List.of();
                else content = List.copyOf(content);
            }
        }

        /** No-op: keep original result. */
        record NoOp() implements AfterToolCallHookResult {
            public static final NoOp INSTANCE = new NoOp();
        }
    }
}
