package io.agentcore.core.events;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.agentcore.core.messages.AgentMessage;

@JsonTypeName("message_start")
public record MessageStart(AgentMessage message) implements AgentEvent {
    @Override
    public String type() { return "message_start"; }
}
