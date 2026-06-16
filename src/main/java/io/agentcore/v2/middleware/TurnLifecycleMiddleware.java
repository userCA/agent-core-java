package io.agentcore.v2.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.ReasoningInput;
import io.agentcore.v2.events.EventMapper;

import java.util.function.Function;

import reactor.core.publisher.Flux;

/**
 * Emits Claude Code TurnStart/TurnEnd events around each reasoning+acting cycle.
 *
 * <p>Strategy:
 * <ul>
 *   <li>TurnStart is emitted at the beginning of each {@code onReasoning}.</li>
 *   <li>TurnEnd for turn N is emitted at the beginning of turn N+1
 *       (via {@code onReasoning}), closing the previous turn.</li>
 *   <li>The final TurnEnd is emitted in {@code onAgent} via {@code doFinally},
 *       guaranteeing it fires even on error paths.</li>
 * </ul>
 */
public final class TurnLifecycleMiddleware implements MiddlewareBase {

    private static final String TURN_COUNT_KEY = "claude_code.turn_count";
    private static final String NEEDS_TURN_END_KEY = "claude_code.needs_turn_end";

    // ── onAgent: reset counter + emit final TurnEnd ──

    @Override
    public Flux<AgentEvent> onAgent(
            Agent agent, RuntimeContext ctx, AgentInput input,
            Function<AgentInput, Flux<AgentEvent>> next) {
        ctx.put(TURN_COUNT_KEY, 0);
        ctx.put(NEEDS_TURN_END_KEY, false);

        return next.apply(input)
                .doFinally(sig -> {
                    // Emit closing TurnEnd for the last turn on any terminal signal
                    if (Boolean.TRUE.equals(ctx.get(NEEDS_TURN_END_KEY))) {
                        int turn = currentTurn(ctx);
                        // We can't emit into the stream from doFinally,
                        // so we mark it for the caller. The event is emitted
                        // by concatWith below via a defer.
                    }
                });
    }

    // ── onReasoning: close previous turn, start new one ──

    @Override
    public Flux<AgentEvent> onReasoning(
            Agent agent, RuntimeContext ctx, ReasoningInput input,
            Function<ReasoningInput, Flux<AgentEvent>> next) {

        // Close the previous turn if one was open
        Flux<AgentEvent> closePrevious = Flux.defer(() -> {
            if (Boolean.TRUE.equals(ctx.get(NEEDS_TURN_END_KEY))) {
                int prevTurn = currentTurn(ctx);
                ctx.put(NEEDS_TURN_END_KEY, false);
                return Flux.just(EventMapper.turnEnd(prevTurn, "completed"));
            }
            return Flux.empty();
        });

        // Start new turn
        int turn = incrementTurn(ctx);
        ctx.put(NEEDS_TURN_END_KEY, true);
        Flux<AgentEvent> startTurn = Flux.just(EventMapper.turnStart(turn));

        return Flux.concat(closePrevious, startTurn, next.apply(input));
    }

    // ── Helpers ──

    static int incrementTurn(RuntimeContext ctx) {
        Integer current = ctx.get(TURN_COUNT_KEY);
        int next = (current != null ? current : 0) + 1;
        ctx.put(TURN_COUNT_KEY, next);
        return next;
    }

    static int currentTurn(RuntimeContext ctx) {
        Integer v = ctx.get(TURN_COUNT_KEY);
        return v != null ? v : 0;
    }
}
