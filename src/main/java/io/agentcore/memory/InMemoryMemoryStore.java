package io.agentcore.memory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * In-memory {@link MemoryStore} with token-overlap ranking.
 *
 * <p>Records are stored per session in a {@link ConcurrentHashMap}.
 * Recall ranks results by the number of shared tokens between the
 * query and each record, with recency as a tiebreaker.
 */
public class InMemoryMemoryStore implements MemoryStore {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\w+");

    private final ConcurrentHashMap<String, List<MemoryRecord>> store = new ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    @Override
    public CompletableFuture<Void> remember(
            String sessionId, String text, Map<String, Object> metadata) {
        return CompletableFuture.runAsync(() -> {
            lock.lock();
            try {
                store.computeIfAbsent(sessionId, k -> new ArrayList<>())
                        .add(new MemoryRecord(text, sessionId,
                                metadata != null ? Map.copyOf(metadata) : Map.of()));
            } finally {
                lock.unlock();
            }
        });
    }

    @Override
    public CompletableFuture<List<MemoryRecord>> recall(
            String sessionId, String query, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            lock.lock();
            try {
                List<MemoryRecord> records = store.get(sessionId);
                if (records == null || records.isEmpty()) {
                    return List.<MemoryRecord>of();
                }

                // Copy snapshot to release lock quickly
                List<MemoryRecord> snapshot = new ArrayList<>(records);

                Set<String> queryTokens = tokenize(query);
                if (queryTokens.isEmpty()) {
                    return List.<MemoryRecord>of();
                }

                // Score each record by token overlap, with recency as tiebreaker
                List<ScoredRecord> scored = new ArrayList<>();
                for (int i = 0; i < snapshot.size(); i++) {
                    MemoryRecord rec = snapshot.get(i);
                    Set<String> recTokens = tokenize(rec.text());
                    int overlap = intersectionSize(queryTokens, recTokens);
                    if (overlap > 0) {
                        // Score: overlap * 1000 + index (recency tiebreaker)
                        scored.add(new ScoredRecord(rec, overlap * 1000L + i));
                    }
                }

                scored.sort(Comparator.<ScoredRecord>comparingLong(s -> -s.score));
                return scored.stream()
                        .limit(limit)
                        .map(s -> s.record)
                        .collect(Collectors.toList());
            } finally {
                lock.unlock();
            }
        });
    }

    @Override
    public CompletableFuture<Void> forget(String sessionId) {
        return CompletableFuture.runAsync(() -> {
            lock.lock();
            try {
                store.remove(sessionId);
            } finally {
                lock.unlock();
            }
        });
    }

    /** Returns the total number of records across all sessions. */
    public int size() {
        return store.values().stream().mapToInt(List::size).sum();
    }

    // ── Internals ──

    private static Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) return Set.of();
        return TOKEN_PATTERN.matcher(text.toLowerCase())
                .results()
                .map(mr -> mr.group())
                .collect(Collectors.toSet());
    }

    private static int intersectionSize(Set<String> a, Set<String> b) {
        Set<String> smaller = a.size() <= b.size() ? a : b;
        Set<String> larger = a.size() <= b.size() ? b : a;
        int count = 0;
        for (String token : smaller) {
            if (larger.contains(token)) count++;
        }
        return count;
    }

    private record ScoredRecord(MemoryRecord record, long score) {}
}
