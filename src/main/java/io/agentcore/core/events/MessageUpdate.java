package io.agentcore.core.events;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.agentcore.core.messages.AgentMessage;

@JsonTypeName("message_update")
public record MessageUpdate(
        AgentMessage message,
        MessageDelta delta
) implements AgentEvent {
    @Override
    public String type() { return "message_update"; }
}
