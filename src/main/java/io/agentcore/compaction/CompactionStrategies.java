package io.agentcore.compaction;

import io.agentcore.core.messages.AgentMessage;

import java.util.Map;

public final class CompactionStrategies {
    private CompactionStrategies() {}

    private static final int IMAGE_TOKEN_ESTIMATE = 200;
    private static final int TOOL_CALL_OVERHEAD = 30;

    private static void estimateContentTokens(io.agentcore.core.content.Content c, StringBuilder sb, int[] extra) {
        if (c instanceof io.agentcore.core.content.TextContent tc) {
            sb.append(tc.text());
        } else if (c instanceof io.agentcore.core.content.ToolCallContent tcc) {
            // Tool calls have overhead tokens for name + schema framing
            sb.append(tcc.name());
            Map<String, Object> args = tcc.arguments();
            if (args != null) {
                sb.append(args.toString());
            }
            extra[0] += TOOL_CALL_OVERHEAD;
        } else if (c instanceof io.agentcore.core.content.ImageContent) {
            // Images consume a fixed number of tokens regardless of text extraction
            extra[0] += IMAGE_TOKEN_ESTIMATE;
        }
    }

    public static int estimateTokens(AgentMessage message) {
        int[] extra = new int[1];
        String text = extractText(message, extra);
        return Math.max(1, text.length() / 4 + 20 + extra[0]);
    }

    public static int totalTokens(java.util.List<AgentMessage> messages) {
        return messages.stream().mapToInt(CompactionStrategies::estimateTokens).sum();
    }

    public static boolean shouldCompactThreshold(java.util.List<AgentMessage> messages,
                                                  int contextWindow, double threshold) {
        return totalTokens(messages) >= contextWindow * threshold;
    }

    private static String extractText(AgentMessage msg, int[] extra) {
        if (msg == null) return "";
        var sb = new StringBuilder();
        if (msg instanceof io.agentcore.core.messages.UserMessage um) {
            um.content().forEach(c -> estimateContentTokens(c, sb, extra));
        } else if (msg instanceof io.agentcore.core.messages.AssistantMessage am) {
            am.content().forEach(c -> estimateContentTokens(c, sb, extra));
        } else if (msg instanceof io.agentcore.core.messages.ToolResultMessage trm) {
            trm.content().forEach(c -> estimateContentTokens(c, sb, extra));
        } else if (msg instanceof io.agentcore.core.messages.CustomMessage cm) {
            String text = cm.contentAsText();
            if (text != null) {
                sb.append(text);
            }
        }
        return sb.toString();
    }
}
