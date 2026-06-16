package io.agentcore.tools.base;

import io.agentcore.core.content.Content;

import java.util.List;
import java.util.Map;

/**
 * Result of a tool execution.
 */
public record ToolResult(
        List<Content> content,
        Object details,
        Map<String, Object> display
) {
    public ToolResult {
        if (content == null) content = List.of();
    }

    public ToolResult(List<Content> content) {
        this(content, null, null);
    }

    public ToolResult(List<Content> content, Object details) {
        this(content, details, null);
    }
}
