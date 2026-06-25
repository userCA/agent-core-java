package io.agentcore.llm.anthropic;

import io.agentcore.model.Content;
import io.agentcore.model.Message;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Converts domain {@link Message} objects to Anthropic-native message format.
 *
 * <p>Unlike the default {@link io.agentcore.llm.MessageConverter} (which produces OpenAI-compatible dicts),
 * this converter produces Anthropic's native content-block format where:
 * <ul>
 *   <li>User messages use content blocks (text, image)</li>
 *   <li>Assistant messages use text + tool_use blocks</li>
 *   <li>Tool results are tool_result blocks attached to user messages</li>
 * </ul>
 */
public class AnthropicMessageConverter implements Function<List<Message>, List<Map<String, Object>>> {

    private final int toolResultMaxChars;

    public AnthropicMessageConverter(int toolResultMaxChars) {
        this.toolResultMaxChars = toolResultMaxChars;
    }

    @Override
    public List<Map<String, Object>> apply(List<Message> messages) {
        return convert(messages);
    }

    /**
     * Convert domain messages to Anthropic-native format.
     */
    public List<Map<String, Object>> convert(List<Message> messages) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Message m : messages) {
            switch (m) {
                case Message.UserMessage um -> {
                    List<Map<String, Object>> blocks = new ArrayList<>();
                    for (Content c : um.content()) {
                        if (c instanceof Content.TextContent tc) {
                            blocks.add(new LinkedHashMap<>(Map.of("type", "text", "text", tc.text())));
                        } else if (c instanceof Content.ImageContent ic) {
                            Map<String, Object> img = new LinkedHashMap<>();
                            img.put("type", "image");
                            img.put("source", Map.of(
                                    "type", "base64",
                                    "media_type", ic.mimeType(),
                                    "data", ic.data()
                            ));
                            blocks.add(img);
                        }
                    }
                    if (blocks.isEmpty()) {
                        blocks.add(Map.of("type", "text", "text", ""));
                    }
                    Map<String, Object> msg = new LinkedHashMap<>();
                    msg.put("role", "user");
                    msg.put("content", blocks);
                    out.add(msg);
                }
                case Message.AssistantMessage am -> {
                    List<Map<String, Object>> blocks = new ArrayList<>();
                    String text = Content.joinAllTextRaw(am.content());
                    if (!text.isEmpty()) {
                        blocks.add(new LinkedHashMap<>(Map.of("type", "text", "text", text)));
                    }
                    for (Content tc : am.content()) {
                        if (tc instanceof Content.ToolCallContent tcc) {
                            Map<String, Object> tu = new LinkedHashMap<>();
                            tu.put("type", "tool_use");
                            tu.put("id", tcc.id());
                            tu.put("name", tcc.name());
                            tu.put("input", tcc.arguments());
                            blocks.add(tu);
                        }
                    }
                    if (blocks.isEmpty()) {
                        blocks.add(Map.of("type", "text", "text", ""));
                    }
                    Map<String, Object> msg = new LinkedHashMap<>();
                    msg.put("role", "assistant");
                    msg.put("content", blocks);
                    out.add(msg);
                }
                case Message.ToolResultMessage trm -> {
                    String text = Content.joinAllTextRaw(trm.content());
                    if (text.length() > toolResultMaxChars) {
                        text = text.substring(0, toolResultMaxChars)
                                + "\n...[truncated, " + text.length() + " chars total]";
                    }
                    Map<String, Object> resultBlock = new LinkedHashMap<>();
                    resultBlock.put("type", "tool_result");
                    resultBlock.put("tool_use_id", trm.toolCallId());
                    resultBlock.put("content", text);

                    if (!out.isEmpty() && "user".equals(out.getLast().get("role"))) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> content = (List<Map<String, Object>>) out.getLast().get("content");
                        content.add(resultBlock);
                    } else {
                        Map<String, Object> msg = new LinkedHashMap<>();
                        msg.put("role", "user");
                        msg.put("content", new ArrayList<>(List.of(resultBlock)));
                        out.add(msg);
                    }
                }
                case Message.CustomMessage cm -> {
                    if ("compaction_summary".equals(cm.customType())) {
                        String text = cm.content() != null ? cm.content().asText() : "";
                        Map<String, Object> msg = new LinkedHashMap<>();
                        msg.put("role", "user");
                        msg.put("content", new ArrayList<>(List.of(
                                Map.of("type", "text", "text", "[Earlier conversation summary]\n" + text)
                        )));
                        out.add(msg);
                    }
                }
            }
        }
        return out;
    }
}
