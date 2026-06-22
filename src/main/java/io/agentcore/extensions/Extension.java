package io.agentcore.extensions;

import io.agentcore.core.AgentEvent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Extension interface — plugins that hook into the agent lifecycle.
 *
 * <p>Mirrors Python {@code agent_core/extensions/base.py} Extension Protocol.
 */
public interface Extension {

    /**
     * Extension name.
     */
    String name();

    /**
     * Called before the agent loop starts. Can return a map with:
     * - "system_prompt" → modified system prompt
     * - "message" → a message to inject
     */
    default Map<String, Object> onBeforeAgentStart(String prompt, String systemPrompt) {
        return null;
    }

    /**
     * Called before each tool execution.
     * Can return a map with "block" → true to prevent execution.
     */
    default Map<String, Object> beforeToolCall(Map<String, Object> callContext) {
        return null;
    }

    /**
     * Called after each tool execution.
     */
    default Map<String, Object> afterToolCall(Map<String, Object> callContext) {
        return null;
    }

    /**
     * Transform the LLM message context before each call.
     */
    default List<Map<String, Object>> transformContext(
            List<Map<String, Object>> messages, AtomicBoolean signal) {
        return messages;
    }

    /**
     * Called on each agent event (for logging, metrics, etc.).
     */
    default void onEvent(AgentEvent event) {}
}
