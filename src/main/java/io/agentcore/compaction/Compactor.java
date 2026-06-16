package io.agentcore.compaction;

import io.agentcore.core.messages.AgentMessage;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public interface Compactor {
    boolean shouldCompact(List<AgentMessage> messages, int contextWindow);
    CompletableFuture<CompactionResult> compact(List<AgentMessage> messages, String reason,
                                                 String instructions, AtomicBoolean signal);

    record CompactionResult(String summary, String firstKeptEntryId,
                            int tokensBefore, int tokensAfter, int keptCount) {}
}
