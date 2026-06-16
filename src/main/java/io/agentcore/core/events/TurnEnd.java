package io.agentcore.core.events;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.agentcore.core.messages.AgentMessage;
import io.agentcore.core.messages.ToolResultMessage;

import java.util.List;

@JsonTypeName("turn_end")
public record TurnEnd(
        AgentMessage message,
        List<ToolResultMessage> toolResults
) implements AgentEvent {
    @Override
    public String type() { return "turn_end"; }
}
