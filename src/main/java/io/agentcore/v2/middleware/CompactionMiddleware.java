package io.agentcore.v2.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ReasoningInput;
import io.agentscope.core.message.Msg;
import io.agentscope.core.state.AgentState;

import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/**
 * Detects context-overflow errors and triggers compaction before retrying.
 *
 * <p>When the model returns a context-overflow error (detected by
 * {@link RetryMiddleware}), this middleware prompts the agent's state
 * to compact its message history before the next reasoning attempt.
 *
 * <p>This replicates the compaction-triggered-by-overflow behaviour from
 * agent-core-java's {@code AgentLoop} retry loop.
 */
public final class CompactionMiddleware implements MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(CompactionMiddleware.class);

    /** Threshold ratio: compact when estimated tokens >= contextWindow * threshold. */
    private final double threshold;

    /** Number of recent messages to keep (not compacted). */
    private final int keepRecent;

    /** Context window size for the model (default 200K). */
    private final int contextWindow;

    public CompactionMiddleware(double threshold, int keepRecent) {
        this(threshold, keepRecent, 200_000);
    }

    public CompactionMiddleware(double threshold, int keepRecent, int contextWindow) {
        this.threshold = threshold;
        this.keepRecent = keepRecent;
        this.contextWindow = contextWindow;
    }

    // ── onReasoning: check if compaction is needed ──

    @Override
    public Flux<AgentEvent> onReasoning(
            Agent agent, RuntimeContext ctx, ReasoningInput input,
            Function<ReasoningInput, Flux<AgentEvent>> next) {

        AgentState state = ctx.getAgentState();
        if (state == null) {
            return next.apply(input);
        }

        List<Msg> messages = state.getContext();
        boolean forceCompact = Boolean.TRUE.equals(
                ctx.get(RetryMiddleware.COMPACT_REQUESTED_KEY));

        if (forceCompact || shouldCompact(messages, contextWindow)) {
            log.info("Compaction triggered ({})",
                    forceCompact ? "overflow signal" : "token threshold");
            compactInPlace(state, messages);
            ctx.put(RetryMiddleware.COMPACT_REQUESTED_KEY, null); // clear signal
        }

        return next.apply(input);
    }

    /**
     * Rough token estimation: {@code text.length / 4 + 20} overhead per message.
     * Matches the estimation logic in agent-core-java's {@code CompactionStrategies}.
     */
    public boolean shouldCompact(List<Msg> messages, int contextWindow) {
        long estimated = messages.stream()
                .mapToLong(CompactionMiddleware::estimateTokens)
                .sum();
        return estimated >= (long) (contextWindow * threshold);
    }

    /**
     * Simple compaction: keep the first system message (if any) and the
     * last {@code keepRecent} messages; discard the middle.
     *
     * <p>Phase 4 will replace this with HarnessAgent's
     * {@code CompactionMiddleware} for LLM-based summarisation.
     */
    private void compactInPlace(AgentState state, List<Msg> messages) {
        if (messages.size() <= keepRecent) return;

        int cutoff = Math.max(0, messages.size() - keepRecent);

        // Estimate tokens before
        long before = messages.stream()
                .mapToLong(CompactionMiddleware::estimateTokens)
                .sum();

        // Find first system message to preserve
        Msg systemMsg = null;
        for (Msg m : messages.subList(0, cutoff)) {
            if ("system".equalsIgnoreCase(String.valueOf(m.getRole()))) {
                systemMsg = m;
                break;
            }
        }

        List<Msg> compacted = new java.util.ArrayList<>();
        if (systemMsg != null) {
            compacted.add(systemMsg);
        }
        compacted.addAll(messages.subList(cutoff, messages.size()));

        messages.clear();
        messages.addAll(compacted);

        long after = compacted.stream()
                .mapToLong(CompactionMiddleware::estimateTokens)
                .sum();

        log.info("Compacted: {} → {} estimated tokens (kept {} messages)",
                before, after, compacted.size());
    }

    static long estimateTokens(Msg msg) {
        if (msg == null) return 0;
        long tokens = 20; // base overhead
        Object content = msg.getContent();
        if (content instanceof String s) {
            tokens += s.length() / 4;
        } else if (content instanceof List<?> blocks) {
            for (Object block : blocks) {
                if (block instanceof io.agentscope.core.message.TextBlock tb
                        && tb.getText() != null) {
                    tokens += tb.getText().length() / 4;
                }
            }
        }
        return tokens;
    }
}
