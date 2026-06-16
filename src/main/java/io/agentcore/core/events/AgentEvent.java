package io.agentcore.core.events;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * AgentEvent discriminated union emitted by the agent loop.
 * 11 event types covering the full agent lifecycle.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = AgentStart.class, name = "agent_start"),
        @JsonSubTypes.Type(value = AgentEnd.class, name = "agent_end"),
        @JsonSubTypes.Type(value = TurnStart.class, name = "turn_start"),
        @JsonSubTypes.Type(value = TurnEnd.class, name = "turn_end"),
        @JsonSubTypes.Type(value = MessageStart.class, name = "message_start"),
        @JsonSubTypes.Type(value = MessageUpdate.class, name = "message_update"),
        @JsonSubTypes.Type(value = MessageEnd.class, name = "message_end"),
        @JsonSubTypes.Type(value = ToolExecutionStart.class, name = "tool_execution_start"),
        @JsonSubTypes.Type(value = ToolExecutionUpdate.class, name = "tool_execution_update"),
        @JsonSubTypes.Type(value = ToolExecutionEnd.class, name = "tool_execution_end"),
        @JsonSubTypes.Type(value = HumanInputRequired.class, name = "human_input_required"),
})
public sealed interface AgentEvent permits
        AgentStart, AgentEnd, TurnStart, TurnEnd,
        MessageStart, MessageUpdate, MessageEnd,
        ToolExecutionStart, ToolExecutionUpdate, ToolExecutionEnd,
        HumanInputRequired {
    String type();
}
