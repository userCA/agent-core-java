package io.agentcore.core.messages;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Discriminated union of all message types — discriminated by {@code role}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "role")
@JsonSubTypes({
        @JsonSubTypes.Type(value = UserMessage.class, name = "user"),
        @JsonSubTypes.Type(value = AssistantMessage.class, name = "assistant"),
        @JsonSubTypes.Type(value = ToolResultMessage.class, name = "tool_result"),
        @JsonSubTypes.Type(value = CustomMessage.class, name = "custom"),
})
public sealed interface AgentMessage permits UserMessage, AssistantMessage, ToolResultMessage, CustomMessage {
    String role();
    double timestamp();
}
