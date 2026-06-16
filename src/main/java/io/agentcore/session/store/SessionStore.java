package io.agentcore.session.store;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface SessionStore {
    CompletableFuture<Void> createSession(String sessionId, SessionHeader header);
    CompletableFuture<Void> appendEntry(String sessionId, SessionEntry entry);
    CompletableFuture<SessionSnapshot> loadSession(String sessionId);
    CompletableFuture<List<SessionMeta>> listSessions(String owner, int limit);
    CompletableFuture<Void> close();
}
