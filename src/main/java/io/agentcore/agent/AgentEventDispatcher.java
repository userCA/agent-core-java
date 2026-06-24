package io.agentcore.agent;

import io.agentcore.model.AgentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Manages event subscribers and creates composite emitters.
 *
 * <p>Extracted from {@link Agent} to separate event dispatch concerns
 * from the core agent orchestration logic (SRP).
 */
final class AgentEventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AgentEventDispatcher.class);

    private final List<Consumer<AgentEvent>> subscribers = new CopyOnWriteArrayList<>();

    /**
     * Register a subscriber that will receive all agent events.
     *
     * @param listener the event consumer
     * @return a cancellation runnable that removes the subscriber
     */
    Runnable subscribe(Consumer<AgentEvent> listener) {
        subscribers.add(listener);
        return () -> subscribers.remove(listener);
    }

    /**
     * Create an event emitter that dispatches to BOTH the override consumer
     * AND all registered subscribers. This ensures session-layer persistence
     * (via subscribe()) always receives events, even when an explicit onEvent
     * consumer is provided.
     *
     * @param override per-call event consumer (nullable)
     * @return composite emitter
     */
    Consumer<AgentEvent> createEmitter(Consumer<AgentEvent> override) {
        return evt -> {
            if (override != null) {
                try {
                    override.accept(evt);
                } catch (Exception e) {
                    log.warn("Event override consumer failed", e);
                }
            }
            for (Consumer<AgentEvent> sub : subscribers) {
                try {
                    sub.accept(evt);
                } catch (Exception e) {
                    log.warn("Subscriber failed to handle event", e);
                }
            }
        };
    }
}
