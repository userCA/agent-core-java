package io.agentcore.tools;

import java.util.List;
import java.util.Map;

/**
 * Tool definition — metadata describing a tool the LLM can invoke.
 *
 * <p>Mirrors Python {@code agent_core/tools/base.py} ToolDefinition.
 *
 * @param name             unique tool name
 * @param description      human-readable description for the LLM
 * @param parameters       JSON Schema of the tool parameters
 * @param promptSnippet    optional snippet injected into system prompt
 * @param promptGuidelines optional usage guidelines for the LLM
 * @param timeoutSeconds   per-execution timeout (null = use global default)
 */
public record ToolDefinition(
        String name,
        String description,
        Map<String, Object> parameters,
        String promptSnippet,
        List<String> promptGuidelines,
        Double timeoutSeconds
) {
    public ToolDefinition {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name required");
        if (description == null) description = "";
        if (parameters == null) parameters = Map.of("type", "object", "properties", Map.of());
        if (promptGuidelines == null) promptGuidelines = List.of();
    }

    /**
     * Minimal constructor.
     */
    public ToolDefinition(String name, String description, Map<String, Object> parameters) {
        this(name, description, parameters, null, null, null);
    }

    /**
     * Convert to OpenAI function-calling format dict.
     */
    public Map<String, Object> toProviderFormat() {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", name,
                        "description", description,
                        "parameters", parameters
                )
        );
    }
}
