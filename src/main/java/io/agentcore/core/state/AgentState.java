package io.agentcore.core.state;

import io.agentcore.core.messages.AgentMessage;
import io.agentcore.core.messages.AssistantMessage;
import io.agentcore.tools.base.ToolDefinition;
import io.agentcore.providers.types.Model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mutable agent state — holds conversation history, streaming state, and configuration.
 * Thread-safe: compound operations are synchronized to ensure atomicity.
 */
public class AgentState {
    private volatile String systemPrompt;
    private volatile Model model;
    private volatile ThinkingLevel thinkingLevel;
    private volatile List<ToolDefinition> tools;
    private final List<AgentMessage> messages;

    private volatile boolean streaming;
    private volatile AssistantMessage streamingMessage;
    private volatile String errorMessage;

    public AgentState() {
        this.systemPrompt = "";
        this.thinkingLevel = ThinkingLevel.OFF;
        this.tools = new ArrayList<>();
        this.messages = Collections.synchronizedList(new ArrayList<>());
        this.streaming = false;
    }

    // --- Getters and Setters ---

    public String systemPrompt() { return systemPrompt; }
    public void systemPrompt(String v) { this.systemPrompt = v; }

    public Model model() { return model; }
    public void model(Model v) { this.model = v; }

    public ThinkingLevel thinkingLevel() { return thinkingLevel; }
    public void thinkingLevel(ThinkingLevel v) { this.thinkingLevel = v; }

    public List<ToolDefinition> tools() { return tools; }
    public void tools(List<ToolDefinition> v) { this.tools = new ArrayList<>(v); }

    /**
     * Returns the internal synchronized messages list.
     * Individual operations (add, get) are thread-safe via synchronizedList.
     * For compound iteration, callers should synchronize on the returned list.
     */
    public List<AgentMessage> messages() { return messages; }

    /**
     * Atomically replace all messages with a copy of the provided list.
     */
    public synchronized void replaceMessages(List<AgentMessage> newMessages) {
        messages.clear();
        messages.addAll(newMessages);
    }

    /**
     * Thread-safe convenience method to add a single message.
     */
    public void addMessage(AgentMessage msg) {
        messages.add(msg);
    }

    /**
     * Atomically check-and-set the streaming flag.
     * @return true if streaming was successfully started (was false, now true),
     *         false if already streaming.
     */
    public synchronized boolean tryStartStreaming() {
        if (streaming) return false;
        streaming = true;
        return true;
    }

    /**
     * Atomically clear streaming state and associated fields.
     */
    public synchronized void stopStreaming() {
        streaming = false;
        streamingMessage = null;
    }

    /**
     * Atomically reset all mutable state while preserving configuration
     * (systemPrompt, model, thinkingLevel, tools).
     */
    public synchronized void resetState() {
        messages.clear();
        streaming = false;
        streamingMessage = null;
        errorMessage = null;
    }

    public boolean isStreaming() { return streaming; }
    public void streaming(boolean v) { this.streaming = v; }

    public AssistantMessage streamingMessage() { return streamingMessage; }
    public void streamingMessage(AssistantMessage v) { this.streamingMessage = v; }

    public String errorMessage() { return errorMessage; }
    public void errorMessage(String v) { this.errorMessage = v; }
}
