package io.agentcore.compaction;

import io.agentcore.core.Message;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

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

    // ── Token estimation utilities ───────────────────────────

    /**
     * Rough token estimation: ~4 chars per token + fixed overhead per message.
     */
    static int estimateTokens(Message message) {
        StringBuilder textParts = new StringBuilder();
        int fixedOverhead = 0;
        switch (message) {
            case Message.UserMessage um -> {
                for (var c : um.content()) {
                    if (c instanceof io.agentcore.core.Content.TextContent tc) {
                        textParts.append(tc.text());
                    } else if (c instanceof io.agentcore.core.Content.ImageContent) {
                        fixedOverhead += 200; // fixed estimate for images
                    }
                }
            }
            case Message.AssistantMessage am -> {
                for (var c : am.content()) {
                    if (c instanceof io.agentcore.core.Content.TextContent tc) {
                        textParts.append(tc.text());
                    } else if (c instanceof io.agentcore.core.Content.ToolCallContent tcc) {
                        // Estimate tokens for tool call: name + serialized arguments
                        textParts.append(tcc.name());
                        if (tcc.arguments() != null) {
                            textParts.append(tcc.arguments().toString());
                        }
                        fixedOverhead += 10; // tool call overhead
                    }
                }
            }
            case Message.ToolResultMessage trm -> {
                for (var c : trm.content()) {
                    if (c instanceof io.agentcore.core.Content.TextContent tc) {
                        textParts.append(tc.text());
                    } else if (c instanceof io.agentcore.core.Content.ImageContent) {
                        fixedOverhead += 200;
                    }
                }
            }
            case Message.CustomMessage cm -> {
                textParts.append(cm.content() != null ? cm.content().asText() : "");
            }
        }
        return Math.max(1, textParts.length() / 4 + 20 + fixedOverhead);
    }

    /**
     * Sum estimated tokens for all messages.
     */
    static int totalTokens(List<Message> messages) {
        return messages.stream().mapToInt(Compactor::estimateTokens).sum();
    }
}
