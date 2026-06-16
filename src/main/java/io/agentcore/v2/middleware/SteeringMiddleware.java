package io.agentcore.v2.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ReasoningInput;
import io.agentscope.core.message.Msg;
import io.agentscope.core.state.AgentState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

import reactor.core.publisher.Flux;

/**
 * Implements Claude Code-style steering and follow-up message injection.
 *
 * <p>Messages enqueued via {@link #steer(Msg)} / {@link #followUp(Msg)} are
 * drained before each reasoning call and appended directly to the agent's
 * {@link AgentState} context so the next LLM call sees them.
 */
public final class SteeringMiddleware implements MiddlewareBase {

    private static final int MAX_QUEUE = 100;

    private final ConcurrentLinkedQueue<Msg> steeringQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Msg> followUpQueue = new ConcurrentLinkedQueue<>();
    private final boolean drainAll;

    public SteeringMiddleware(boolean drainAll) {
        this.drainAll = drainAll;
    }

    /** Enqueue a steering message — triggers a new turn after the current one. */
    public void steer(Msg message) {
        enqueue(steeringQueue, message);
    }

    /** Enqueue a follow-up message — injected before the next reasoning call. */
    public void followUp(Msg message) {
        enqueue(followUpQueue, message);
    }

    /** Clear all queued messages. */
    public void clearAll() {
        steeringQueue.clear();
        followUpQueue.clear();
    }

    // ── onReasoning: drain queues and append to agent state ──

    @Override
    public Flux<AgentEvent> onReasoning(
            Agent agent, RuntimeContext ctx, ReasoningInput input,
            Function<ReasoningInput, Flux<AgentEvent>> next) {

        AgentState state = ctx.getAgentState();
        if (state == null) {
            return next.apply(input);
        }

        List<Msg> injected = new ArrayList<>();

        // Drain steering queue
        List<Msg> steering = drain(steeringQueue);
        if (!steering.isEmpty()) {
            injected.addAll(steering);
        }

        // Drain follow-up queue
        List<Msg> followUps = drain(followUpQueue);
        if (!followUps.isEmpty()) {
            injected.addAll(followUps);
        }

        // Append drained messages to the agent's conversation context
        if (!injected.isEmpty()) {
            state.contextMutable().addAll(injected);
        }

        return next.apply(input);
    }

    // ── Internals ──

    private void enqueue(ConcurrentLinkedQueue<Msg> queue, Msg msg) {
        if (msg == null) return;
        queue.offer(msg);
        while (queue.size() > MAX_QUEUE) {
            queue.poll();
        }
    }

    /**
     * Atomically drains the queue using poll-in-a-loop to avoid
     * the TOCTOU race between snapshot and clear.
     */
    private List<Msg> drain(ConcurrentLinkedQueue<Msg> queue) {
        if (drainAll) {
            List<Msg> result = new ArrayList<>();
            Msg m;
            while ((m = queue.poll()) != null) {
                result.add(m);
            }
            return result;
        }
        Msg one = queue.poll();
        return one != null ? List.of(one) : List.of();
    }
}
