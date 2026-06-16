package io.agentcore.core.queue;

import io.agentcore.core.content.TextContent;
import io.agentcore.core.messages.AgentMessage;
import io.agentcore.core.messages.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PendingMessageQueueTest {

    private UserMessage makeMsg(String text) {
        return new UserMessage(List.of(new TextContent(text)), 0);
    }

    @Test
    void defaultMode_isOneAtATime() {
        var queue = new PendingMessageQueue();
        assertEquals(QueueMode.ONE_AT_A_TIME, queue.mode());
    }

    @Test
    void oneAtATime_drain_returnsSingleMessage() {
        var queue = new PendingMessageQueue(QueueMode.ONE_AT_A_TIME);
        queue.enqueue(makeMsg("first"));
        queue.enqueue(makeMsg("second"));
        queue.enqueue(makeMsg("third"));

        var drained = queue.drain();
        assertEquals(1, drained.size());

        // Next drain returns the next one
        drained = queue.drain();
        assertEquals(1, drained.size());

        drained = queue.drain();
        assertEquals(1, drained.size());

        // Queue is now empty
        drained = queue.drain();
        assertTrue(drained.isEmpty());
    }

    @Test
    void allMode_drain_returnsAllMessages() {
        var queue = new PendingMessageQueue(QueueMode.ALL);
        queue.enqueue(makeMsg("first"));
        queue.enqueue(makeMsg("second"));
        queue.enqueue(makeMsg("third"));

        var drained = queue.drain();
        assertEquals(3, drained.size());

        // Queue is empty after draining all
        drained = queue.drain();
        assertTrue(drained.isEmpty());
    }

    @Test
    void hasItems_returnsTrueWhenNotEmpty() {
        var queue = new PendingMessageQueue();
        assertFalse(queue.hasItems());

        queue.enqueue(makeMsg("msg"));
        assertTrue(queue.hasItems());

        queue.drain();
        assertFalse(queue.hasItems());
    }

    @Test
    void clear_removesAllItems() {
        var queue = new PendingMessageQueue(QueueMode.ALL);
        queue.enqueue(makeMsg("a"));
        queue.enqueue(makeMsg("b"));
        queue.clear();

        assertFalse(queue.hasItems());
        assertTrue(queue.drain().isEmpty());
    }

    @Test
    void drain_emptyQueue_returnsEmptyList() {
        var queue = new PendingMessageQueue(QueueMode.ALL);
        assertTrue(queue.drain().isEmpty());

        var queue2 = new PendingMessageQueue(QueueMode.ONE_AT_A_TIME);
        assertTrue(queue2.drain().isEmpty());
    }
}
