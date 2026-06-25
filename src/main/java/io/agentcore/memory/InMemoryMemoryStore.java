package io.agentcore.memory;

import io.agentcore.util.TextTokenizer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link MemoryStore} with token-overlap ranking.
 *
 * <p>Records are stored per session in a {@link ConcurrentHashMap}.
 * Tokens are pre-computed at {@code remember()} time to avoid repeated
 * tokenization during {@code recall()}.
 * Recall ranks results by the number of shared tokens between the
 * query and each record, with recency as a tiebreaker.
 */
public class InMemoryMemoryStore implements MemoryStore {

    /** Score multiplier for token overlap; recency index serves as tiebreaker. */
    private static final long OVERLAP_WEIGHT = 1000L;

    /** Pre-tokenized wrapper to avoid re-tokenizing on every recall. */
    private record CachedRecord(MemoryRecord record, Set<String> tokens) {}

    private final ConcurrentHashMap<String, List<CachedRecord>> store = new ConcurrentHashMap<>();

    @Override
    public void remember(String sessionId, String text, Map<String, Object> metadata) {
        List<CachedRecord> list = store.computeIfAbsent(sessionId,
                k -> Collections.synchronizedList(new ArrayList<>()));
        MemoryRecord rec = new MemoryRecord(text, sessionId,
                metadata != null ? Map.copyOf(metadata) : Map.of());
        Set<String> tokens = TextTokenizer.tokenize(text);
        synchronized (list) {
            list.add(new CachedRecord(rec, tokens));
        }
    }

    @Override
    public List<MemoryRecord> recall(String sessionId, String query, int limit) {
        List<CachedRecord> records = store.get(sessionId);
        if (records == null) {
            return List.of();
        }

        // Take snapshot under per-session lock
        List<CachedRecord> snapshot;
        synchronized (records) {
            if (records.isEmpty()) return List.of();
            snapshot = new ArrayList<>(records);
        }

        Set<String> queryTokens = TextTokenizer.tokenize(query);
        if (queryTokens.isEmpty()) {
            return List.of();
        }

        // Score each record by token overlap, with recency as tiebreaker
        List<ScoredRecord> scored = new ArrayList<>();
        for (int i = 0; i < snapshot.size(); i++) {
            CachedRecord cached = snapshot.get(i);
            int overlap = TextTokenizer.intersectionSize(queryTokens, cached.tokens());
            if (overlap > 0) {
                scored.add(new ScoredRecord(cached.record(), overlap * OVERLAP_WEIGHT + i));
            }
        }

        scored.sort(Comparator.<ScoredRecord>comparingLong(s -> -s.score));
        return scored.stream()
                .limit(limit)
                .map(s -> s.record)
                .toList();
    }

    @Override
    public void forget(String sessionId) {
        store.remove(sessionId);
    }

    /** Returns the total number of records across all sessions. */
    public int size() {
        int total = 0;
        for (List<CachedRecord> list : store.values()) {
            synchronized (list) {
                total += list.size();
            }
        }
        return total;
    }

    private record ScoredRecord(MemoryRecord record, long score) {}
}
