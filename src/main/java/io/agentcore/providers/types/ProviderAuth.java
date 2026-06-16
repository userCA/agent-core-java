package io.agentcore.providers.types;

import java.util.Map;

/**
 * Resolved authentication credentials for a provider.
 */
public record ProviderAuth(
        String apiKey,
        Map<String, String> extraHeaders
) {
    public ProviderAuth(String apiKey) {
        this(apiKey, null);
    }
}
