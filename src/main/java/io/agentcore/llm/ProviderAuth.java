package io.agentcore.llm;

import java.util.Map;

/**
 * Credentials for authenticating with an LLM provider.
 *
 * <p>Mirrors Python {@code agent_core/providers/auth.py} ProviderAuth dataclass.
 *
 * @param apiKey        the API key or bearer token
 * @param extraHeaders  additional HTTP headers to include in requests (nullable)
 */
public record ProviderAuth(String apiKey, Map<String, String> extraHeaders) {

    public ProviderAuth {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey must not be null or blank");
        }
        extraHeaders = extraHeaders == null ? null : Map.copyOf(extraHeaders);
    }

    /**
     * Convenience: create auth with just an API key, no extra headers.
     */
    public ProviderAuth(String apiKey) {
        this(apiKey, null);
    }
}
