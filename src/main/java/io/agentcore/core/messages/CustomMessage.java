package io.agentcore.core.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;

@JsonTypeName("custom")
public record CustomMessage(
        @JsonProperty("custom_type") String customType,
        JsonNode content,
        Object display,
        Object details,
        double timestamp
) implements AgentMessage {

    public CustomMessage {
        // allow nulls
    }

    /**
     * Convenience constructor with String content.
     */
    public CustomMessage(String customType, String content) {
        this(customType, new TextNode(content), null, null, System.currentTimeMillis() / 1000.0);
    }

    /**
     * Convenience constructor with JsonNode content.
     */
    public CustomMessage(String customType, JsonNode content) {
        this(customType, content, null, null, System.currentTimeMillis() / 1000.0);
    }

    /**
     * Get content as text. Returns the text value for TextNode, or toString for complex nodes.
     */
    public String contentAsText() {
        if (content == null) return null;
        if (content.isTextual()) return content.asText();
        return content.toString();
    }

    @Override
    public String role() {
        return "custom";
    }
}
