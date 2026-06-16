package io.agentcore.tools.render;

import java.util.Map;

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
