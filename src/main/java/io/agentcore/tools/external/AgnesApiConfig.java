package io.agentcore.tools.external;
import io.agentcore.tools.Tool;
import io.agentcore.tools.ToolContext;
import io.agentcore.tools.ToolDefinition;

/**
 * Shared configuration constants for Agnes API tools.
 *
 * <p>Centralizes the base URL and API key resolution used by
 * {@link AgnesImageTool}, {@link AgnesVideoTool}, and {@link CheckVideoTool}.
 */
public final class AgnesApiConfig {

    private AgnesApiConfig() {}

    /** Base URL for the Agnes API. */
    public static final String API_BASE = "https://apihub.agnes-ai.com/v1";

    /**
     * Build a full API URL by appending a path to the base URL.
     *
     * @param path the API path (e.g. "/images/generations", "/videos/123")
     * @return the complete URL
     */
    public static String url(String path) {
        return API_BASE + path;
    }

    /**
     * Read the AGNES_API_KEY from the environment.
     *
     * @return the API key, or {@code null} if not set or empty
     */
    public static String getApiKey() {
        String key = System.getenv("AGNES_API_KEY");
        return (key != null && !key.isEmpty()) ? key : null;
    }
}
