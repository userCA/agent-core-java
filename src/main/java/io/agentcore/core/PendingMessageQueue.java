package io.agentcore.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Internal pending-message buffer for steering and follow-up injection.
 *
 * <p>Mirrors Python {@code agent_core/core/queue.py} PendingMessageQueue.
 * Supports two drain modes:
 * <ul>
 *   <li>{@code "all"} — drain all pending messages at once</li>
 *   <li>{@code "one_at_a_time"} — drain only the oldest message per call</li>
 * </ul>
 */
public final class PendingMessageQueue {

    public static final String MODE_ALL = "all";
    public static final String MODE_ONE_AT_A_TIME = "one_at_a_time";

    private volatile String mode;
    private final List<Message> items = new ArrayList<>();

    public PendingMessageQueue() {
        this(MODE_ONE_AT_A_TIME);
    }

    public PendingMessageQueue(String mode) {
        this.mode = mode;
    }

    public String mode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    /**
     * Add a message to the queue.
     */
    public synchronized void enqueue(Message message) {
        items.add(message);
    }

    /**
     * Returns true if there are pending messages.
     */
    public synchronized boolean hasItems() {
        return !items.isEmpty();
    }

    /**
     * Drain messages according to the current mode.
     * <ul>
     *   <li>{@code "all"} — returns all pending messages and clears the queue</li>
     *   <li>{@code "one_at_a_time"} — returns only the oldest message</li>
     * </ul>
     */
    public synchronized List<Message> drain() {
        if (items.isEmpty()) return List.of();
        if (MODE_ALL.equals(mode)) {
            List<Message> drained = new ArrayList<>(items);
            items.clear();
            return drained;
        }
        // one_at_a_time
        return List.of(items.removeFirst());
    }

    /**
     * Clear all pending messages.
     */
    public synchronized void clear() {
        items.clear();
    }
}
