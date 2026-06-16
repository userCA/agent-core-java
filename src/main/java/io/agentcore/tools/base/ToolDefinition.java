package io.agentcore.tools.base;

import java.util.List;
import java.util.Map;

/**
 * Describes a tool's interface — name, description, JSON Schema parameters, and optional metadata.
 */
public record ToolDefinition(
        String name,
        String description,
        Map<String, Object> parameters,
        String promptSnippet,
        List<String> promptGuidelines,
        Object renderer,
        Double timeoutSeconds,
        Class<?> argsModel
) {
    public ToolDefinition {
        if (promptGuidelines == null) promptGuidelines = List.of();
    }

    public ToolDefinition(String name, String description, Map<String, Object> parameters) {
        this(name, description, parameters, null, List.of(), null, null, null);
    }

    public ToolDefinition(String name, String description, Map<String, Object> parameters,
                          String promptSnippet, List<String> promptGuidelines) {
        this(name, description, parameters, promptSnippet, promptGuidelines, null, null, null);
    }
}
