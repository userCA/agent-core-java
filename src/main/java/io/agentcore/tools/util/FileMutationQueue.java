package io.agentcore.tools.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * Per-file mutual exclusion for concurrent write/edit operations.
 *
 * <p>When tools run in parallel mode, multiple write operations on the same
 * file could race. This class provides per-path lock serialization using
 * {@link Semaphore}, which has no thread affinity — {@code release()} can
 * be called from any thread, unlike {@code ReentrantLock}.
 *
 * <p>Entries are cleaned up after release when no other thread is waiting,
 * preventing unbounded map growth for long-running agents.
 */
public final class FileMutationQueue {

    private final ConcurrentHashMap<String, Semaphore> locks = new ConcurrentHashMap<>();

    private Semaphore lockFor(String path) {
        return locks.computeIfAbsent(path, k -> new Semaphore(1));
    }

    private void releaseAndCleanup(String path, Semaphore sem) {
        sem.release();
        if (!sem.hasQueuedThreads()) {
            locks.remove(path, sem);
        }
    }

    /**
     * Read with per-file lock.
     */
    public CompletableFuture<String> readLocked(FileOperations ops, String path) {
        Semaphore sem = lockFor(path);
        try {
            sem.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CompletableFuture.failedFuture(e);
        }
        try {
            String result = ops.read(path, 0, null);
            releaseAndCleanup(path, sem);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            releaseAndCleanup(path, sem);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Write with per-file lock.
     */
    public CompletableFuture<Void> writeLocked(FileOperations ops, String path, String content) {
        Semaphore sem = lockFor(path);
        try {
            sem.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CompletableFuture.failedFuture(e);
        }
        try {
            ops.write(path, content);
            releaseAndCleanup(path, sem);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            releaseAndCleanup(path, sem);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Edit with per-file lock.
     */
    public CompletableFuture<Boolean> editLocked(FileOperations ops, String path,
                                                  String oldText, String newText) {
        Semaphore sem = lockFor(path);
        try {
            sem.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CompletableFuture.failedFuture(e);
        }
        try {
            boolean result = ops.edit(path, oldText, newText);
            releaseAndCleanup(path, sem);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            releaseAndCleanup(path, sem);
            return CompletableFuture.failedFuture(e);
        }
    }
}
