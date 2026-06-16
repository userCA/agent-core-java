package io.agentcore.core.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.agentcore.core.content.Content;

import java.util.List;

@JsonTypeName("tool_result")
public record ToolResultMessage(
        @JsonProperty("tool_call_id") String toolCallId,
        @JsonProperty("tool_name") String toolName,
        List<Content> content,
        @JsonProperty("is_error") boolean isError,
        double timestamp
) implements AgentMessage {

    public ToolResultMessage {
        if (content == null) content = List.of();
    }

    public ToolResultMessage(String toolCallId, String toolName, List<Content> content, boolean isError) {
        this(toolCallId, toolName, content, isError, System.currentTimeMillis() / 1000.0);
    }

    @Override
    public String role() {
        return "tool_result";
    }
}
