package io.agentcore.session;

/**
 * Exception thrown by {@link SessionStore} operations.
 *
 * <p>Wraps underlying IO errors with session-specific context.
 */
public class SessionStoreException extends RuntimeException {

    public SessionStoreException(String message) {
        super(message);
    }

    public SessionStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
