package io.agentcore.agent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import io.agentcore.model.Message;
import io.agentcore.model.Content;

/**
 * Internal pending-message buffer for steering and follow-up injection.
 *
 * <p>Mirrors Python {@code agent_core/core/queue.py} PendingMessageQueue.
 * Supports two drain modes:
 * <ul>
 *   <li>{@link DrainMode#ALL} — drain all pending messages at once</li>
 *   <li>{@link DrainMode#ONE_AT_A_TIME} — drain only the oldest message per call</li>
 * </ul>
 */
public final class PendingMessageQueue {

    /**
     * Drain mode for the queue.
     */
    public enum DrainMode {
        /** Drain all pending messages at once. */
        ALL,
        /** Drain only the oldest message per call. */
        ONE_AT_A_TIME;

        /**
         * Parse a string value (backward-compatible with "all" / "one_at_a_time").
         */
        public static DrainMode fromString(String value) {
            if (value == null) return ONE_AT_A_TIME;
            return switch (value.toLowerCase()) {
                case "all" -> ALL;
                case "one_at_a_time" -> ONE_AT_A_TIME;
                default -> ONE_AT_A_TIME;
            };
        }
    }

    private volatile DrainMode mode;
    private final Deque<Message> items = new ArrayDeque<>();

    public PendingMessageQueue() {
        this(DrainMode.ONE_AT_A_TIME);
    }

    public PendingMessageQueue(DrainMode mode) {
        this.mode = mode != null ? mode : DrainMode.ONE_AT_A_TIME;
    }

    /**
     * Backward-compatible string constructor.
     * @deprecated Use {@link #PendingMessageQueue(DrainMode)} instead.
     */
    @Deprecated
    public PendingMessageQueue(String mode) {
        this(DrainMode.fromString(mode));
    }

    public DrainMode mode() { return mode; }
    public void setMode(DrainMode mode) { this.mode = mode; }

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
     *   <li>{@link DrainMode#ALL} — returns all pending messages and clears the queue</li>
     *   <li>{@link DrainMode#ONE_AT_A_TIME} — returns only the oldest message</li>
     * </ul>
     */
    public synchronized List<Message> drain() {
        if (items.isEmpty()) return List.of();
        if (mode == DrainMode.ALL) {
            List<Message> drained = new ArrayList<>(items);
            items.clear();
            return drained;
        }
        // ONE_AT_A_TIME — O(1) removal from ArrayDeque
        return List.of(items.removeFirst());
    }

    /**
     * Clear all pending messages.
     */
    public synchronized void clear() {
        items.clear();
    }
}
