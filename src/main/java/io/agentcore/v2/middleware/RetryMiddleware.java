package io.agentcore.v2.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ReasoningInput;
import io.agentcore.v2.agent.ClaudeCodeConfig;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Implements exponential-backoff retry for the reasoning (model call) phase.
 *
 * <p>Replicates the retry behaviour from {@code AgentLoop.run()}:
 * <ul>
 *   <li>First attempt: events streamed in real-time</li>
 *   <li>Retry: events buffered then replayed</li>
 *   <li>Backoff: {@code min(base * 2^(n-1), maxDelay)} with jitter</li>
 *   <li>Context overflow: triggers compaction callback, then retries</li>
 *   <li>Non-retryable errors propagate immediately</li>
 * </ul>
 */
public final class RetryMiddleware implements MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(RetryMiddleware.class);

    private static final String RETRY_COUNT_KEY = "claude_code.retry_count";
    /** Set by this middleware when context overflow is detected. Read by CompactionMiddleware. */
    public static final String COMPACT_REQUESTED_KEY = "claude_code.compact_requested";

    private final int maxRetries;
    private final double baseDelay;
    private final double maxDelay;

    public RetryMiddleware(ClaudeCodeConfig config) {
        this.maxRetries = config.maxRetries();
        this.baseDelay = config.retryBaseDelay();
        this.maxDelay = config.retryMaxDelay();
    }

    @Override
    public Flux<AgentEvent> onReasoning(
            Agent agent, RuntimeContext ctx, ReasoningInput input,
            Function<ReasoningInput, Flux<AgentEvent>> next) {

        return Flux.defer(() -> attempt(agent, ctx, input, next, 0));
    }

    private Flux<AgentEvent> attempt(
            Agent agent, RuntimeContext ctx, ReasoningInput input,
            Function<ReasoningInput, Flux<AgentEvent>> next, int retryCount) {

        ctx.put(RETRY_COUNT_KEY, retryCount);

        if (retryCount == 0) {
            // First attempt: stream in real-time
            return next.apply(input)
                    .onErrorResume(error ->
                            handleRetry(agent, ctx, input, next, retryCount, error));
        } else {
            // Retry: buffer events then replay
            return next.apply(input)
                    .collectList()
                    .flatMapMany(buffered -> Flux.fromIterable(buffered))
                    .onErrorResume(error ->
                            handleRetry(agent, ctx, input, next, retryCount, error));
        }
    }

    private Flux<AgentEvent> handleRetry(
            Agent agent, RuntimeContext ctx, ReasoningInput input,
            Function<ReasoningInput, Flux<AgentEvent>> next, int retryCount,
            Throwable error) {

        boolean retryable = isRetryable(error);
        boolean overflow = isContextOverflow(error);

        if (!retryable && !overflow) {
            return Flux.error(error);
        }

        if (retryCount >= maxRetries) {
            log.warn("Max retries ({}) reached, giving up", maxRetries);
            return Flux.error(error);
        }

        // Signal CompactionMiddleware to compact before next reasoning attempt
        if (overflow) {
            ctx.put(COMPACT_REQUESTED_KEY, true);
            log.info("Context overflow detected, signalling compaction before retry");
        }

        double delay = computeBackoff(retryCount);
        log.info("Retrying reasoning (attempt {}/{}), backoff {}s: {}",
                retryCount + 1, maxRetries, String.format("%.1f", delay),
                error.getMessage());

        return Mono.delay(Duration.ofMillis((long) (delay * 1000)))
                .thenMany(Flux.defer(() ->
                        attempt(agent, ctx, input, next, retryCount + 1)));
    }

    /**
     * Compute exponential backoff with jitter.
     * Formula: min(baseDelay * 2^(n), maxDelay) * random(0.5, 1.0)
     */
    public double computeBackoff(int retryCount) {
        double exponential = baseDelay * Math.pow(2, retryCount);
        double capped = Math.min(exponential, maxDelay);
        double jitter = 0.5 + ThreadLocalRandom.current().nextDouble() * 0.5;
        return capped * jitter;
    }

    /**
     * Checks whether the error is retryable.
     * Maps to the same conditions as the {@code retryableError} flag
     * in agent-core-java's {@code AssistantMessage}.
     */
    private static boolean isRetryable(Throwable error) {
        String msg = error.getMessage();
        if (msg == null) return false;
        String lower = msg.toLowerCase();
        return lower.contains("retryable")
                || lower.contains("rate limit")
                || lower.contains("too many requests")
                || lower.contains("server error")
                || lower.contains("service unavailable")
                || lower.contains("timeout")
                || lower.contains("internal error")
                || lower.contains("overloaded");
    }

    /**
     * Checks whether the error is a context-overflow error.
     * Maps to the {@code overflowError} flag in agent-core-java.
     */
    private static boolean isContextOverflow(Throwable error) {
        String msg = error.getMessage();
        if (msg == null) return false;
        String lower = msg.toLowerCase();
        return lower.contains("context length")
                || lower.contains("too many tokens")
                || lower.contains("too long")
                || lower.contains("context limit")
                || lower.contains("prompt is too long")
                || lower.contains("max_tokens")
                || lower.contains("context_length_exceeded")
                || lower.contains("maximum context length");
    }
}
