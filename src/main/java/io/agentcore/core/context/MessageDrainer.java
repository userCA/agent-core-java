package io.agentcore.core.context;

import io.agentcore.core.messages.AgentMessage;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Drains pending messages from a queue (steering or follow-up).
 */
@FunctionalInterface
public interface MessageDrainer {
    CompletableFuture<List<AgentMessage>> drain();
}
