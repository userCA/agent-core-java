package io.agentcore.retrieval;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Document retriever (RAG building block).
 *
 * <p>Implementations search an internal or external index and return
 * the most relevant chunks for a given {@link Query}.
 *
 * <p>The primary method {@link #retrieve(Query)} is synchronous and designed
 * to run on virtual threads. Use {@link #retrieveAsync(Query)} for non-blocking
 * access — it delegates to a shared virtual-thread executor.
 *
 * <p>Usage:
 * <pre>{@code
 * Retriever retriever = new InMemoryRetriever();
 * List<RetrievedChunk> chunks = retriever.retrieve(new Query("agent architecture"));
 * }</pre>
 */
public interface Retriever {

    /** Shared virtual-thread executor for async retrieval. */
    ExecutorService VIRTUAL_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Search for chunks matching the given query (synchronous).
     *
     * <p>Designed to run on a virtual thread; implementations may perform
     * blocking I/O directly without wrapping in futures.
     *
     * @param query the retrieval query (text, topK, filters)
     * @return the ranked list of matching chunks
     */
    List<RetrievedChunk> retrieve(Query query);

    /**
     * Asynchronous wrapper around {@link #retrieve(Query)}.
     *
     * @param query the retrieval query
     * @return a future completing with the ranked list of matching chunks
     */
    default CompletableFuture<List<RetrievedChunk>> retrieveAsync(Query query) {
        return CompletableFuture.supplyAsync(() -> retrieve(query), VIRTUAL_EXECUTOR);
    }
}
