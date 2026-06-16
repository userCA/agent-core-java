package io.agentcore.core.events;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Delta types for streaming message updates.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = TextDelta.class, name = "text_delta"),
        @JsonSubTypes.Type(value = ThinkingDelta.class, name = "thinking_delta"),
        @JsonSubTypes.Type(value = ToolCallDelta.class, name = "tool_call_delta"),
})
public sealed interface MessageDelta permits TextDelta, ThinkingDelta, ToolCallDelta {
    String type();
}
