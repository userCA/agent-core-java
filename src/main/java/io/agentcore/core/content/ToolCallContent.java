package io.agentcore.core.content;

import com.fasterxml.jackson.annotation.JsonTypeName;

import java.util.Map;

@JsonTypeName("tool_call")
public record ToolCallContent(
        String id,
        String name,
        Map<String, Object> arguments
) implements Content {
    @Override
    public String type() {
        return "tool_call";
    }

    public ToolCallContent {
        if (arguments == null) arguments = Map.of();
    }
}
