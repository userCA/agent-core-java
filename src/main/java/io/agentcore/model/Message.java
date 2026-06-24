package io.agentcore.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Message types: user, assistant, tool_result, custom — discriminated union.
 *
 * <p>Mirrors Python {@code agent_core/core/messages.py}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "role")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Message.UserMessage.class, name = "user"),
    @JsonSubTypes.Type(value = Message.AssistantMessage.class, name = "assistant"),
    @JsonSubTypes.Type(value = Message.ToolResultMessage.class, name = "tool_result"),
    @JsonSubTypes.Type(value = Message.CustomMessage.class, name = "custom"),
})
public sealed interface Message {

    /**
     * Returns the current epoch time in seconds.
     */
    static double nowEpochSeconds() {
        return System.currentTimeMillis() / 1000.0;
    }

    /**
     * Timestamp (epoch seconds) when this message was created.
     */
    double timestamp();

    // ── Usage ──────────────────────────────────────────────────

    /**
     * Token usage statistics for an assistant response.
     */
    record Usage(int inputTokens, int outputTokens,
                 int cacheReadTokens, int cacheWriteTokens) {

        public Usage() { this(0, 0, 0, 0); }

        /**
         * Total tokens for this response (input + output, excluding cache).
         */
        public int totalTokens() {
            return inputTokens + outputTokens;
        }

        /**
         * Total tokens including cache reads and writes.
         */
        public int totalTokensWithCache() {
            return inputTokens + outputTokens + cacheReadTokens + cacheWriteTokens;
        }
    }

    // ── StopReason ─────────────────────────────────────────────

    /**
     * Reason the LLM stopped generating.
     */
    enum StopReason {
        STOP, TOOL_USE, LENGTH, CONTENT_FILTER, ERROR, ABORTED;

        private static final System.Logger LOG =
                System.getLogger(StopReason.class.getName());

        public static StopReason fromValue(String value) {
            if (value == null) return STOP;
            return switch (value.toLowerCase()) {
                case "stop", "end_turn" -> STOP;
                case "tool_use", "tool_calls" -> TOOL_USE;
                case "length" -> LENGTH;
                case "content_filter" -> CONTENT_FILTER;
                case "error" -> ERROR;
                case "aborted" -> ABORTED;
                default -> {
                    LOG.log(System.Logger.Level.DEBUG,
                            "Unknown StopReason value: ''{0}'', defaulting to STOP", value);
                    yield STOP;
                }
            };
        }

        public String toValue() {
            return name().toLowerCase();
        }
    }

    // ── UserMessage ────────────────────────────────────────────

    record UserMessage(
        List<Content> content,
        double timestamp
    ) implements Message {
        public UserMessage {
            content = content == null ? List.of() : List.copyOf(content);
        }
    }

    // ── AssistantMessage ───────────────────────────────────────

