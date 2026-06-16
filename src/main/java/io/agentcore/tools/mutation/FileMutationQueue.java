package io.agentcore.tools.mutation;

import io.agentcore.tools.operations.FileOperations;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * Per-file mutual exclusion for concurrent write/edit operations.
 * When tools run in parallel mode, multiple write operations on the same file
 * could race. This class provides per-path lock serialization.
 * <p>
 * Uses Semaphore instead of ReentrantLock because Semaphore has no thread
 * affinity — release() can be called from any thread. This is critical when
 * the CompletableFuture completes on a different thread (e.g. ForkJoinPool)
 * than the one that acquired the permit.
 * <p>
 * The lock is held until the CompletableFuture completes (not when it is returned),
 * ensuring the async operation finishes before the lock is released.
 * <p>
 * Entries are cleaned up after release when no other thread is waiting,
 * preventing unbounded map growth for long-running agents.
 */
public class FileMutationQueue {
    private final ConcurrentHashMap<String, Semaphore> locks = new ConcurrentHashMap<>();

    private Semaphore lockFor(String path) {
        return locks.computeIfAbsent(path, k -> new Semaphore(1));
    }

    /**
     * Release permit and clean up the entry if no other thread is waiting.
     */
    private void releaseAndCleanup(String path, Semaphore sem) {
        sem.release();
        // Remove the entry if it's uncontended to prevent unbounded growth
        if (!sem.hasQueuedThreads()) {
            locks.remove(path, sem);
        }
    }

    public CompletableFuture<String> readLocked(FileOperations ops, String path) {
        Semaphore sem = lockFor(path);
        try {
            sem.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CompletableFuture.failedFuture(e);
        }
        try {
            return ops.read(path, 0, null)
                    .whenComplete((result, error) -> releaseAndCleanup(path, sem));
        } catch (Exception e) {
            releaseAndCleanup(path, sem);
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<Void> writeLocked(FileOperations ops, String path, String content) {
        Semaphore sem = lockFor(path);
        try {
            sem.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CompletableFuture.failedFuture(e);
        }
        try {
            return ops.write(path, content)
                    .whenComplete((result, error) -> releaseAndCleanup(path, sem));
        } catch (Exception e) {
            releaseAndCleanup(path, sem);
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<Boolean> editLocked(FileOperations ops, String path, String oldText, String newText) {
        Semaphore sem = lockFor(path);
        try {
            sem.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CompletableFuture.failedFuture(e);
        }
        try {
            return ops.edit(path, oldText, newText)
                    .whenComplete((result, error) -> releaseAndCleanup(path, sem));
        } catch (Exception e) {
            releaseAndCleanup(path, sem);
            return CompletableFuture.failedFuture(e);
        }
    }
}
