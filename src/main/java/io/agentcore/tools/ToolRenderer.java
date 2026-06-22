package io.agentcore.tools;

import java.util.Map;

/**
 * Tool rendering interface for frontend display.
 *
 * <p>Mirrors Python {@code agent_core/tools/render.py} ToolRenderer protocol.
 * Provides optional rendering hooks that tools can use to produce
 * user-friendly display output for tool calls and results.
 */
public interface ToolRenderer {

    /**
     * Rendered output of a tool call or result for frontend consumption.
     *
     * @param text     rendered text content
     * @param display  optional display hints for UI (nullable)
     * @param mimeType MIME type of the output (default: "text/plain")
     */
    record RenderedOutput(
            String text,
            Map<String, Object> display,
            String mimeType
    ) {
        public RenderedOutput(String text) {
            this(text, null, "text/plain");
        }

        public RenderedOutput(String text, Map<String, Object> display) {
            this(text, display, "text/plain");
        }
    }

    /**
     * Render a short summary of the tool call (e.g. 'read /path/to/file').
     *
     * @param toolCallId unique ID for this tool call
     * @param name       tool name
     * @param arguments  tool call arguments
     * @return rendered summary string
     */
    String renderCall(String toolCallId, String name, Map<String, Object> arguments);

    /**
     * Render the tool execution result.
     *
     * @param toolCallId unique ID for this tool call
     * @param name       tool name
     * @param result     the tool result
     * @param isError    whether the execution resulted in an error
     * @return rendered output with text, display hints, and MIME type
     */
    RenderedOutput renderResult(String toolCallId, String name,
                                ToolResult result, boolean isError);
}
