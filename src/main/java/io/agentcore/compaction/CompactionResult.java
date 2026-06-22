package io.agentcore.compaction;

/**
 * Result of a compaction operation.
 *
 * <p>Mirrors Python {@code agent_core/compaction/compactor.py} CompactionResult.
 *
 * @param summary            generated summary of compacted messages
 * @param firstKeptEntryId   ID of the first message kept (cutoff boundary)
 * @param tokensBefore       estimated tokens before compaction
 * @param tokensAfter        estimated tokens after compaction (kept + summary)
 * @param keptCount          number of messages kept (not compacted)
 */
public record CompactionResult(
        String summary,
        String firstKeptEntryId,
        int tokensBefore,
        int tokensAfter,
        int keptCount
) {
    public CompactionResult {
        if (summary == null) summary = "";
        if (firstKeptEntryId == null) firstKeptEntryId = "";
    }

    /**
     * Create an empty compaction result.
     */
    public static CompactionResult empty() {
        return new CompactionResult("", "", 0, 0, 0);
    }
}
