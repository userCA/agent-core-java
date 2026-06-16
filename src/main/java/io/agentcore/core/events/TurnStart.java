package io.agentcore.core.events;

import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("turn_start")
public record TurnStart() implements AgentEvent {
    @Override
    public String type() { return "turn_start"; }
}
