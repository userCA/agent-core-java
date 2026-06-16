package io.agentcore.core.events;

import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("text_delta")
public record TextDelta(String text) implements MessageDelta {
    @Override
    public String type() { return "text_delta"; }
}