    /**
     * Assistant response message.
     *
     * <p>Unlike Python (Pydantic), Java records are immutable by default.
     * For streaming construction, use {@link Builder}.
     */
    record AssistantMessage(
        List<Content> content,
        Usage usage,
        StopReason stopReason,
        String errorMessage,
        boolean retryableError,
        boolean overflowError,
        String provider,
        String model,
        double timestamp
    ) implements Message {

        public AssistantMessage {
            content = content == null ? List.of() : List.copyOf(content);
            if (usage == null) usage = new Usage();
            if (stopReason == null) stopReason = StopReason.STOP;
        }

        /**
         * Returns only the tool-call content blocks.
         */
        public List<Content.ToolCallContent> toolCalls() {
            return content.stream()
                    .filter(c -> c instanceof Content.ToolCallContent)
                    .map(c -> (Content.ToolCallContent) c)
                    .toList();
        }

        /**
         * Returns true if this message contains any tool calls.
         */
        public boolean hasToolCalls() {
            return content.stream().anyMatch(c -> c instanceof Content.ToolCallContent);
        }

        /**
         * Extract the concatenated text from all TextContent blocks.
         */
        public String text() {
            StringBuilder sb = new StringBuilder();
            for (Content c : content) {
                if (c instanceof Content.TextContent tc) {
                    sb.append(tc.text());
                }
            }
            return sb.toString();
        }

        /**
         * Lightweight snapshot for streaming events.
         * Carries only provider, model, and current text — avoids allocating
         * a full {@link AssistantMessage} (content list, Usage, etc.) on every delta.
         */
        public record StreamingSnapshot(
                String provider, String model, String text
        ) {}

        public static Builder builder() {
            return new Builder();
        }

        /**
         * Mutable builder for streaming construction.
         */
        public static final class Builder {
            private static final List<Content> EMPTY_CONTENT = List.of();
            private static final Usage DEFAULT_USAGE = new Usage();

            private final List<Content> content = new ArrayList<>();
            private Usage usage = new Usage();
            private StopReason stopReason = StopReason.STOP;
            private String errorMessage;
            private boolean retryableError;
            private boolean overflowError;
            private String provider;
            private String model;
            private double timestamp = Message.nowEpochSeconds();

            public Builder addContent(Content c) { content.add(c); return this; }
            public Builder usage(Usage u) { this.usage = u; return this; }
            public Builder stopReason(StopReason s) { this.stopReason = s; return this; }
            public Builder errorMessage(String m) { this.errorMessage = m; return this; }
            public Builder retryableError(boolean b) { this.retryableError = b; return this; }
            public Builder overflowError(boolean b) { this.overflowError = b; return this; }
            public Builder provider(String p) { this.provider = p; return this; }
            public Builder model(String m) { this.model = m; return this; }
            public Builder timestamp(double t) { this.timestamp = t; return this; }

            public AssistantMessage build() {
                return new AssistantMessage(
                    List.copyOf(content),
                    usage, stopReason, errorMessage,
                    retryableError, overflowError,
                    provider, model, timestamp
                );
            }

            /**
             * Build a lightweight snapshot for streaming events.
             * Only includes text content so far, skipping tool calls and usage
             * to avoid expensive list copies on every delta.
             *
             * <p>Uses cached empty-content and default-usage singletons to
             * minimize per-call allocation during high-frequency streaming.
             */
            public AssistantMessage buildStreamingSnapshot(String textSoFar) {
                List<Content> snapshot = textSoFar.isEmpty()
                        ? EMPTY_CONTENT
                        : List.of(new Content.TextContent(textSoFar));
                return new AssistantMessage(
                        snapshot, DEFAULT_USAGE, StopReason.STOP, null,
                        false, false, provider, model, timestamp
                );
            }

            /**
             * Build an ultra-lightweight streaming snapshot.
             * Use this instead of {@link #buildStreamingSnapshot(String)} when
             * downstream consumers only need provider/model/text.
             */
            public StreamingSnapshot buildLightweightSnapshot(String textSoFar) {
                return new StreamingSnapshot(provider, model, textSoFar);
            }
        }
    }

    // ── ToolResultMessage ──────────────────────────────────────

    record ToolResultMessage(
        String toolCallId,
        String toolName,
        List<Content> content,
        boolean isError,
        double timestamp
    ) implements Message {
        public ToolResultMessage {
            if (toolCallId == null) throw new IllegalArgumentException("toolCallId must not be null");
            content = content == null ? List.of() : List.copyOf(content);
        }
    }

    // ── CustomMessage ──────────────────────────────────────────

    record CustomMessage(
        String customType,
        JsonNode content,
        JsonNode display,
        JsonNode details,
        double timestamp
    ) implements Message {
        public CustomMessage {
            if (customType == null) throw new IllegalArgumentException("customType must not be null");
        }

        /**
         * Convenience: compaction summary message.
         */
        public static CustomMessage compactionSummary(String summary) {
            return new CustomMessage("compaction_summary",
                    new TextNode(summary != null ? summary : ""), null, null,
                    Message.nowEpochSeconds());
        }
    }
}
