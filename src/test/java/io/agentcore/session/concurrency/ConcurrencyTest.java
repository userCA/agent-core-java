package io.agentcore.session.concurrency;

import io.agentcore.session.jsonl.JsonlStore;
import io.agentcore.session.memory.InMemoryStore;
import io.agentcore.session.store.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrency tests for InMemoryStore and JsonlStore.
 */
class ConcurrencyTest {

    @TempDir
    Path tempDir;

    private static SessionEntry msgEntry(String content) {
        return new SessionEntry.MessageEntry(
                Map.of("role", "user", "content", content), null, UUID.randomUUID().toString());
    }

    // === InMemoryStore Concurrent Append (CONC-05) ===

    @Test
    void inMemoryStore_concurrentAppend_noDataLoss() throws Exception {
        var store = new InMemoryStore();
        String sessionId = "concurrent-test";
        store.createSession(sessionId, new SessionHeader("id", "ts", "cwd")).get();

        int threadCount = 10;
        int entriesPerThread = 50;
        var executor = Executors.newFixedThreadPool(threadCount);
        var barrier = new CyclicBarrier(threadCount);

        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                futures.add(CompletableFuture.runAsync(() -> {
                    try { barrier.await(); } catch (Exception ignored) {}
                    for (int i = 0; i < entriesPerThread; i++) {
                        store.appendEntry(sessionId, msgEntry("msg-" + threadId + "-" + i)).join();
                    }
                }, executor));
            }

            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(30, TimeUnit.SECONDS);

            var snapshot = store.loadSession(sessionId).get();
            assertEquals(threadCount * entriesPerThread, snapshot.entries().size(),
                    "All entries should be present after concurrent append");
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void inMemoryStore_concurrentReadAndAppend_noException() throws Exception {
        var store = new InMemoryStore();
        String sessionId = "read-write-test";
        store.createSession(sessionId, new SessionHeader("id", "ts", "cwd")).get();

        int iterations = 100;
        var executor = Executors.newFixedThreadPool(4);

        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (int i = 0; i < iterations; i++) {
                final int idx = i;
                futures.add(CompletableFuture.runAsync(() ->
                        store.appendEntry(sessionId, msgEntry("msg-" + idx)).join(), executor));
            }
            for (int i = 0; i < iterations; i++) {
                futures.add(CompletableFuture.runAsync(() ->
                        store.loadSession(sessionId).join(), executor));
            }

            assertDoesNotThrow(() ->
                    CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                            .get(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdown();
        }
    }

    // === JsonlStore Concurrent Writes (CONC-06) ===

    @Test
    void jsonlStore_concurrentAppend_noInterleaving() throws Exception {
        var store = new JsonlStore(tempDir.toString());
        String sessionId = "concurrent-jsonl";
        store.createSession(sessionId, new SessionHeader("id", "ts", "cwd")).get();

        int threadCount = 5;
        int entriesPerThread = 20;
        var executor = Executors.newFixedThreadPool(threadCount);
        var barrier = new CyclicBarrier(threadCount);

        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                futures.add(CompletableFuture.runAsync(() -> {
                    try { barrier.await(); } catch (Exception ignored) {}
                    for (int i = 0; i < entriesPerThread; i++) {
                        store.appendEntry(sessionId, msgEntry("msg-" + threadId + "-" + i)).join();
                    }
                }, executor));
            }

            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(30, TimeUnit.SECONDS);

            var snapshot = store.loadSession(sessionId).get();
            assertEquals(threadCount * entriesPerThread, snapshot.entries().size(),
                    "All entries should be present without interleaving");
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void jsonlStore_differentSessions_noConflict() throws Exception {
        var store = new JsonlStore(tempDir.toString());
        var executor = Executors.newFixedThreadPool(4);

        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (int s = 0; s < 4; s++) {
                final String sid = "session-" + s;
                futures.add(CompletableFuture.runAsync(() -> {
                    store.createSession(sid, new SessionHeader("id", "ts", "cwd")).join();
                    for (int i = 0; i < 10; i++) {
                        store.appendEntry(sid, msgEntry("msg-" + i)).join();
                    }
                }, executor));
            }

            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(30, TimeUnit.SECONDS);

            for (int s = 0; s < 4; s++) {
                var snapshot = store.loadSession("session-" + s).get();
                assertEquals(10, snapshot.entries().size(),
                        "Session " + s + " should have 10 entries");
            }
        } finally {
            executor.shutdown();
        }
    }
}
