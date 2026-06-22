package io.agentcore.core;

import io.agentcore.tools.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mutable runtime context for an Agent — holds working state for the agent loop.
 *
 * <p>Mirrors Python {@code agent_core/core/context.py} AgentContext dataclass,
 * merged with streaming/error state management.
 * Thread-safe via synchronized lists, atomic references, and volatile fields.
 */
public final class AgentContext {

    private static final Logger log = LoggerFactory.getLogger(AgentContext.class);

    /** Thinking levels for model reasoning. */
    public static final String THINKING_OFF = "off";
    public static final String THINKING_MINIMAL = "minimal";
    public static final String THINKING_LOW = "low";
    public static final String THINKING_MEDIUM = "medium";
    public static final String THINKING_HIGH = "high";
    public static final String THINKING_XHIGH = "xhigh";

    private volatile String systemPrompt;
    private final List<Message> messages;
    private final List<Tool> tools;

    // Streaming state
    private final AtomicBoolean streaming = new AtomicBoolean(false);
    private final AtomicReference<Message> streamingMessage = new AtomicReference<>();
    private volatile String errorMessage;

    public AgentContext() {
        this("", new ArrayList<>(), new ArrayList<>());
    }

    public AgentContext(String systemPrompt, List<Message> messages, List<Tool> tools) {
        this.systemPrompt = systemPrompt != null ? systemPrompt : "";
        this.messages = Collections.synchronizedList(
                new ArrayList<>(messages != null ? messages : List.of()));
        this.tools = Collections.synchronizedList(
                new ArrayList<>(tools != null ? tools : List.of()));
    }

    // ── Getters ────────────────────────────────────────────────

    public String systemPrompt() { return systemPrompt; }
    public List<Message> messages() { return messages; }
    public List<Tool> tools() { return tools; }
    public boolean isStreaming() { return streaming.get(); }
    public Message streamingMessage() { return streamingMessage.get(); }
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
        streamingMessage.set(null);
    }

    /**
     * Set the current streaming message.
     */
    public void setStreamingMessage(Message msg) {
        streamingMessage.set(msg);
    }

    /**
     * Add a message to the context's message list (thread-safe via synchronized list).
     */
    public void addMessage(Message msg) {
        messages.add(msg);
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
    public synchronized void resetState() {
        messages.clear();
        streaming.set(false);
        streamingMessage.set(null);
        errorMessage = null;
    }
}
