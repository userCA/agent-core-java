package io.agentcore.core.content;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Content blocks within messages — discriminated by {@code type}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = TextContent.class, name = "text"),
        @JsonSubTypes.Type(value = ImageContent.class, name = "image"),
        @JsonSubTypes.Type(value = ToolCallContent.class, name = "tool_call"),
})
public sealed interface Content permits TextContent, ImageContent, ToolCallContent {
    String type();
}
