package io.agentcore.session;

import java.util.List;

/**
 * Loaded session with header and all entries.
 *
 * <p>Mirrors Python {@code agent_core/session/store.py} SessionSnapshot.
 */
public record SessionSnapshot(
    SessionHeader header,
    List<SessionEntry> entries
) {
    public SessionSnapshot {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }
}
