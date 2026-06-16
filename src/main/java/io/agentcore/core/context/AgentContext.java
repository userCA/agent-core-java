package io.agentcore.core.context;

import io.agentcore.core.messages.AgentMessage;
import io.agentcore.tools.base.ToolDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mutable working memory passed into the agent loop.
 * The loop appends to {@code messages} as the conversation progresses.
 */
public class AgentContext {
    private String systemPrompt;
    private List<AgentMessage> messages;
    private List<ToolDefinition> tools;

    public AgentContext() {
        this("", new ArrayList<>(), new ArrayList<>());
    }

    public AgentContext(String systemPrompt, List<AgentMessage> messages, List<ToolDefinition> tools) {
        this.systemPrompt = systemPrompt;
        this.messages = Collections.synchronizedList(new ArrayList<>(messages));
        this.tools = Collections.synchronizedList(new ArrayList<>(tools));
    }

    public String systemPrompt() { return systemPrompt; }
    public void systemPrompt(String v) { this.systemPrompt = v; }

    public List<AgentMessage> messages() { return messages; }

    public List<ToolDefinition> tools() { return tools; }
    public void tools(List<ToolDefinition> v) { this.tools = Collections.synchronizedList(new ArrayList<>(v)); }
}
