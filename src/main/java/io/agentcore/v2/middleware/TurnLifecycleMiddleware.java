package io.agentcore.v2.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.ReasoningInput;
import io.agentscope.core.middleware.ActingInput;
import io.agentcore.v2.events.EventMapper;

import java.util.function.Function;

import reactor.core.publisher.Flux;

/**
 * Emits Claude Code TurnStart/TurnEnd events around each reasoning+acting cycle.
 *
 * <p>Tracks the turn counter in {@link RuntimeContext} attributes so it
 * survives across middleware invocations within a single agent run.
 */
public final class TurnLifecycleMiddleware implements MiddlewareBase {

    private static final String TURN_COUNT_KEY = "claude_code.turn_count";

    // ── onAgent: reset turn counter at agent start ──

    @Override
    public Flux<AgentEvent> onAgent(
            Agent agent, RuntimeContext ctx, AgentInput input,
            Function<AgentInput, Flux<AgentEvent>> next) {
        ctx.put(TURN_COUNT_KEY, 0);
        return next.apply(input);
    }

    // ── onReasoning: emit TurnStart before reasoning begins ──

    @Override
    public Flux<AgentEvent> onReasoning(
            Agent agent, RuntimeContext ctx, ReasoningInput input,
            Function<ReasoningInput, Flux<AgentEvent>> next) {
        int turn = incrementTurn(ctx);
        return Flux.concat(
                Flux.just(EventMapper.turnStart(turn)),
                next.apply(input)
        );
    }

    // ── onActing: emit TurnEnd after acting completes ──

    @Override
    public Flux<AgentEvent> onActing(
            Agent agent, RuntimeContext ctx, ActingInput input,
            Function<ActingInput, Flux<AgentEvent>> next) {
        return next.apply(input)
                .concatWith(Flux.defer(() -> {
                    int turn = currentTurn(ctx);
                    return Flux.just(EventMapper.turnEnd(turn, "completed"));
                }));
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
