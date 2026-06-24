package io.agentcore.retrieval;

import java.util.Map;
import java.util.Objects;

/**
 * A retrieval query describing what to search for.
 *
 * @param text    natural-language query text (must not be null)
 * @param topK    maximum number of chunks to return (must be &gt;= 1)
 * @param filters optional metadata equality filters (key → value); null → empty map
 */
public record Query(String text, int topK, Map<String, Object> filters) {

    /**
     * Compact constructor with validation.
     */
    public Query {
        Objects.requireNonNull(text, "Query text must not be null");
        if (topK < 1) throw new IllegalArgumentException("topK must be >= 1, got: " + topK);
        if (filters == null) filters = Map.of();
    }

    /** Convenience: query with no filters. */
    public Query(String text, int topK) {
        this(text, topK, Map.of());
    }

    /** Convenience: query with default topK=5 and no filters. */
    public Query(String text) {
        this(text, 5, Map.of());
    }
}
