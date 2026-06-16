package io.agentcore.core.content;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ContentSerializationTest {
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
    }

    @Test
    void textContent_roundTrip() throws Exception {
        var original = new TextContent("Hello, world!");
        String json = mapper.writeValueAsString(original);
        Content restored = mapper.readValue(json, Content.class);

        assertInstanceOf(TextContent.class, restored);
        assertEquals("Hello, world!", ((TextContent) restored).text());
        assertEquals("text", restored.type());
    }

    @Test
    void imageContent_roundTrip() throws Exception {
        var original = new ImageContent("base64data==", "image/png");
        String json = mapper.writeValueAsString(original);
        Content restored = mapper.readValue(json, Content.class);

        assertInstanceOf(ImageContent.class, restored);
        var img = (ImageContent) restored;
        assertEquals("base64data==", img.data());
        assertEquals("image/png", img.mimeType());
        assertEquals("image", img.type());
    }

    @Test
    void toolCallContent_roundTrip() throws Exception {
        var original = new ToolCallContent("tc_1", "read_file",
                Map.of("path", "/tmp/test.txt", "offset", 10));
        String json = mapper.writeValueAsString(original);
        Content restored = mapper.readValue(json, Content.class);

        assertInstanceOf(ToolCallContent.class, restored);
        var tc = (ToolCallContent) restored;
        assertEquals("tc_1", tc.id());
        assertEquals("read_file", tc.name());
        assertEquals("/tmp/test.txt", tc.arguments().get("path"));
        assertEquals(10, tc.arguments().get("offset"));
        assertEquals("tool_call", tc.type());
    }

    @Test
    void toolCallContent_nullArguments_defaultsToEmptyMap() {
        var tc = new ToolCallContent("tc_2", "bash", null);
        assertNotNull(tc.arguments());
        assertTrue(tc.arguments().isEmpty());
    }

    @Test
    void textContent_typeField_inJson() throws Exception {
        var text = new TextContent("test");
        String json = mapper.writeValueAsString(text);
        assertTrue(json.contains("\"type\":\"text\""), "JSON should contain type discriminator");
    }
}
