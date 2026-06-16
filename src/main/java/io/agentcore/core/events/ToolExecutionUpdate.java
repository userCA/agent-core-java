package io.agentcore.core.events;

import com.fasterxml.jackson.annotation.JsonTypeName;

import java.util.Map;

@JsonTypeName("tool_execution_update")
public record ToolExecutionUpdate(
        String toolCallId,
        String toolName,
        Map<String, Object> args,
        Object partialResult
) implements AgentEvent {
    @Override
    public String type() { return "tool_execution_update"; }
}
