package io.agentcore.memory;

import io.agentcore.util.TextTokenizer;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link MemoryStore} with token-overlap ranking.
 *
 * <p>Records are stored per session in a {@link ConcurrentHashMap}.
 * Recall ranks results by the number of shared tokens between the
 * query and each record, with recency as a tiebreaker.
 */
public class InMemoryMemoryStore implements MemoryStore {

    /** Score multiplier for token overlap; recency index serves as tiebreaker. */
    private static final long OVERLAP_WEIGHT = 1000L;

    private final ConcurrentHashMap<String, List<MemoryRecord>> store = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<Void> remember(
            String sessionId, String text, Map<String, Object> metadata) {
        return CompletableFuture.runAsync(() -> {
            List<MemoryRecord> list = store.computeIfAbsent(sessionId,
                    k -> Collections.synchronizedList(new ArrayList<>()));
            synchronized (list) {
                list.add(new MemoryRecord(text, sessionId,
                        metadata != null ? Map.copyOf(metadata) : Map.of()));
            }
        });
    }

    @Override
    public CompletableFuture<List<MemoryRecord>> recall(
            String sessionId, String query, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<MemoryRecord> records = store.get(sessionId);
            if (records == null) {
                return List.<MemoryRecord>of();
            }

            // Take snapshot under per-session lock
            List<MemoryRecord> snapshot;
            synchronized (records) {
                if (records.isEmpty()) return List.of();
                snapshot = new ArrayList<>(records);
            }

            Set<String> queryTokens = TextTokenizer.tokenize(query);
            if (queryTokens.isEmpty()) {
                return List.<MemoryRecord>of();
            }

            // Score each record by token overlap, with recency as tiebreaker
            List<ScoredRecord> scored = new ArrayList<>();
            for (int i = 0; i < snapshot.size(); i++) {
                MemoryRecord rec = snapshot.get(i);
                Set<String> recTokens = TextTokenizer.tokenize(rec.text());
                int overlap = TextTokenizer.intersectionSize(queryTokens, recTokens);
                if (overlap > 0) {
                    scored.add(new ScoredRecord(rec, overlap * OVERLAP_WEIGHT + i));
                }
            }

            scored.sort(Comparator.<ScoredRecord>comparingLong(s -> -s.score));
            return scored.stream()
                    .limit(limit)
                    .map(s -> s.record)
                    .toList();
        });
    }

    @Override
    public CompletableFuture<Void> forget(String sessionId) {
        return CompletableFuture.runAsync(() -> store.remove(sessionId));
    }

    /** Returns the total number of records across all sessions. */
    public int size() {
        int total = 0;
        for (List<MemoryRecord> list : store.values()) {
            synchronized (list) {
                total += list.size();
            }
        }
        return total;
    }

    private record ScoredRecord(MemoryRecord record, long score) {}
}
