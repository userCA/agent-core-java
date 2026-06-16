package io.agentcore.core.events;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.agentcore.core.messages.AgentMessage;

import java.util.List;

@JsonTypeName("agent_end")
public record AgentEnd(List<AgentMessage> messages) implements AgentEvent {
    @Override
    public String type() { return "agent_end"; }
}
