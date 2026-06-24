package io.agentcore.extensions;

import io.agentcore.model.Content.ToolCallContent;
import io.agentcore.model.ToolResult;

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

    // ── Before-agent-start ────────────────────────────────────

    /**
     * Result returned from {@link Extension#onBeforeAgentStart(String, String)}.
     *
     * <p>Sealed interface with one variant:
     * <ul>
     *   <li>{@link ModifySystemPrompt} — replace the system prompt</li>
     * </ul>
     */
    public sealed interface BeforeAgentStartResult {

        /** Replace the system prompt with a modified version. */
        record ModifySystemPrompt(String systemPrompt) implements BeforeAgentStartResult {
            public ModifySystemPrompt {
                if (systemPrompt == null) throw new IllegalArgumentException("systemPrompt must not be null");
            }
        }
    }

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

        /** Proceed with execution, optionally mutating arguments and/or carrying metadata. */
        record Proceed(Map<String, Object> mutatedArguments, Map<String, Object> metadata) implements ToolCallHookResult {
            public Proceed {
                if (metadata != null) metadata = Map.copyOf(metadata);
            }

            /** No-op: proceed with original arguments unchanged. */
            public static final Proceed NO_CHANGE = new Proceed(null, null);

            /** Convenience: proceed with mutated arguments only, no metadata. */
            public Proceed(Map<String, Object> mutatedArguments) {
                this(mutatedArguments, null);
            }
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
     * <p>Sealed interface with one variant:
     * <ul>
     *   <li>{@link ModifyResult} — replace the tool result, optionally overriding isError/terminate</li>
     * </ul>
     */
    public sealed interface AfterToolCallHookResult {

        /**
         * Replace the tool result with modified fields.
         * @param content   replacement content (null = keep original)
         * @param details   replacement details (null = keep original)
         * @param isError   override isError flag (null = keep original)
         * @param terminate override terminate flag (null = keep original)
         */
        record ModifyResult(
                List<io.agentcore.model.Content> content,
                Map<String, Object> details,
                Boolean isError,
                Boolean terminate
        ) implements AfterToolCallHookResult {
            public ModifyResult {
                if (content != null) content = List.copyOf(content);
            }

            /** Convenience: modify only content. */
            public ModifyResult(List<io.agentcore.model.Content> content) {
                this(content, null, null, null);
            }
        }
    }
}
