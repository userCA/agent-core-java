package io.agentcore.core.events;

import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("agent_start")
public record AgentStart() implements AgentEvent {
    @Override
    public String type() { return "agent_start"; }
}
