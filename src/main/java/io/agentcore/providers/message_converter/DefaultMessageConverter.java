package io.agentcore.providers.message_converter;

import io.agentcore.core.content.*;
import io.agentcore.core.messages.*;
import io.agentcore.tools.truncate.Truncate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Converts internal AgentMessages to OpenAI-compatible chat format.
 */
public class DefaultMessageConverter {
    private static final Logger log = LoggerFactory.getLogger(DefaultMessageConverter.class);
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final int toolResultMaxChars;

    public DefaultMessageConverter(int toolResultMaxChars) {
        this.toolResultMaxChars = toolResultMaxChars;
    }

    public CompletableFuture<List<Map<String, Object>>> convert(List<AgentMessage> messages) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (var msg : messages) {
            if (msg instanceof UserMessage um) {
                result.add(convertUser(um));
            } else if (msg instanceof AssistantMessage am) {
                result.add(convertAssistant(am));
            } else if (msg instanceof ToolResultMessage trm) {
                result.add(convertToolResult(trm));
            } else if (msg instanceof CustomMessage cm) {
                result.add(convertCustom(cm));
            } else {
                log.warn("Unknown message type {} skipped during conversion", msg.getClass().getSimpleName());
            }
        }
        return CompletableFuture.completedFuture(result);
    }

    private Map<String, Object> convertUser(UserMessage msg) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("role", "user");
        boolean hasImages = msg.content().stream().anyMatch(c -> c instanceof ImageContent);
        if (hasImages) {
            result.put("content", userContentToOpenAI(msg.content()));
        } else {
            StringBuilder sb = new StringBuilder();
            for (var c : msg.content()) {
                if (c instanceof TextContent tc) sb.append(tc.text());
            }
            result.put("content", sb.toString());
        }
        return result;
    }

    private List<Map<String, Object>> userContentToOpenAI(List<Content> content) {
        List<Map<String, Object>> parts = new ArrayList<>();
        for (var c : content) {
            if (c instanceof TextContent tc) {
                parts.add(Map.of("type", "text", "text", tc.text()));
            } else if (c instanceof ImageContent ic) {
                parts.add(Map.of(
                        "type", "image_url",
                        "image_url", Map.of("url", "data:" + ic.mimeType() + ";base64," + ic.data())
                ));
            }
        }
        return parts;
    }

    private Map<String, Object> convertAssistant(AssistantMessage msg) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("role", "assistant");

        StringBuilder textBuf = new StringBuilder();
        List<Map<String, Object>> toolCalls = new ArrayList<>();

        for (var c : msg.content()) {
            if (c instanceof TextContent tc) {
                textBuf.append(tc.text());
            } else if (c instanceof ToolCallContent tcc) {
                Map<String, Object> tc = new LinkedHashMap<>();
                tc.put("id", tcc.id());
                tc.put("type", "function");
                tc.put("function", Map.of(
                        "name", tcc.name(),
                        "arguments", serializeJson(tcc.arguments())
                ));
                toolCalls.add(tc);
            }
        }

        result.put("content", textBuf.toString());
        if (!toolCalls.isEmpty()) {
            result.put("tool_calls", toolCalls);
        }
        return result;
    }

    private Map<String, Object> convertToolResult(ToolResultMessage msg) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("role", "tool");
        result.put("tool_call_id", msg.toolCallId());

        StringBuilder sb = new StringBuilder();
        for (var c : msg.content()) {
            if (c instanceof TextContent tc) sb.append(tc.text());
        }
        result.put("content", Truncate.truncateTail(sb.toString(), toolResultMaxChars));
        return result;
    }

    private Map<String, Object> convertCustom(CustomMessage msg) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("role", "system");
        result.put("content", "[Earlier conversation summary]\n" + (msg.contentAsText() != null ? msg.contentAsText() : ""));
        return result;
    }

    /**
     * JSON serialization for tool call arguments using Jackson ObjectMapper.
     */
    private static String serializeJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
