package io.agentcore.providers;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Map;

/**
 * Provider-neutral stream event types emitted during LLM streaming.
 *
 * <p>Mirrors Python {@code agent_core/providers/types.py} StreamEvent union.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = StreamEvent.StreamTextDelta.class, name = "text_delta"),
    @JsonSubTypes.Type(value = StreamEvent.StreamThinkingDelta.class, name = "thinking_delta"),
    @JsonSubTypes.Type(value = StreamEvent.StreamToolCallStart.class, name = "tool_call_start"),
    @JsonSubTypes.Type(value = StreamEvent.StreamToolCallDelta.class, name = "tool_call_delta"),
    @JsonSubTypes.Type(value = StreamEvent.StreamToolCallEnd.class, name = "tool_call_end"),
    @JsonSubTypes.Type(value = StreamEvent.StreamMessageEnd.class, name = "message_end"),
    @JsonSubTypes.Type(value = StreamEvent.StreamError.class, name = "error"),
})
public sealed interface StreamEvent {

    record StreamTextDelta(String text) implements StreamEvent {}

    record StreamThinkingDelta(String text) implements StreamEvent {}

    record StreamToolCallStart(String id, String name) implements StreamEvent {}

    record StreamToolCallDelta(String id, String argumentsDelta) implements StreamEvent {}

    record StreamToolCallEnd(String id, Map<String, Object> arguments) implements StreamEvent {
        public StreamToolCallEnd {
            if (arguments == null) arguments = Map.of();
            else arguments = Map.copyOf(arguments);
        }
    }

    record StreamMessageEnd(String stopReason, int inputTokens, int outputTokens) implements StreamEvent {}

    record StreamError(String message, boolean retryable, boolean overflow) implements StreamEvent {}
}
