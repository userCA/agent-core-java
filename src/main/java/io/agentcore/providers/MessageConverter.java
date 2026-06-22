package io.agentcore.providers;

import io.agentcore.core.Content;
import io.agentcore.core.Message;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Default OpenAI-format message converter.
 *
 * <p>Mirrors Python {@code agent_core/providers/message_converter.py}.
 * Converts domain {@link Message} objects to OpenAI-compatible {@code Map<String, Object>} dicts.
 */
public final class MessageConverter implements Function<List<Message>, List<Map<String, Object>>> {

    private final int toolResultMaxChars;

    public MessageConverter(int toolResultMaxChars) {
        this.toolResultMaxChars = toolResultMaxChars;
    }

    public MessageConverter() {
        this(4000);
    }

    @Override
    public List<Map<String, Object>> apply(List<Message> messages) {
        return convert(messages);
    }

    /**
     * Convert domain messages to OpenAI-compatible dicts.
     */
    public List<Map<String, Object>> convert(List<Message> messages) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Message m : messages) {
            switch (m) {
                case Message.UserMessage um -> {
                    Object content = userContentToOpenAi(um.content());
                    out.add(Map.of("role", "user", "content", content));
                }
                case Message.AssistantMessage am -> {
                    Map<String, Object> msg = new LinkedHashMap<>();
                    msg.put("role", "assistant");
                    String text = am.content().stream()
                            .filter(c -> c instanceof Content.TextContent)
                            .map(c -> ((Content.TextContent) c).text())
                            .collect(Collectors.joining());
                    msg.put("content", text);

                    List<Content.ToolCallContent> toolCalls = am.toolCalls();
                    if (!toolCalls.isEmpty()) {
                        List<Map<String, Object>> tcList = toolCalls.stream()
                                .map(tc -> {
                                    Map<String, Object> fn = new LinkedHashMap<>();
                                    fn.put("name", tc.name());
                                    fn.put("arguments", toJsonString(tc.arguments()));
                                    Map<String, Object> tcMap = new LinkedHashMap<>();
                                    tcMap.put("id", tc.id());
                                    tcMap.put("type", "function");
                                    tcMap.put("function", fn);
                                    return tcMap;
                                })
                                .toList();
                        msg.put("tool_calls", tcList);
                    }
                    out.add(msg);
                }
                case Message.ToolResultMessage trm -> {
                    String text = trm.content().stream()
                            .filter(c -> c instanceof Content.TextContent)
                            .map(c -> ((Content.TextContent) c).text())
                            .collect(Collectors.joining());
                    if (text.length() > toolResultMaxChars) {
                        text = text.substring(0, toolResultMaxChars)
                                + "\n...[truncated, " + (text.length()) + " chars total]";
                    }
                    Map<String, Object> msg = new LinkedHashMap<>();
                    msg.put("role", "tool");
                    msg.put("tool_call_id", trm.toolCallId());
                    msg.put("content", text);
                    out.add(msg);
                }
                case Message.CustomMessage cm -> {
                    if ("compaction_summary".equals(cm.customType())) {
                        String text = cm.content() != null ? cm.content().asText() : "";
                        Map<String, Object> msg = new LinkedHashMap<>();
                        msg.put("role", "system");
                        msg.put("content", "[Earlier conversation summary]\n" + text);
                        out.add(msg);
                    }
                }
            }
        }
        return out;
    }

    /**
     * Convert user content list to OpenAI format.
     * If only text, returns a plain string; otherwise returns a list of content parts.
     */
    private Object userContentToOpenAi(List<Content> content) {
        List<Map<String, Object>> parts = new ArrayList<>();
        boolean onlyText = true;

        for (Content c : content) {
            if (c instanceof Content.TextContent tc) {
                parts.add(Map.of("type", "text", "text", tc.text()));
            } else if (c instanceof Content.ImageContent ic) {
                parts.add(Map.of(
                        "type", "image_url",
                        "image_url", Map.of("url", "data:" + ic.mimeType() + ";base64," + ic.data())
                ));
                onlyText = false;
            }
        }

        if (onlyText) {
            return parts.stream()
                    .map(p -> (String) p.get("text"))
                    .collect(Collectors.joining());
        }
        return parts;
    }

    /**
     * Simple JSON serialization for tool arguments.
     * Uses Jackson ObjectMapper for proper serialization.
     */
    private static String toJsonString(Map<String, Object> map) {
        try {
            return OBJECT_MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();
}
