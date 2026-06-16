package io.agentcore.compaction;

import io.agentcore.core.messages.AgentMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public class LLMSummaryCompactor implements Compactor {
    private static final Logger log = LoggerFactory.getLogger(LLMSummaryCompactor.class);

    private final Function<List<AgentMessage>, CompletableFuture<String>> summarizeFn;
    private final double threshold;
    private final int keepRecent;

    public LLMSummaryCompactor(Function<List<AgentMessage>, CompletableFuture<String>> summarizeFn,
                                double threshold, int keepRecent) {
        this.summarizeFn = summarizeFn;
        this.threshold = threshold;
        this.keepRecent = keepRecent;
    }

    @Override
    public boolean shouldCompact(List<AgentMessage> messages, int contextWindow) {
        return CompactionStrategies.shouldCompactThreshold(messages, contextWindow, threshold);
    }

    @Override
    public CompletableFuture<CompactionResult> compact(List<AgentMessage> messages, String reason,
                                                        String instructions, AtomicBoolean signal) {
        int tokensBefore = CompactionStrategies.totalTokens(messages);

        // Early abort check before starting expensive summarization
        if (signal != null && signal.get()) {
            return CompletableFuture.completedFuture(
                    new CompactionResult("[aborted]", "", tokensBefore, tokensBefore, messages.size()));
        }

        int cutoff = Math.max(0, messages.size() - keepRecent);
        List<AgentMessage> toCompact = new ArrayList<>(messages.subList(0, cutoff));
        List<AgentMessage> toKeep = new ArrayList<>(messages.subList(cutoff, messages.size()));

        return summarizeFn.apply(toCompact).thenApply(summary -> {
            if (signal != null && signal.get()) {
                return new CompactionResult("[aborted]", "", tokensBefore, tokensBefore, toKeep.size());
            }
            int tokensAfter = CompactionStrategies.totalTokens(toKeep);
            String firstKeptId = toKeep.isEmpty() ? "" : String.valueOf(cutoff);
            return new CompactionResult(summary, firstKeptId, tokensBefore, tokensAfter, toKeep.size());
        }).exceptionally(e -> {
            log.warn("Compaction summarize failed: {}", e.getMessage());
            // Return a no-op result so the caller can handle gracefully
            return new CompactionResult("", "", tokensBefore, tokensBefore, messages.size());
        });
    }
}
