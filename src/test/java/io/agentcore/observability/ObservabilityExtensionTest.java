package io.agentcore.observability;

import io.agentcore.core.AgentEvent.*;
import io.agentcore.core.Message.AssistantMessage;
import io.agentcore.core.Content.TextContent;
import io.agentcore.core.Content;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ObservabilityExtensionTest {

    /** Collects emitted events for verification. */
    static class CollectingSink implements ObservabilityExtension.EventSink {
        final List<String> eventTypes = new ArrayList<>();
        final List<Map<String, Object>> attributes = new ArrayList<>();

        @Override
        public void emit(String eventType, Map<String, Object> attrs) {
            eventTypes.add(eventType);
            attributes.add(attrs);
        }
    }

    @Test
    void emitsStartAndEndEvents() {
        CollectingSink sink = new CollectingSink();
        ObservabilityExtension ext = new ObservabilityExtension("s1", "openai", "gpt-4", sink);

        ext.onEvent(new AgentStart());
        ext.onEvent(new AgentEnd(List.of(new AssistantMessage(
                List.of(new TextContent("Hi")), null, null, null, false, false, "", "", 0))));

        assertEquals(2, sink.eventTypes.size());
        assertEquals("agent.start", sink.eventTypes.get(0));
        assertEquals("agent.end", sink.eventTypes.get(1));
        assertEquals("s1", sink.attributes.get(0).get("session_id"));
        assertEquals("openai", sink.attributes.get(0).get("provider"));
    }

    @Test
    void emitsToolCallEvents() {
        CollectingSink sink = new CollectingSink();
        ObservabilityExtension ext = new ObservabilityExtension("s2", "", "", sink);

        ext.onEvent(new ToolExecutionStart("tc1", "bash", Map.of("command", "ls")));
        ext.onEvent(new ToolExecutionEnd("tc1", "bash", "output", false));

        assertEquals("agent.tool_call.start", sink.eventTypes.get(0));
        assertEquals("agent.tool_call.end", sink.eventTypes.get(1));
        assertEquals("bash", sink.attributes.get(0).get("tool.name"));
        assertEquals(false, sink.attributes.get(1).get("is_error"));
    }

    @Test
    void noOpSinkDoesNotThrow() {
        ObservabilityExtension ext = new ObservabilityExtension("s3", "", "",
                ObservabilityExtension.EventSinks.noOp());
        assertDoesNotThrow(() -> ext.onEvent(new AgentStart()));
    }

    @Test
    void nameIsObservability() {
        ObservabilityExtension ext = new ObservabilityExtension("s4");
        assertEquals("observability", ext.name());
    }

    @Test
    void unknownEventIgnored() {
        CollectingSink sink = new CollectingSink();
        ObservabilityExtension ext = new ObservabilityExtension("s5", "", "", sink);

        // MessageStart should not trigger any emit
        ext.onEvent(new MessageStart(new AssistantMessage(
                List.of(new TextContent("test")), null, null, null, false, false, "", "", 0)));
        assertEquals(0, sink.eventTypes.size());
    }
}
