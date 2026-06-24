package io.agentcore.session;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory session store — useful for testing and short-lived sessions.
 *
 * <p>Mirrors a simple in-memory implementation of the Python SessionStore.
 */
public class MemorySessionStore implements SessionStore {

    private final Map<String, SessionHeader> headers = new ConcurrentHashMap<>();
    private final Map<String, List<SessionEntry>> entries = new ConcurrentHashMap<>();

    @Override
    public void createSession(String sessionId, SessionHeader header) {
        headers.put(sessionId, header);
        entries.put(sessionId, Collections.synchronizedList(new ArrayList<>()));
    }

    @Override
    public void appendEntry(String sessionId, SessionEntry entry) {
        List<SessionEntry> list = entries.get(sessionId);
        if (list == null) {
            throw new IllegalArgumentException("Session " + sessionId + " not found");
        }
        list.add(entry);
    }

    @Override
    public SessionSnapshot loadSession(String sessionId) {
        SessionHeader header = headers.get(sessionId);
        if (header == null) {
            throw new IllegalArgumentException("Session " + sessionId + " not found");
        }
        List<SessionEntry> list = entries.getOrDefault(sessionId, List.of());
        return new SessionSnapshot(header, List.copyOf(list));
    }

    @Override
    public List<SessionMeta> listSessions(String owner, int limit) {
        return headers.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .limit(limit)
                .map(e -> new SessionMeta(
                        e.getKey(),
                        e.getValue().timestamp(),
                        entries.getOrDefault(e.getKey(), List.of()).size(),
                        ""))
                .toList();
    }

    @Override
    public boolean sessionExists(String sessionId) {
        return headers.containsKey(sessionId);
    }

    @Override
    public boolean deleteSession(String sessionId) {
        return headers.remove(sessionId) != null && entries.remove(sessionId) != null;
    }

    @Override
    public void close() {
        // No-op
    }
}
