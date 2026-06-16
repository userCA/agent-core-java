package io.agentcore.v2.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ReasoningInput;
import io.agentscope.core.message.Msg;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

import reactor.core.publisher.Flux;

/**
 * Implements Claude Code-style steering and follow-up message injection.
 *
 * <p>Messages enqueued via {@link #steer(Msg)} / {@link #followUp(Msg)} are
 * drained between turns and injected into the conversation by appending them
 * to the {@code RuntimeContext} so downstream middlewares and the agent loop
 * can include them in the next reasoning call.
 */
public final class SteeringMiddleware implements MiddlewareBase {

    /** Maximum queued messages before oldest are dropped. */
    private static final int MAX_QUEUE = 100;

    /** Context key for drained steering messages. */
    public static final String STEERING_MESSAGES_KEY = "claude_code.steering_messages";

    /** Context key for drained follow-up messages. */
    public static final String FOLLOWUP_MESSAGES_KEY = "claude_code.followup_messages";

    private final ConcurrentLinkedQueue<Msg> steeringQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Msg> followUpQueue = new ConcurrentLinkedQueue<>();
    private final boolean drainAll;

    /**
     * @param drainAll if {@code true}, drain all queued messages per turn;
     *                 if {@code false}, drain one at a time (FIFO).
     */
    public SteeringMiddleware(boolean drainAll) {
        this.drainAll = drainAll;
    }

    /** Enqueue a steering message (triggers a new turn). */
    public void steer(Msg message) {
        enqueue(steeringQueue, message);
    }

    /** Enqueue a follow-up message (injected before next reasoning). */
    public void followUp(Msg message) {
        enqueue(followUpQueue, message);
    }

    /** Clear all queued messages. */
    public void clearAll() {
        steeringQueue.clear();
        followUpQueue.clear();
    }

    // ── onReasoning: inject drained messages before reasoning ──

    @Override
    public Flux<AgentEvent> onReasoning(
            Agent agent, RuntimeContext ctx, ReasoningInput input,
            Function<ReasoningInput, Flux<AgentEvent>> next) {

        // Drain steering
        List<Msg> steering = drain(steeringQueue);
        if (!steering.isEmpty()) {
            ctx.put(STEERING_MESSAGES_KEY, steering);
        }

        // Drain follow-up
        List<Msg> followUps = drain(followUpQueue);
        if (!followUps.isEmpty()) {
            ctx.put(FOLLOWUP_MESSAGES_KEY, followUps);
        }

        return next.apply(input);
    }

    // ── Internals ──

    private void enqueue(ConcurrentLinkedQueue<Msg> queue, Msg msg) {
        queue.offer(msg);
        while (queue.size() > MAX_QUEUE) {
            queue.poll();
        }
    }

    private List<Msg> drain(ConcurrentLinkedQueue<Msg> queue) {
        if (drainAll) {
            List<Msg> all = List.copyOf(queue);
            queue.clear();
            return all;
        }
        Msg one = queue.poll();
        return one != null ? List.of(one) : List.of();
    }
}
