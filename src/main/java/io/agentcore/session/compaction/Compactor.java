package io.agentcore.session.compaction;

import io.agentcore.model.Content;
import io.agentcore.model.Message;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Context window compactor interface.
 *
 * <p>Mirrors Python {@code agent_core/compaction/compactor.py} Compactor Protocol.
 */
public interface Compactor {

    /**
     * Check if compaction is needed based on current message token usage.
     */
    boolean shouldCompact(List<Message> messages, int contextWindow);

    /**
     * Compact messages: summarize old messages, keep recent ones.
     *
     * @param messages     current message list
     * @param reason       why compaction was triggered ("threshold", "overflow", "manual")
     * @param instructions optional user instructions for summarization
     * @param signal       abort signal (nullable)
     * @return compaction result with summary and kept messages info
     */
    CompactionResult compact(
            List<Message> messages,
            String reason,
            String instructions,
            AtomicBoolean signal
    );

    // ── Token estimation utilities ───────────────────────

    /** Approximate characters per token for token estimation. */
    int CHARS_PER_TOKEN = 4;
    /** Fixed overhead added per message as baseline. */
    int MESSAGE_BASE_OVERHEAD = 20;
    /** Fixed token overhead for image content. */
    int IMAGE_TOKEN_OVERHEAD = 200;
    /** Fixed token overhead for tool call content. */
    int TOOL_CALL_TOKEN_OVERHEAD = 10;

    /**
     * Rough token estimation: ~4 chars per token + fixed overhead per message.
     */
    static int estimateTokens(Message message) {
        int charCount = 0;
        int fixedOverhead = 0;
        switch (message) {
            case Message.UserMessage um -> {
                for (var c : um.content()) {
                    if (c instanceof Content.TextContent tc) {
                        charCount += tc.text().length();
                    } else if (c instanceof Content.ImageContent) {
                        fixedOverhead += IMAGE_TOKEN_OVERHEAD;
                    }
                }
            }
            case Message.AssistantMessage am -> {
                for (var c : am.content()) {
                    if (c instanceof Content.TextContent tc) {
                        charCount += tc.text().length();
                    } else if (c instanceof Content.ToolCallContent tcc) {
                        charCount += tcc.name().length();
                        if (tcc.arguments() != null) {
                            charCount += tcc.arguments().toString().length();
                        }
                        fixedOverhead += TOOL_CALL_TOKEN_OVERHEAD;
                    }
                }
            }
            case Message.ToolResultMessage trm -> {
                for (var c : trm.content()) {
                    if (c instanceof Content.TextContent tc) {
                        charCount += tc.text().length();
                    } else if (c instanceof Content.ImageContent) {
                        fixedOverhead += IMAGE_TOKEN_OVERHEAD;
                    }
                }
            }
            case Message.CustomMessage cm -> {
                charCount += cm.content() != null ? cm.content().asText().length() : 0;
            }
        }
        return Math.max(1, charCount / CHARS_PER_TOKEN + MESSAGE_BASE_OVERHEAD + fixedOverhead);
    }

    /**
     * Sum estimated tokens for all messages.
     */
    static int totalTokens(List<Message> messages) {
        return messages.stream().mapToInt(Compactor::estimateTokens).sum();
    }
}
