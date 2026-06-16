package io.agentcore.core.humaninput;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Synchronization primitive for Human-in-the-Loop (HITL).
 * <p>
 * When a tool raises {@link RequiresHumanInput}, the tool runner calls
 * {@link #requireInput(String)} which creates a future keyed by toolCallId.
 * An external caller resolves it via {@link #provideInput(String, Map)}.
 */
public class HumanInputGate {
    private final ConcurrentHashMap<String, CompletableFuture<Map<String, Object>>> futures =
            new ConcurrentHashMap<>();
    private volatile boolean cancelled = false;

    /**
     * Create a future for the given tool call id. The caller awaits it to pause execution.
     * If the gate has been cancelled, returns an already-cancelled future.
     */
    public CompletableFuture<Map<String, Object>> requireInput(String toolCallId) {
        if (cancelled) {
            var future = new CompletableFuture<Map<String, Object>>();
            future.cancel(true);
            return future;
        }
        var future = new CompletableFuture<Map<String, Object>>();
        futures.put(toolCallId, future);
        return future;
    }

    /**
     * Resolve the future for the given tool call id with the provided values.
     *
     * @return true if a pending future was found and resolved
     */
    public boolean provideInput(String toolCallId, Map<String, Object> values) {
        var future = futures.remove(toolCallId);
        if (future != null) {
            future.complete(values);
            return true;
        }
        return false;
    }

    /**
     * Check if there is a pending future for the given tool call id.
     */
    public boolean isWaiting(String toolCallId) {
        var future = futures.get(toolCallId);
        return future != null && !future.isDone();
    }

    /**
     * Cancel all pending futures and prevent new ones from being created.
     * Uses atomic swap to eliminate the race window between forEach and clear.
     */
    public void cancelAll() {
        cancelled = true;
        // Snapshot the current futures so new requireInput calls see cancelled=true
        // and get pre-cancelled futures. Any future added between snapshot and
        // iteration will also be caught by the re-check in requireInput.
        Map<String, CompletableFuture<Map<String, Object>>> snapshot =
                new HashMap<>(futures);
        futures.keySet().removeAll(snapshot.keySet());
        snapshot.values().forEach(f -> f.cancel(true));
    }

    /**
     * Reset the cancelled state, allowing new input requests.
     */
    public void reset() {
        cancelled = false;
    }
}
