package io.agentcore.core.events;

import com.fasterxml.jackson.annotation.JsonTypeName;

import java.util.Map;

@JsonTypeName("human_input_required")
public record HumanInputRequired(
        String toolCallId,
        String prompt,
        Map<String, Object> inputSchema
) implements AgentEvent {
    @Override
    public String type() { return "human_input_required"; }
}
