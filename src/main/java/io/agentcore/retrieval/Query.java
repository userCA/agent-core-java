package io.agentcore.retrieval;

import java.util.Map;

/**
 * A retrieval query describing what to search for.
 *
 * @param text    natural-language query text
 * @param topK    maximum number of chunks to return
 * @param filters optional metadata equality filters (key → value)
 */
public record Query(String text, int topK, Map<String, Object> filters) {

    /** Convenience: query with no filters. */
    public Query(String text, int topK) {
        this(text, topK, Map.of());
    }

    /** Convenience: query with default topK=5 and no filters. */
    public Query(String text) {
        this(text, 5, Map.of());
    }
}
