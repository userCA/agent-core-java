package io.agentcore.tools;

import io.agentcore.core.Content;
import io.agentcore.core.Content.TextContent;
import io.agentcore.core.Content.ImageContent;

import java.util.List;
import java.util.Map;

/**
 * Result returned by a tool execution.
 *
 * <p>Mirrors Python {@code agent_core/tools/base.py} ToolResult.
 *
 * @param content  list of text/image content blocks
 * @param details  optional structured details
 * @param display  optional display hints for UI
 */
public record ToolResult(
        List<Content> content,
        Object details,
        Map<String, Object> display
) {
    public ToolResult {
        if (content == null) content = List.of();
        else content = List.copyOf(content);
        if (display != null) display = Map.copyOf(display);
    }

    /**
     * Create a simple text result.
     */
    public ToolResult(String text) {
        this(List.of(new TextContent(text)), null, null);
    }

    /**
     * Create a result from content blocks.
     */
    public ToolResult(List<Content> content) {
        this(content, null, null);
    }

    /**
     * Extract the first text content.
     */
    public String text() {
        return content.stream()
                .filter(c -> c instanceof TextContent)
                .map(c -> ((TextContent) c).text())
                .findFirst()
                .orElse("");
    }
}
