package io.agentcore.providers;

import io.agentcore.core.Content;
import io.agentcore.core.Content.*;
import io.agentcore.core.Message;
import io.agentcore.core.Message.*;
import io.agentcore.providers.openai.OpenAIProvider;
import io.agentcore.providers.anthropic.AnthropicProvider;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Phase 2: Provider layer (MessageConverter, OpenAI/Anthropic providers).
 */
class ProviderTest {

    // ── MessageConverter (OpenAI format) ──────────────────────

    @Nested
    class MessageConverterTest {

        private final MessageConverter converter = new MessageConverter();

        @Test
        void convertUserMessageTextOnly() {
            var msg = new UserMessage(List.of(new TextContent("Hello")), System.currentTimeMillis() / 1000.0);
            var result = converter.convert(List.of(msg));
            assertEquals(1, result.size());
            assertEquals("user", result.get(0).get("role"));
            assertEquals("Hello", result.get(0).get("content"));
        }

        @Test
        void convertUserMessageWithImage() {
            var msg = new UserMessage(
                    List.of(new TextContent("Look at this"), new ImageContent("ABC123", "image/png")),
                    System.currentTimeMillis() / 1000.0);
            var result = converter.convert(List.of(msg));
            assertEquals(1, result.size());
            Object content = result.get(0).get("content");
            assertInstanceOf(List.class, content);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content;
            assertEquals(2, parts.size());
            assertEquals("text", parts.get(0).get("type"));
            assertEquals("image_url", parts.get(1).get("type"));
        }

        @Test
        void convertAssistantMessageWithToolCalls() {
            var msg = AssistantMessage.builder()
                    .addContent(new TextContent("Let me check"))
                    .addContent(new ToolCallContent("tc1", "search", Map.of("query", "test")))
                    .build();
            var result = converter.convert(List.of(msg));
            assertEquals(1, result.size());
            assertEquals("assistant", result.get(0).get("role"));
            assertEquals("Let me check", result.get(0).get("content"));
            assertNotNull(result.get(0).get("tool_calls"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) result.get(0).get("tool_calls");
            assertEquals(1, toolCalls.size());
            assertEquals("tc1", toolCalls.get(0).get("id"));
            assertEquals("function", toolCalls.get(0).get("type"));
        }

        @Test
        void convertToolResultMessage() {
            var msg = new ToolResultMessage("tc1", "search",
                    List.of(new TextContent("Found 3 results")), false,
                    System.currentTimeMillis() / 1000.0);
            var result = converter.convert(List.of(msg));
            assertEquals(1, result.size());
            assertEquals("tool", result.get(0).get("role"));
            assertEquals("tc1", result.get(0).get("tool_call_id"));
            assertEquals("Found 3 results", result.get(0).get("content"));
        }

        @Test
        void convertToolResultNoTruncation() {
            // MessageConverter no longer truncates — ToolRunner.truncateContent() handles it
            String longText = "x".repeat(5000);
            var msg = new ToolResultMessage("tc1", "search",
                    List.of(new TextContent(longText)), false,
                    System.currentTimeMillis() / 1000.0);
            var result = converter.convert(List.of(msg));
            String content = (String) result.get(0).get("content");
            assertEquals(longText, content); // no truncation in converter
        }

        @Test
        void convertCompactionSummary() {
            var msg = CustomMessage.compactionSummary("Summary of conversation so far");
            var result = converter.convert(List.of(msg));
            assertEquals(1, result.size());
            assertEquals("system", result.get(0).get("role"));
            assertTrue(((String) result.get(0).get("content")).contains("Earlier conversation summary"));
        }
    }

    // ── AnthropicMessageConverter ────────────────────────────

    @Nested
    class AnthropicConverterTest {

        private final AnthropicProvider.AnthropicMessageConverter converter =
                new AnthropicProvider().createMessageConverter();

