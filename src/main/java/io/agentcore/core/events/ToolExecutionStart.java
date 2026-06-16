package io.agentcore.core.events;

import com.fasterxml.jackson.annotation.JsonTypeName;

import java.util.Map;

@JsonTypeName("tool_execution_start")
public record ToolExecutionStart(
        String toolCallId,
        String toolName,
        Map<String, Object> args
) implements AgentEvent {
    @Override
    public String type() { return "tool_execution_start"; }
}
