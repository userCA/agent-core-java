package io.agentcore.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Internal helpers for inspecting LLM-format messages (provider-neutral dict shape).
 *
 * <p>Mirrors Python {@code agent_core/providers/llm_message_utils.py}.
 */
public final class LlmMessageUtils {

    private LlmMessageUtils() {}

    /**
     * Find the index of the latest user message.
     * Returns messages.size() if no user message found.
     */
    public static int latestUserIndex(List<Map<String, Object>> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).get("role"))) {
                return i;
            }
        }
        return messages.size();
    }

    /**
     * Get the text content of the latest user message.
     */
    @SuppressWarnings("unchecked")
    public static String latestUserText(List<Map<String, Object>> messages) {
        int idx = latestUserIndex(messages);
        if (idx >= messages.size()) return null;

        Object content = messages.get(idx).get("content");
        if (content instanceof String s) return s;
        if (content instanceof List<?> list) {
            List<String> parts = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    if ("text".equals(m.get("type")) && m.get("text") instanceof String t) {
                        parts.add(t);
                    }
                }
            }
            return parts.isEmpty() ? null : String.join("\n", parts);
        }
        return null;
    }

    /**
     * Insert a system message right before the latest user message.
     */
    public static List<Map<String, Object>> injectSystemMessageAtLatestUser(
            List<Map<String, Object>> messages, String text) {
        int insertAt = latestUserIndex(messages);
        Map<String, Object> systemMsg = Map.of("role", "system", "content", text);

        List<Map<String, Object>> result = new ArrayList<>(messages.size() + 1);
        result.addAll(messages.subList(0, insertAt));
        result.add(systemMsg);
        result.addAll(messages.subList(insertAt, messages.size()));
        return result;
    }
}
