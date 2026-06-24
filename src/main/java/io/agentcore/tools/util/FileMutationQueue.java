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
 * <p>Entries persist for the lifetime of the queue. This avoids TOCTOU races
 * between release and cleanup that could break mutual exclusion.
 */
public final class FileMutationQueue {

    private final ConcurrentHashMap<String, Semaphore> locks = new ConcurrentHashMap<>();

    private Semaphore lockFor(String path) {
        return locks.computeIfAbsent(path, k -> new Semaphore(1));
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
            return CompletableFuture.completedFuture(ops.read(path, 0, null));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        } finally {
            sem.release();
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
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        } finally {
            sem.release();
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
            return CompletableFuture.completedFuture(ops.edit(path, oldText, newText));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        } finally {
            sem.release();
        }
    }
}
