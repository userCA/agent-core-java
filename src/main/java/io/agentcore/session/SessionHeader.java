package io.agentcore.session;

/**
 * Session header metadata.
 *
 * <p>Mirrors Python {@code agent_core/session/store.py} SessionHeader.
 */
public record SessionHeader(
    String id,
    String timestamp,
    String cwd,
    String version
) {
    public SessionHeader(String id, String timestamp, String cwd) {
        this(id, timestamp, cwd, "1");
    }

    public SessionHeader(String id, String timestamp) {
        this(id, timestamp, "", "1");
    }
}