        @Test
        void convertUserMessage() {
            var msg = new UserMessage(List.of(new TextContent("Hello")), System.currentTimeMillis() / 1000.0);
            var result = converter.convert(List.of(msg));
            assertEquals(1, result.size());
            assertEquals("user", result.get(0).get("role"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> blocks = (List<Map<String, Object>>) result.get(0).get("content");
            assertEquals(1, blocks.size());
            assertEquals("text", blocks.get(0).get("type"));
            assertEquals("Hello", blocks.get(0).get("text"));
        }

        @Test
        void convertAssistantWithToolUse() {
            var msg = AssistantMessage.builder()
                    .addContent(new TextContent("Searching"))
                    .addContent(new ToolCallContent("tc1", "search", Map.of("q", "test")))
                    .build();
            var result = converter.convert(List.of(msg));
            assertEquals(1, result.size());
            assertEquals("assistant", result.get(0).get("role"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> blocks = (List<Map<String, Object>>) result.get(0).get("content");
            assertEquals(2, blocks.size());
            assertEquals("text", blocks.get(0).get("type"));
            assertEquals("tool_use", blocks.get(1).get("type"));
            assertEquals("tc1", blocks.get(1).get("id"));
        }

        @Test
        void convertToolResultAttachesToUserMessage() {
            var userMsg = new UserMessage(List.of(new TextContent("Find X")), System.currentTimeMillis() / 1000.0);
            var assistantMsg = AssistantMessage.builder()
                    .addContent(new ToolCallContent("tc1", "search", Map.of()))
                    .build();
            var toolResult = new ToolResultMessage("tc1", "search",
                    List.of(new TextContent("Found it")), false,
                    System.currentTimeMillis() / 1000.0);
            var result = converter.convert(List.of(userMsg, assistantMsg, toolResult));
            // user, assistant, user(tool_result)
            assertEquals(3, result.size());
            assertEquals("user", result.get(0).get("role"));
            assertEquals("assistant", result.get(1).get("role"));
            assertEquals("user", result.get(2).get("role"));
        }
    }

    // ── OpenAIProvider helpers ───────────────────────────────

    @Nested
    class OpenAIProviderTest {

        @Test
        void contextOverflowDetection() {
            assertTrue(ProviderUtils.isContextOverflow("context_length_exceeded"));
            assertTrue(ProviderUtils.isContextOverflow("maximum context length"));
            assertTrue(ProviderUtils.isContextOverflow("prompt is too long"));
            assertFalse(ProviderUtils.isContextOverflow("invalid api key"));
        }

        @Test
        void defaultModelsList() {
            var provider = new OpenAIProvider();
            assertEquals("openai", provider.name());
            assertEquals(2, provider.listModels().size());
            assertEquals("gpt-4o", provider.listModels().get(0).id());
        }

        @Test
        void toolDefinitionToProviderFormat() {
            var toolDef = new io.agentcore.tools.ToolDefinition(
                    "search", "Search the web",
                    Map.of("type", "object", "properties", Map.of()));
            var result = toolDef.toProviderFormat();
            assertEquals("function", result.get("type"));
            @SuppressWarnings("unchecked")
            Map<String, Object> fn = (Map<String, Object>) result.get("function");
            assertEquals("search", fn.get("name"));
        }
    }

    // ── AnthropicProvider helpers ────────────────────────────

    @Nested
    class AnthropicProviderTest {

        @Test
        void defaultModelsList() {
            var provider = new AnthropicProvider();
            assertEquals("anthropic", provider.name());
            assertEquals(2, provider.listModels().size());
            assertTrue(provider.listModels().get(0).supportsReasoning());
        }
    }

    // ── ProviderUtils ────────────────────────────────────────

    @Nested
    class ProviderUtilsTest {

        @Test
        void parseJsonMap() {
            var result = ProviderUtils.parseJsonMap("{\"key\":\"value\"}");
            assertEquals("value", result.get("key"));
        }

        @Test
        void parseJsonMapInvalid() {
            var result = ProviderUtils.parseJsonMap("not json");
            assertTrue(result.isEmpty());
        }

        @Test
        void parseJsonMapEmpty() {
            var result = ProviderUtils.parseJsonMap("");
            assertTrue(result.isEmpty());
        }

        @Test
        void toIntFromNumber() {
            assertEquals(42, ProviderUtils.toInt(42));
            assertEquals(42, ProviderUtils.toInt(42L));
            assertEquals(42, ProviderUtils.toInt(42.5));
            assertEquals(0, ProviderUtils.toInt("not a number"));
            assertEquals(0, ProviderUtils.toInt(null));
        }
    }

    // ── ModelProvider interface ──────────────────────────────

    @Nested
    class ModelProviderInterfaceTest {

        @Test
        void createAbortSignal() {
            var signal = ModelProvider.createAbortSignal();
            assertFalse(signal.get());
            signal.set(true);
            assertTrue(signal.get());
        }
    }
}
