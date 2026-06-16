package io.agentcore.core.messages;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentcore.core.content.TextContent;
import io.agentcore.core.content.ToolCallContent;
import io.agentcore.core.content.Content;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AgentMessageTest {
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
    }

    @Test
    void userMessage_basicProperties() {
        var msg = new UserMessage(List.of(new TextContent("hi")));
        assertEquals("user", msg.role());
        assertTrue(msg.timestamp() > 0);
        assertEquals(1, msg.content().size());
        assertInstanceOf(TextContent.class, msg.content().get(0));
    }

    @Test
    void userMessage_nullContent_defaultsToEmpty() {
        var msg = new UserMessage(null, 1234.0);
        assertNotNull(msg.content());
        assertTrue(msg.content().isEmpty());
    }

    @Test
    void assistantMessage_toolCalls_convenienceMethods() {
        var msg = new AssistantMessage(
                List.of(
                        new TextContent("Let me read that file."),
                        new ToolCallContent("tc_1", "read", Map.of("path", "/a.txt"))
                ),
                new Usage(100, 50, 0, 0),
                StopReason.TOOL_USE, null,
                false, false, "openai", "gpt-4o", 1234.0
        );

        assertTrue(msg.hasToolCalls());
        assertEquals(1, msg.toolCalls().size());
        assertEquals("tc_1", msg.toolCalls().get(0).id());
        assertEquals("read", msg.toolCalls().get(0).name());
        assertEquals("assistant", msg.role());
    }

    @Test
    void assistantMessage_noToolCalls() {
        var msg = new AssistantMessage(
                List.of(new TextContent("Done.")),
                new Usage(10, 5, 0, 0),
                StopReason.STOP, null,
                false, false, "openai", "gpt-4o", 1234.0
        );

        assertFalse(msg.hasToolCalls());
        assertTrue(msg.toolCalls().isEmpty());
    }

    @Test
    void mutableAssistant_streamingAccumulation() {
        var initial = new AssistantMessage();
        var mutable = initial.toMutable();

        mutable.stopReason(StopReason.STOP);
        mutable.usage(new Usage(50, 25, 0, 0));
        mutable.errorMessage(null);
        mutable.content(List.of(new TextContent("Hello!")));

        var record = mutable.toRecord();
        assertEquals(StopReason.STOP, record.stopReason());
        assertEquals(50, record.usage().inputTokens());
        assertEquals(1, record.content().size());
        assertNull(record.errorMessage());
    }

    @Test
    void toolResultMessage_basicProperties() {
        var msg = new ToolResultMessage("tc_1", "bash",
                List.of(new TextContent("output line 1")), false);
        assertEquals("tool_result", msg.role());
        assertEquals("tc_1", msg.toolCallId());
        assertEquals("bash", msg.toolName());
        assertFalse(msg.isError());
    }

    @Test
    void customMessage_basicProperties() {
        var msg = new CustomMessage("thinking", "Let me think about this...");
        assertEquals("custom", msg.role());
        assertEquals("thinking", msg.customType());
        assertEquals("Let me think about this...", msg.contentAsText());
    }

    @Test
    void usage_totalTokens() {
        var usage = new Usage(100, 50, 30, 10);
        assertEquals(150, usage.totalTokens());
        assertEquals(190, usage.totalTokensWithCache());
    }

    @Test
    void usage_defaultConstructor() {
        var usage = new Usage();
        assertEquals(0, usage.totalTokens());
    }

    @Test
    void userMessage_serializationRoundTrip() throws Exception {
        var original = new UserMessage(
                List.of(new TextContent("Hello")),
                1234.5
        );
        String json = mapper.writeValueAsString(original);
        AgentMessage restored = mapper.readValue(json, AgentMessage.class);

        assertInstanceOf(UserMessage.class, restored);
        var restoredUser = (UserMessage) restored;
        assertEquals("user", restoredUser.role());
        assertEquals(1, restoredUser.content().size());
    }
}
