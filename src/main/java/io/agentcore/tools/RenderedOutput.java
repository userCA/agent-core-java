package io.agentcore.tools;

import java.util.Map;

/**
 * Rendered output of a tool call or result for frontend consumption.
 *
 * @param text     rendered text content
 * @param display  optional display hints for UI (nullable)
 * @param mimeType MIME type of the output (default: "text/plain")
 */
public record RenderedOutput(
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
