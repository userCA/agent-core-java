package io.agentcore.memory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Abstraction for persisting and retrieving long-term memories.
 *
 * <p>Implementations can be in-memory, file-backed, or backed by
 * external services (e.g. vector databases).
 *
 * <p>All methods are asynchronous to support both sync and async backends.
 */
public interface MemoryStore {

    /**
     * Persist a memory record for the given session.
     *
     * @param sessionId the session identifier
     * @param text      the text to remember
     * @param metadata  optional metadata (may be null)
     * @return future completing when the record is persisted
     */
    CompletableFuture<Void> remember(String sessionId, String text, java.util.Map<String, Object> metadata);

    /**
     * Recall memories relevant to the given query.
     *
     * @param sessionId the session to search within
     * @param query     the search query (typically the latest user message)
     * @param limit     maximum number of records to return
     * @return future resolving to a list of matching records, ranked by relevance
     */
    CompletableFuture<List<MemoryRecord>> recall(String sessionId, String query, int limit);

    /**
     * Delete all memories for the given session.
     *
     * @param sessionId the session to forget
     * @return future completing when records are deleted
     */
    CompletableFuture<Void> forget(String sessionId);
}
