package io.agentcore.core.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentcore.core.content.TextContent;
import io.agentcore.core.messages.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentEventTest {
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
    }

    @Test
    void agentStart_type() {
        var evt = new AgentStart();
        assertEquals("agent_start", evt.type());
    }

    @Test
    void agentEnd_type_andMessages() {
        var msgs = List.<AgentMessage>of(
                new AssistantMessage(List.of(new TextContent("hi")),
                        new Usage(), StopReason.STOP, null,
                        false, false, "openai", "gpt-4o", 1.0)
        );
        var evt = new AgentEnd(msgs);
        assertEquals("agent_end", evt.type());
        assertEquals(1, evt.messages().size());
    }

    @Test
    void turnStart_type() {
        var evt = new TurnStart();
        assertEquals("turn_start", evt.type());
    }

    @Test
    void messageStart_serialization() throws Exception {
        var msg = new UserMessage(List.of(new TextContent("test")), 1.0);
        var evt = new MessageStart(msg);
        assertEquals("message_start", evt.type());

        String json = mapper.writeValueAsString(evt);
        assertTrue(json.contains("\"type\":\"message_start\""));
    }

    @Test
    void messageUpdate_withDelta() {
        var msg = new AssistantMessage(List.of(new TextContent("partial")),
                new Usage(), StopReason.STOP, null,
                false, false, "openai", "gpt-4o", 1.0);
        var delta = new TextDelta(" more text");
        var evt = new MessageUpdate(msg, delta);
        assertEquals("message_update", evt.type());
        assertEquals(" more text", ((TextDelta) evt.delta()).text());
    }

    @Test
    void toolExecutionStart_serialization() throws Exception {
        var evt = new ToolExecutionStart("tc_1", "bash", java.util.Map.of("cmd", "ls"));
        assertEquals("tool_execution_start", evt.type());
        assertEquals("tc_1", evt.toolCallId());
        assertEquals("bash", evt.toolName());

        String json = mapper.writeValueAsString(evt);
        assertTrue(json.contains("\"type\":\"tool_execution_start\""));
    }

    @Test
    void humanInputRequired_serialization() throws Exception {
        var evt = new HumanInputRequired("tc_2", "Please confirm", null);
        assertEquals("human_input_required", evt.type());
        assertEquals("tc_2", evt.toolCallId());
        assertEquals("Please confirm", evt.prompt());
    }

    @Test
    void agentStart_serializationRoundTrip() throws Exception {
        var evt = new AgentStart();
        String json = mapper.writeValueAsString(evt);
        AgentEvent restored = mapper.readValue(json, AgentEvent.class);
        assertInstanceOf(AgentStart.class, restored);
    }
}
