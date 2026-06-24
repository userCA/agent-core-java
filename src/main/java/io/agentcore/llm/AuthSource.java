package io.agentcore.llm;

import java.util.Map;
import java.util.function.Function;

/**
 * Credential resolution strategy for LLM providers.
 *
 * <p>Mirrors Python {@code agent_core/providers/auth.py} AuthSource dataclass.
 * Supports three modes:
 * <ul>
 *   <li>{@link #staticAuth(String)} — fixed API key</li>
 *   <li>{@link #env(String)} — read from environment variable</li>
 *   <li>{@link #dynamic(Function)} — custom resolver callback</li>
 * </ul>
 */
public final class AuthSource {

    private final Function<String, ProviderAuth> resolver;

    private AuthSource(Function<String, ProviderAuth> resolver) {
        this.resolver = resolver;
    }

    /**
     * Resolve credentials for the given provider name.
     */
    public ProviderAuth resolve(String provider) {
        return resolver.apply(provider);
    }

    /**
     * Create an AuthSource that always returns the same API key.
     */
    public static AuthSource staticAuth(String apiKey) {
        return staticAuth(apiKey, null);
    }

    /**
     * Create an AuthSource with a fixed API key and extra headers.
     */
    public static AuthSource staticAuth(String apiKey, Map<String, String> extraHeaders) {
        return new AuthSource(provider -> new ProviderAuth(apiKey, extraHeaders));
    }

    /**
     * Create an AuthSource that reads from an environment variable.
     */
    public static AuthSource env(String varName) {
        return new AuthSource(provider -> {
            String value = System.getenv(varName);
            if (value == null || value.isBlank()) {
                throw new MissingCredentialsError(
                        "Environment variable " + varName + " not set for provider " + provider);
            }
            return new ProviderAuth(value);
        });
    }

    /**
     * Create an AuthSource with a custom resolver function.
     */
    public static AuthSource dynamic(Function<String, ProviderAuth> callback) {
        return new AuthSource(callback);
    }

    /**
     * Thrown when no credentials can be resolved for a provider.
     */
    public static class MissingCredentialsError extends RuntimeException {
        public MissingCredentialsError(String message) {
            super(message);
        }
    }
}
