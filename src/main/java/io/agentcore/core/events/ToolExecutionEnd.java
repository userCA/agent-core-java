package io.agentcore.core.events;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.agentcore.core.messages.ToolResultMessage;

@JsonTypeName("tool_execution_end")
public record ToolExecutionEnd(
        String toolCallId,
        String toolName,
        ToolResultMessage result,
        boolean isError
) implements AgentEvent {
    @Override
    public String type() { return "tool_execution_end"; }
}
