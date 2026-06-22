package io.agentcore.companion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CompanionMemory — structured observations about user behavior.
 *
 * <p>Mirrors Python {@code agent_core/companion/memory.py}.
 * Lightweight structured memory for companion, separate from agent memory.
 * Uses uid as the store session_id so observations survive across HTTP sessions.
 */
public final class CompanionMemory {

    private static final Logger log = LoggerFactory.getLogger(CompanionMemory.class);

    /** A single observation event. */
    public record Observation(
            String type,       // "user_prompt" | "tool_use" | "idle_return"
            long timestamp,
            String summary,
            Map<String, Object> metadata
    ) {
        public Observation {
            if (type == null) type = "";
            if (summary == null) summary = "";
            if (metadata == null) metadata = Map.of();
        }
    }

    private static final int MAX_OBS_PER_USER = 200;

    // In-memory storage keyed by uid — ConcurrentHashMap for thread safety
    private final Map<String, List<Observation>> store = new ConcurrentHashMap<>();

    /**
     * Record an observation for a user (thread-safe, synchronized per uid).
     */
    public void observe(String uid, Observation event) {
        List<Observation> list = store.computeIfAbsent(uid, k -> Collections.synchronizedList(new ArrayList<>()));
        synchronized (list) {
            list.add(event);
            // Cap at MAX_OBS_PER_USER
            while (list.size() > MAX_OBS_PER_USER) {
                list.removeFirst();
            }
        }
        log.debug("Observation recorded for uid={}: type={}", uid, event.type());
    }

    /**
     * Recall recent observations for a user (thread-safe snapshot).
     */
    public List<Observation> recall(String uid, int limit) {
        List<Observation> list = store.getOrDefault(uid, List.of());
        synchronized (list) {
            int from = Math.max(0, list.size() - limit);
            return List.copyOf(list.subList(from, list.size()));
        }
    }

    /**
     * Recall all observations for a user (thread-safe snapshot).
     */
    public List<Observation> recallAll(String uid) {
        List<Observation> list = store.getOrDefault(uid, List.of());
        synchronized (list) {
            return List.copyOf(list);
        }
    }

    /**
     * Clear all observations for a user.
     */
    public void clear(String uid) {
        store.remove(uid);
    }

    /**
     * Get the count of observations for a user.
     */
    public int count(String uid) {
        List<Observation> list = store.getOrDefault(uid, List.of());
        synchronized (list) {
            return list.size();
        }
    }
}
