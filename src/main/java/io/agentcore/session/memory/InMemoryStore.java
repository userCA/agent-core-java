package io.agentcore.session.memory;

import io.agentcore.session.store.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * In-memory session store with bounded capacity.
 * When the maximum number of sessions is reached, the oldest session is evicted (LRU).
 */
public class InMemoryStore implements SessionStore {
    private static final int DEFAULT_MAX_SESSIONS = 1000;

    private final ConcurrentHashMap<String, SessionSnapshot> sessions = new ConcurrentHashMap<>();
    /** Tracks insertion order for LRU eviction. */
    private final ConcurrentLinkedDeque<String> sessionOrder = new ConcurrentLinkedDeque<>();
    private final int maxSessions;

    public InMemoryStore() {
        this(DEFAULT_MAX_SESSIONS);
    }

    public InMemoryStore(int maxSessions) {
        this.maxSessions = maxSessions;
    }

    @Override
    public CompletableFuture<Void> createSession(String sessionId, SessionHeader header) {
        sessions.put(sessionId, new SessionSnapshot(header,
                Collections.synchronizedList(new ArrayList<>())));
        sessionOrder.addLast(sessionId);
        evictIfNeeded();
        return CompletableFuture.completedFuture(null);
    }

    private void evictIfNeeded() {
        while (sessions.size() > maxSessions) {
            String oldest = sessionOrder.pollFirst();
            if (oldest != null) {
                sessions.remove(oldest);
            }
        }
    }

    @Override
    public CompletableFuture<Void> appendEntry(String sessionId, SessionEntry entry) {
        return sessions.compute(sessionId, (id, snap) -> {
            if (snap != null) {
                snap.entries().add(entry);
            }
            return snap;
        }) != null
                ? CompletableFuture.completedFuture(null)
                : CompletableFuture.failedFuture(
                        new NoSuchElementException("Session not found: " + sessionId));
    }

    @Override
    public CompletableFuture<SessionSnapshot> loadSession(String sessionId) {
        var snap = sessions.get(sessionId);
        if (snap == null) return CompletableFuture.failedFuture(new NoSuchElementException(sessionId));
        List<SessionEntry> entriesCopy;
        synchronized (snap.entries()) {
            entriesCopy = new ArrayList<>(snap.entries());
        }
        return CompletableFuture.completedFuture(
                new SessionSnapshot(snap.header(), entriesCopy));
    }

    @Override
    public CompletableFuture<List<SessionMeta>> listSessions(String owner, int limit) {
        List<SessionMeta> result = sessions.entrySet().stream()
                .map(e -> {
                    int size;
                    synchronized (e.getValue().entries()) {
                        size = e.getValue().entries().size();
                    }
                    return new SessionMeta(e.getKey(), e.getValue().header().timestamp(),
                            size, "");
                })
                .limit(limit)
                .toList();
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<Void> close() {
        return CompletableFuture.completedFuture(null);
    }
}
