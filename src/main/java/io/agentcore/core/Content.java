package io.agentcore.core;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Map;

/**
 * Content block models used across messages and tool results.
 *
 * <p>Sealed interface with three variants:
 * <ul>
 *   <li>{@link TextContent} — plain text</li>
 *   <li>{@link ImageContent} — base64-encoded image</li>
 *   <li>{@link ToolCallContent} — LLM tool-call request</li>
 * </ul>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Content.TextContent.class, name = "text"),
    @JsonSubTypes.Type(value = Content.ImageContent.class, name = "image"),
    @JsonSubTypes.Type(value = Content.ToolCallContent.class, name = "tool_call"),
})
public sealed interface Content {

    /**
     * Plain text content block.
     */
    record TextContent(String text) implements Content {
        public TextContent {
            if (text == null) text = "";
        }
    }

    /**
     * Base64-encoded image content block.
     */
    record ImageContent(String data, String mimeType) implements Content {
        public ImageContent {
            if (data == null) throw new IllegalArgumentException("data must not be null");
            if (mimeType == null) mimeType = "image/png";
        }
    }

    /**
     * Tool call request content block emitted by the LLM.
     */
    record ToolCallContent(String id, String name, Map<String, Object> arguments) implements Content {
        public ToolCallContent {
            if (id == null) throw new IllegalArgumentException("id must not be null");
            if (name == null) throw new IllegalArgumentException("name must not be null");
            if (arguments == null) arguments = Map.of();
            else arguments = Map.copyOf(arguments);
        }
    }
}
