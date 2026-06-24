package io.agentcore.session;

import java.util.List;

/**
 * Session persistence interface.
 *
 * <p>Mirrors Python {@code agent_core/session/store.py} SessionStore Protocol.
 * Implementations: {@link MemorySessionStore}, {@link JsonlSessionStore}.
 */
public interface SessionStore extends AutoCloseable {

    /**
     * Create a new session with the given header.
     */
    void createSession(String sessionId, SessionHeader header);

    /**
     * Append an entry to an existing session.
     */
    void appendEntry(String sessionId, SessionEntry entry);

    /**
     * Load a full session snapshot.
     *
     * @throws IllegalArgumentException if session not found
     */
    SessionSnapshot loadSession(String sessionId);

    /**
     * List available sessions.
     *
     * @param owner optional owner filter (unused for now)
     * @param limit maximum number of sessions to return
     */
    List<SessionMeta> listSessions(String owner, int limit);

    /**
     * Close the store and release resources.
     */
    void close();
}
