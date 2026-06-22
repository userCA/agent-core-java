package io.agentcore.session;

/**
 * Session metadata for listing sessions.
 *
 * <p>Mirrors Python {@code agent_core/session/store.py} SessionMeta.
 */
public record SessionMeta(
    String sessionId,
    String createdAt,
    int entryCount,
    String title
) {
    public SessionMeta {
        if (title == null) title = "";
    }
}
