package io.agentcore.model;

import io.agentcore.model.Content;
import io.agentcore.model.Content.TextContent;
import java.util.List;
import java.util.Map;

/**
 * Result returned by a tool execution — pure data model.
 *
 * <p>Control flow signals (e.g. terminate) are handled separately via
 * {@link ToolContext#requestTerminate()} and {@link io.agentcore.agent.ToolRunner.ToolCallResult},
 * keeping this record focused on tool output data.
 *
 * @param content  list of text/image content blocks
 * @param details  optional structured details
 * @param display  optional display hints for UI
 */
public record ToolResult(
        List<Content> content,
        Map<String, Object> details,
        Map<String, Object> display
) {
    public ToolResult {
        if (content == null) content = List.of();
        else content = List.copyOf(content);
        if (details != null) details = Map.copyOf(details);
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

    // ── Error factory methods ────────────────────────────────

    /**
     * Create a standardized error result with a code and message.
     */
    public static ToolResult error(String code, String message) {
        return new ToolResult(
                List.of(new TextContent("ERROR [" + code + "]: " + message)),
                Map.of("error", code, "message", message), null);
    }

    /**
     * Create a simple error result with only a message.
     */
    public static ToolResult error(String message) {
        return new ToolResult(
                List.of(new TextContent("ERROR: " + message)),
                Map.of("error", "general", "message", message), null);
    }
}
