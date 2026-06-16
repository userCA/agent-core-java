package io.agentcore.providers.auth;

import io.agentcore.providers.types.ProviderAuth;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Credential resolver for model providers.
 * Supports static keys, environment variable lookups, and dynamic callbacks (e.g. OAuth refresh).
 */
public class AuthSource {
    private final Function<String, CompletableFuture<ProviderAuth>> resolver;

    private AuthSource(Function<String, CompletableFuture<ProviderAuth>> resolver) {
        this.resolver = resolver;
    }

    /**
     * Resolve credentials for the given provider name.
     */
    public CompletableFuture<ProviderAuth> resolve(String provider) {
        return resolver.apply(provider);
    }

    /**
     * Always returns the same fixed API key.
     */
    public static AuthSource staticAuth(String apiKey) {
        var auth = new ProviderAuth(apiKey);
        return new AuthSource(p -> CompletableFuture.completedFuture(auth));
    }

    /**
     * Reads the API key from an environment variable.
     */
    public static AuthSource env(String envVar) {
        return new AuthSource(p -> {
            String key = System.getenv(envVar);
            if (key == null || key.isBlank()) {
                return CompletableFuture.failedFuture(
                        new MissingCredentialsException("Environment variable '" + envVar + "' is not set"));
            }
            return CompletableFuture.completedFuture(new ProviderAuth(key));
        });
    }

    /**
     * Wraps an arbitrary async callback (e.g. for OAuth token refresh).
     */
    public static AuthSource dynamic(Function<String, CompletableFuture<ProviderAuth>> callback) {
        return new AuthSource(callback);
    }
}
