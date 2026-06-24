package io.agentcore.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;
import java.util.Map;

/**
 * AgentEvent discriminated union emitted by the agent loop.
 *
 * <p>Mirrors Python {@code agent_core/core/events.py}.
 * All event types are records implementing this sealed interface.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = AgentEvent.AgentStart.class, name = "agent_start"),
    @JsonSubTypes.Type(value = AgentEvent.AgentEnd.class, name = "agent_end"),
    @JsonSubTypes.Type(value = AgentEvent.TurnStart.class, name = "turn_start"),
    @JsonSubTypes.Type(value = AgentEvent.TurnEnd.class, name = "turn_end"),
    @JsonSubTypes.Type(value = AgentEvent.MessageStart.class, name = "message_start"),
    @JsonSubTypes.Type(value = AgentEvent.MessageUpdate.class, name = "message_update"),
    @JsonSubTypes.Type(value = AgentEvent.MessageEnd.class, name = "message_end"),
    @JsonSubTypes.Type(value = AgentEvent.ToolExecutionStart.class, name = "tool_execution_start"),
    @JsonSubTypes.Type(value = AgentEvent.ToolExecutionEnd.class, name = "tool_execution_end"),
    @JsonSubTypes.Type(value = AgentEvent.HumanInputRequired.class, name = "human_input_required"),
})
public sealed interface AgentEvent {

    /**
     * Event timestamp (epoch seconds).
     * Default uses current time; records may override with a captured value.
     */
    default double timestamp() {
        return Message.nowEpochSeconds();
    }

    // ── Lifecycle events ───────────────────────────────────────

    record AgentStart() implements AgentEvent {}

    record AgentEnd(List<Message> messages) implements AgentEvent {
        public AgentEnd { messages = messages == null ? List.of() : List.copyOf(messages); }
    }

    record TurnStart() implements AgentEvent {}

    record TurnEnd(Message message, List<Message> toolResults) implements AgentEvent {
        public TurnEnd { toolResults = toolResults == null ? List.of() : List.copyOf(toolResults); }
    }

    // ── Message streaming events ───────────────────────────────

    record MessageStart(Message message) implements AgentEvent {}

    record MessageUpdate(Message message, MessageDelta delta) implements AgentEvent {}

    record MessageEnd(Message message) implements AgentEvent {}

    // ── Tool execution events ──────────────────────────────────

    record ToolExecutionStart(
        String toolCallId, String toolName, Map<String, Object> args
    ) implements AgentEvent {
        public ToolExecutionStart { args = args == null ? Map.of() : Map.copyOf(args); }
    }

    record ToolExecutionEnd(
        String toolCallId, String toolName, ToolResult result, boolean isError
    ) implements AgentEvent {}

    // ── Human-in-the-loop ──────────────────────────────────────

    record HumanInputRequired(
        String toolCallId, String prompt, Map<String, Object> inputSchema
    ) implements AgentEvent {}

    // ── MessageDelta ───────────────────────────────────────────

    /**
     * Discriminated union for streaming deltas within a MessageUpdate.
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = MessageDelta.TextDelta.class, name = "text_delta"),
        @JsonSubTypes.Type(value = MessageDelta.ThinkingDelta.class, name = "thinking_delta"),
        @JsonSubTypes.Type(value = MessageDelta.ToolCallDelta.class, name = "tool_call_delta"),
    })
    sealed interface MessageDelta {
        record TextDelta(String text) implements MessageDelta {}
        record ThinkingDelta(String text) implements MessageDelta {}
        record ToolCallDelta(String id, String name, String argumentsDelta) implements MessageDelta {}
    }
}
