package io.agentcore.retrieval;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Asynchronous document retriever (RAG building block).
 *
 * <p>Implementations search an internal or external index and return
 * the most relevant chunks for a given {@link Query}.
 *
 * <p>Usage:
 * <pre>{@code
 * Retriever retriever = new InMemoryRetriever();
 * List<RetrievedChunk> chunks = retriever.retrieve(new Query("agent architecture")).get();
 * }</pre>
 */
public interface Retriever {

    /**
     * Search for chunks matching the given query.
     *
     * @param query the retrieval query (text, topK, filters)
     * @return a future completing with the ranked list of matching chunks
     */
    CompletableFuture<List<RetrievedChunk>> retrieve(Query query);
}
