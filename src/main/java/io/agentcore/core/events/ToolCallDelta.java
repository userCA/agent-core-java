package io.agentcore.core.events;

import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("tool_call_delta")
public record ToolCallDelta(String id, String name, String argumentsDelta) implements MessageDelta {
    @Override
    public String type() { return "tool_call_delta"; }
}
