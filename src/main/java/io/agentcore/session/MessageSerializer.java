package io.agentcore.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;

import io.agentcore.model.Content;
import io.agentcore.model.Message;
import io.agentcore.model.Message.*;
import io.agentcore.llm.ProviderUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Message serialization/deserialization for session persistence.
 *
 * <p>Extracted from AgentSession (SRP) — converts between domain
 * {@link Message} objects and session-store {@code Map} representations.
 */
public final class MessageSerializer {

    private static final Logger log = LoggerFactory.getLogger(MessageSerializer.class);
    private static final ObjectMapper OBJECT_MAPPER = ProviderUtils.mapper();

    private MessageSerializer() {}

    // ── Serialization ──────────────────────────────────────────

    /**
     * Serialize a Message to a Map suitable for session storage.
     */
    public static Map<String, Object> serialize(Message msg) {
        Map<String, Object> map = new LinkedHashMap<>();
        switch (msg) {
            case UserMessage um -> {
                map.put("role", "user");
                map.put("content", serializeContent(um.content()));
                map.put("timestamp", um.timestamp());
            }
            case AssistantMessage am -> {
                map.put("role", "assistant");
                map.put("content", serializeContent(am.content()));
                map.put("timestamp", am.timestamp());
                map.put("stop_reason", am.stopReason().toValue());
                map.put("provider", am.provider());
                map.put("model", am.model());
                if (am.errorMessage() != null) map.put("error_message", am.errorMessage());
                if (am.isRetryableError()) map.put("retryable_error", true);
                if (am.isOverflowError()) map.put("overflow_error", true);
                Usage u = am.usage();
                if (u.totalTokensWithCache() > 0) {
                    map.put("usage", Map.of(
                            "input_tokens", u.inputTokens(),
                            "output_tokens", u.outputTokens(),
                            "cache_read_tokens", u.cacheReadTokens(),
                            "cache_write_tokens", u.cacheWriteTokens()));
                }
            }
            case ToolResultMessage trm -> {
                map.put("role", "tool_result");
                map.put("tool_call_id", trm.toolCallId());
                map.put("content", serializeContent(trm.content()));
                map.put("is_error", trm.isError());
                map.put("timestamp", trm.timestamp());
            }
            case CustomMessage cm -> {
                map.put("role", "custom");
                map.put("custom_type", cm.customType());
                map.put("content", cm.content() != null ? cm.content().toString() : null);
                map.put("timestamp", cm.timestamp());
            }
        }
        return map;
    }

    /**
     * Serialize a list of Content blocks to a list of Maps.
     */
    public static List<Map<String, Object>> serializeContent(List<Content> contents) {
        if (contents == null) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (var c : contents) {
            Map<String, Object> item = new LinkedHashMap<>();
            if (c instanceof Content.TextContent tc) {
                item.put("type", "text");
                item.put("text", tc.text());
            } else if (c instanceof Content.ImageContent ic) {
                item.put("type", "image");
                item.put("data", ic.data());
                item.put("mimeType", ic.mimeType());
            } else if (c instanceof Content.ToolCallContent tcc) {
                item.put("type", "tool_call");
                item.put("id", tcc.id());
                item.put("name", tcc.name());
                item.put("arguments", tcc.arguments());
            }
            result.add(item);
        }
        return result;
    }

    // ── Deserialization ────────────────────────────────────────

    /**
     * Deserialize a Message from a session-store Map.
     *
     * @return the deserialized Message, or null if the role is unknown
     */
    @SuppressWarnings("unchecked")
    public static Message deserialize(Map<?, ?> msgMap) {
        String role = (String) msgMap.get("role");
        double timestamp = msgMap.get("timestamp") instanceof Number n ? n.doubleValue() : 0;

        return switch (role) {
            case "user" -> {
                List<Content> content = deserializeContent(msgMap.get("content"));
                yield new UserMessage(content, timestamp);
            }
            case "assistant" -> {
                List<Content> content = deserializeContent(msgMap.get("content"));
                StopReason stopReason = StopReason.fromValue((String) msgMap.get("stop_reason"));
                String provider = msgMap.get("provider") instanceof String p ? p : "";
                String modelVal = msgMap.get("model") instanceof String m ? m : "";
                String errorMsg = msgMap.get("error_message") instanceof String em ? em : null;
                boolean retryable = Boolean.TRUE.equals(msgMap.get("retryable_error"));
                boolean overflow = Boolean.TRUE.equals(msgMap.get("overflow_error"));
                Usage usage = new Usage();
                if (msgMap.get("usage") instanceof Map<?, ?> uMap) {
                    usage = new Usage(
                            toInt(uMap.get("input_tokens")),
                            toInt(uMap.get("output_tokens")),
                            toInt(uMap.get("cache_read_tokens")),
                            toInt(uMap.get("cache_write_tokens")));
                }
                yield new AssistantMessage(content, usage, stopReason, errorMsg,
                        retryable, overflow, provider, modelVal, timestamp);
            }
            case "tool_result" -> {
                List<Content> content = deserializeContent(msgMap.get("content"));
                String toolCallId = (String) msgMap.get("tool_call_id");
                String toolName = msgMap.get("tool_name") instanceof String tn ? tn : "";
                boolean isError = Boolean.TRUE.equals(msgMap.get("is_error"));
                yield new ToolResultMessage(toolCallId, toolName, content, isError, timestamp);
            }
            case "custom" -> {
                String customType = (String) msgMap.get("custom_type");
                JsonNode content = null;
                Object rawContent = msgMap.get("content");
                if (rawContent instanceof String s) {
                    content = new TextNode(s);
                } else if (rawContent != null) {
                    content = OBJECT_MAPPER.valueToTree(rawContent);
                }
                yield new CustomMessage(customType, content, null, null, timestamp);
            }
            default -> {
                log.debug("Unknown message role '{}', wrapping as CustomMessage", role);
                yield new CustomMessage("unknown:" + role, null, null, null, timestamp);
            }
        };
    }

    /**
     * Deserialize content blocks from a raw object (typically a List of Maps).
     */
    public static List<Content> deserializeContent(Object contentObj) {
        if (contentObj instanceof List<?> list) {
            List<Content> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    String type = (String) m.get("type");
                    if ("text".equals(type)) {
                        result.add(new Content.TextContent((String) m.get("text")));
                    } else if ("image".equals(type)) {
                        String data = (String) m.get("data");
                        String mimeType = m.get("mimeType") instanceof String mt ? mt : "image/png";
                        if (data != null) result.add(new Content.ImageContent(data, mimeType));
                    } else if ("tool_call".equals(type)) {
                        String id = (String) m.get("id");
                        String name = (String) m.get("name");
                        @SuppressWarnings("unchecked")
                        Map<String, Object> args = m.get("arguments") instanceof Map<?, ?> a
                                ? (Map<String, Object>) a : Map.of();
                        if (id != null && name != null) {
                            result.add(new Content.ToolCallContent(id, name, args));
                        }
                    }
                }
            }
            return result;
        }
        return List.of();
    }

    private static int toInt(Object obj) {
        return obj instanceof Number n ? n.intValue() : 0;
    }
}
