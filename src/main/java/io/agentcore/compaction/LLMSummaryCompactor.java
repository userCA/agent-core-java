package io.agentcore.compaction;

import io.agentcore.core.Message;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Default compactor: keep recent N messages, summarize older ones via a pluggable function.
 *
 * <p>Mirrors Python {@code agent_core/compaction/compactor.py} LLMSummaryCompactor.
 */
public class LLMSummaryCompactor implements Compactor {

    /**
     * Function that takes a list of messages and returns a summary string.
     */
    private final Function<List<Message>, String> summarizeFn;
    private final double threshold;
    private final int keepRecent;

    /**
     * @param summarizeFn  function to summarize messages (can be null for no-op summarization)
     * @param threshold    fraction of context window that triggers compaction (default 0.8)
     * @param keepRecent   number of recent messages to keep (default 4)
     */
    public LLMSummaryCompactor(Function<List<Message>, String> summarizeFn, double threshold, int keepRecent) {
        this.summarizeFn = summarizeFn;
        this.threshold = threshold;
        this.keepRecent = keepRecent;
    }

    public LLMSummaryCompactor() {
        this(null, 0.8, 4);
    }

    @Override
    public boolean shouldCompact(List<Message> messages, int contextWindow) {
        int tokens = Compactor.totalTokens(messages);
        return tokens >= contextWindow * threshold;
    }

    @Override
    public CompactionResult compact(
            List<Message> messages,
            String reason,
            String instructions,
            AtomicBoolean signal) {

        if (messages == null || messages.isEmpty()) {
            return CompactionResult.empty();
        }

        int tokensBefore = Compactor.totalTokens(messages);
        int cutoff = Math.max(0, messages.size() - keepRecent);
        List<Message> toCompact = messages.subList(0, cutoff);
        List<Message> toKeep = messages.subList(cutoff, messages.size());

        String summary = "";
        if (!toCompact.isEmpty() && summarizeFn != null) {
            if (signal != null && signal.get()) {
                summary = "[aborted]";
            } else {
                summary = summarizeFn.apply(toCompact);
            }
        }

        // Generate an ID for the first kept entry
        String firstKeptId = "";
        if (!toKeep.isEmpty()) {
            firstKeptId = "msg-" + UUID.randomUUID();
        }

        int tokensAfter = Compactor.totalTokens(toKeep) + summary.length() / 4;

        return new CompactionResult(
                summary,
                firstKeptId,
                tokensBefore,
                tokensAfter,
                toKeep.size()
        );
    }
}
