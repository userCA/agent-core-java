package io.agentcore.core.queue;

import io.agentcore.core.messages.AgentMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Queue for steering / follow-up messages with two drain modes.
 * <ul>
 *   <li>{@code ALL} — drain returns all queued messages</li>
 *   <li>{@code ONE_AT_A_TIME} — drain returns only the first message</li>
 * </ul>
 * Bounded: drops oldest messages when capacity is exceeded.
 */
public class PendingMessageQueue {
    private static final int DEFAULT_MAX_SIZE = 100;

    private final QueueMode mode;
    private final int maxSize;
    private final ConcurrentLinkedQueue<AgentMessage> items = new ConcurrentLinkedQueue<>();

    public PendingMessageQueue() {
        this(QueueMode.ONE_AT_A_TIME, DEFAULT_MAX_SIZE);
    }

    public PendingMessageQueue(QueueMode mode) {
        this(mode, DEFAULT_MAX_SIZE);
    }

    public PendingMessageQueue(QueueMode mode, int maxSize) {
        this.mode = mode;
        this.maxSize = maxSize > 0 ? maxSize : DEFAULT_MAX_SIZE;
    }

    public void enqueue(AgentMessage message) {
        items.add(message);
        // Drop oldest if capacity exceeded
        while (items.size() > maxSize) {
            items.poll();
        }
    }

    public boolean hasItems() {
        return !items.isEmpty();
    }

    public List<AgentMessage> drain() {
        List<AgentMessage> result = new ArrayList<>();
        if (mode == QueueMode.ALL) {
            AgentMessage msg;
            while ((msg = items.poll()) != null) {
                result.add(msg);
            }
        } else {
            AgentMessage msg = items.poll();
            if (msg != null) {
                result.add(msg);
            }
        }
        return result;
    }

    public void clear() {
        items.clear();
    }

    public QueueMode mode() {
        return mode;
    }
}
