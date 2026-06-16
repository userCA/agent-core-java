package io.agentcore.core.events;

import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("thinking_delta")
public record ThinkingDelta(String text) implements MessageDelta {
    @Override
    public String type() { return "thinking_delta"; }
}
