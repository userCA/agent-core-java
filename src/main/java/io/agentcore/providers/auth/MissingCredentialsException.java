package io.agentcore.providers.auth;

/**
 * Thrown when authentication credentials are missing or cannot be resolved.
 */
public class MissingCredentialsException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public MissingCredentialsException(String message) {
        super(message);
    }
}
