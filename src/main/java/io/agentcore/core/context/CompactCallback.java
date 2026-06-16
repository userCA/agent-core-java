package io.agentcore.core.context;

import io.agentcore.core.messages.AgentMessage;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Callback to trigger context compaction (e.g. on overflow).
 * Returns true if compaction was performed.
 */
@FunctionalInterface
public interface CompactCallback {
    CompletableFuture<Boolean> compact(List<AgentMessage> messages);
}
