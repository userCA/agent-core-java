package io.agentcore.core.events;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.agentcore.core.messages.AgentMessage;

@JsonTypeName("message_end")
public record MessageEnd(AgentMessage message) implements AgentEvent {
    @Override
    public String type() { return "message_end"; }
}
