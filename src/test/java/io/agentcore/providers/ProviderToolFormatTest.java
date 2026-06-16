package io.agentcore.providers;

import io.agentcore.providers.anthropic.AnthropicProvider;
import io.agentcore.providers.openai.OpenAIProvider;
import io.agentcore.tools.base.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that OpenAI and Anthropic providers format tool definitions
 * in their respective API formats.
 */
class ProviderToolFormatTest {

    private static ToolDefinition sampleTool() {
        return new ToolDefinition(
                "calculator",
                "Performs arithmetic calculations",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "expression", Map.of("type", "string", "description", "Math expression")
                        ),
                        "required", List.of("expression")
                )
        );
    }

    // === OpenAI Format (BUG-03) ===

    @Test
    void openai_toolFormat_usesFunctionSchema() {
        var provider = new OpenAIProvider();
        var tools = provider.toolsToProviderFormat(List.of(sampleTool()));

        assertEquals(1, tools.size());
        var tool = tools.get(0);
        assertEquals("function", tool.get("type"));

        @SuppressWarnings("unchecked")
        var fn = (Map<String, Object>) tool.get("function");
        assertNotNull(fn);
        assertEquals("calculator", fn.get("name"));
        assertEquals("Performs arithmetic calculations", fn.get("description"));
        assertNotNull(fn.get("parameters"));
    }

    @Test
    void openai_toolFormat_parametersPresent() {
        var provider = new OpenAIProvider();
        var tools = provider.toolsToProviderFormat(List.of(sampleTool()));

        @SuppressWarnings("unchecked")
        var fn = (Map<String, Object>) tools.get(0).get("function");
        @SuppressWarnings("unchecked")
        var params = (Map<String, Object>) fn.get("parameters");
        assertEquals("object", params.get("type"));
        assertNotNull(params.get("properties"));
    }

    // === Anthropic Format (BUG-03) ===

    @Test
    void anthropic_toolFormat_usesInputSchema() {
        var provider = new AnthropicProvider();
        var tools = provider.toolsToProviderFormat(List.of(sampleTool()));

        assertEquals(1, tools.size());
        var tool = tools.get(0);
        assertEquals("calculator", tool.get("name"));
        assertEquals("Performs arithmetic calculations", tool.get("description"));
        // Anthropic uses input_schema, NOT parameters
        assertNotNull(tool.get("input_schema"), "Anthropic format must use input_schema");
        assertNull(tool.get("type"), "Anthropic format should not have 'type' wrapper");
    }

    @Test
    void anthropic_toolFormat_noFunctionWrapper() {
        var provider = new AnthropicProvider();
        var tools = provider.toolsToProviderFormat(List.of(sampleTool()));

        var tool = tools.get(0);
        // Anthropic format is flat: {name, description, input_schema}
        // Not nested like OpenAI: {type: "function", function: {...}}
        assertNull(tool.get("function"), "Anthropic format should not have function wrapper");
    }

    // === Context Overflow Detection (BUG-04) ===

    @Test
    void openai_isContextOverflow_detectsKnownPatterns() {
        assertTrue(OpenAIProvider.isContextOverflow(400,
                "This model's maximum context_length_exceeded"));
        assertTrue(OpenAIProvider.isContextOverflow(400,
                "maximum context length reached"));
        assertTrue(OpenAIProvider.isContextOverflow(400,
                "prompt is too long"));
        assertFalse(OpenAIProvider.isContextOverflow(400,
                "invalid API key"));
        assertFalse(OpenAIProvider.isContextOverflow(500,
                "context_length_exceeded"));
    }

    @Test
    void anthropic_isContextOverflow_detectsKnownPatterns() {
        assertTrue(AnthropicProvider.isContextOverflow(400,
                "prompt is too long, max is 200000"));
        assertTrue(AnthropicProvider.isContextOverflow(400,
                "max_tokens must be less than"));
        assertTrue(AnthropicProvider.isContextOverflow(400,
                "context limit exceeded"));
        assertTrue(AnthropicProvider.isContextOverflow(400,
                "too many tokens in request"));
        assertTrue(AnthropicProvider.isContextOverflow(400,
                "input too long for model"));
        assertFalse(AnthropicProvider.isContextOverflow(400,
                "invalid api key"));
        assertFalse(AnthropicProvider.isContextOverflow(500,
                "prompt is too long"));
    }
}
