package io.agentcore.retrieval;

import java.util.Map;

/**
 * A single chunk retrieved from a {@link Retriever}.
 *
 * @param text     the chunk text content
 * @param score    relevance score (0.0–1.0, higher is better)
 * @param source   optional source identifier (e.g., file path, document id)
 * @param metadata arbitrary metadata attached to this chunk
 */
public record RetrievedChunk(
        String text,
        double score,
        String source,
        Map<String, Object> metadata) {

    /** Convenience: chunk with no source or metadata. */
    public RetrievedChunk(String text, double score) {
        this(text, score, null, Map.of());
    }

    /** Convenience: chunk with source but no metadata. */
    public RetrievedChunk(String text, double score, String source) {
        this(text, score, source, Map.of());
    }
}
