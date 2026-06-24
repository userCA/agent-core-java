package io.agentcore.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import io.agentcore.model.Message;
import io.agentcore.model.Content;
import io.agentcore.model.AgentEvent;

/**
 * Mutable runtime context for an Agent — holds working state for the agent loop.
 *
 * <p>Mirrors Python {@code agent_core/core/context.py} AgentContext dataclass,
 * merged with streaming/error state management.
 * Thread-safe via synchronized lists, atomic references, and volatile fields.
 */
public final class AgentContext {

    private static final Logger log = LoggerFactory.getLogger(AgentContext.class);

    /**
     * Thinking levels for model reasoning.
     */
    public enum ThinkingLevel {
        OFF("off"),
        MINIMAL("minimal"),
        LOW("low"),
        MEDIUM("medium"),
        HIGH("high"),
        XHIGH("xhigh");

        private static final Map<String, ThinkingLevel> LOOKUP;
        static {
            Map<String, ThinkingLevel> m = new HashMap<>();
            for (ThinkingLevel level : values()) {
                m.put(level.value.toLowerCase(), level);
            }
            LOOKUP = Map.copyOf(m);
        }

        private final String value;

        ThinkingLevel(String value) {
            this.value = value;
        }

        /** Returns the string value used by providers. */
        public String value() {
            return value;
        }

        /**
         * Parse a string to ThinkingLevel (case-insensitive).
         * @return the matching level, or {@link #OFF} if not recognized
         */
        public static ThinkingLevel fromValue(String s) {
            if (s == null) return OFF;
            ThinkingLevel level = LOOKUP.get(s.toLowerCase());
            return level != null ? level : OFF;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    private volatile String systemPrompt;
    private final List<Message> messages;

    // Streaming state
    private final AtomicBoolean streaming = new AtomicBoolean(false);
    private volatile String errorMessage;

    public AgentContext() {
        this("", new ArrayList<>());
    }

    public AgentContext(String systemPrompt, List<Message> messages) {
        this.systemPrompt = systemPrompt != null ? systemPrompt : "";
        this.messages = Collections.synchronizedList(new ArrayList<>(
                messages != null ? messages : List.of()));
    }

    // ── Getters ────────────────────────────────────────────────

    public String systemPrompt() { return systemPrompt; }
    /**
     * Returns the internal message list (synchronized wrapper).
     *
     * <p>Prefer {@link #messagesSnapshot()} for iteration. The returned list
     * is backed by {@link Collections#synchronizedList} — iteration requires
     * external synchronization.
     *
     * @return the internal synchronized list (not a copy)
     */
    public List<Message> messages() { return messages; }

    /**
     * Returns an immutable snapshot of the current messages.
     *
     * <p>Safe for iteration without external synchronization.
     * Preferred over {@link #messages()} for read-only access.
     *
     * @return an unmodifiable copy of the message list
     */
    public List<Message> messagesSnapshot() {
        synchronized (messages) {
            return List.copyOf(messages);
        }
    }
    public boolean isStreaming() { return streaming.get(); }
    public String errorMessage() { return errorMessage; }

    // ── Setters ────────────────────────────────────────────────

    public void setSystemPrompt(String v) { this.systemPrompt = v; }
    public void setErrorMessage(String v) { this.errorMessage = v; }

    // ── Thread-safe state operations ──────────────────────────

    /**
     * Atomically set streaming state to true.
     * @return true if successfully transitioned from false to true
     */
    public boolean tryStartStreaming() {
        return streaming.compareAndSet(false, true);
    }

    /**
     * Set streaming state to false and clear streaming message.
     */
    public void stopStreaming() {
        streaming.set(false);
    }

    /**
     * Add a message to the context's message list (thread-safe via synchronized list).
     */
    public void addMessage(Message msg) {
        synchronized (messages) {
            messages.add(msg);
        }
    }

    /**
     * Add multiple messages atomically (thread-safe).
     * Uses the same lock as {@link #replaceMessages(List)} to prevent races.
     */
    public void addMessages(List<? extends Message> msgs) {
        synchronized (messages) {
            messages.addAll(msgs);
        }
    }

    /**
     * Replace all messages with a new list (thread-safe).
     */
    public void replaceMessages(List<Message> newMessages) {
        synchronized (messages) {
            messages.clear();
            messages.addAll(newMessages);
        }
    }

    /**
     * Reset context for a new conversation, preserving systemPrompt and tools.
     */
    public void resetState() {
        synchronized (messages) {
            messages.clear();
        }
        streaming.set(false);
        errorMessage = null;
    }
}
