package io.agentcore.extensions;

import io.agentcore.model.AgentEvent;
import io.agentcore.extensions.HookTypes.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Extension interface — plugins that hook into the agent lifecycle.
 *
 * <p>Mirrors Python {@code agent_core/extensions/base.py} Extension Protocol.
 *
 * <p>All hook methods use strongly-typed context and result records
 * (see {@link HookTypes}) for compile-time safety.
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
     * Return a {@link ToolCallHookResult} to control execution:
     * <ul>
     *   <li>{@link ToolCallHookResult.Block} — prevent the tool call</li>
     *   <li>{@link ToolCallHookResult.Proceed} — allow, optionally with mutated args</li>
     *   <li>{@link ToolCallHookResult.InjectMetadata} — attach metadata</li>
     * </ul>
     */
    default ToolCallHookResult beforeToolCall(ToolCallContext context) {
        return null;
    }

    /**
     * Called after each tool execution.
     * Return a {@link AfterToolCallHookResult} to optionally modify the result.
     */
    default AfterToolCallHookResult afterToolCall(AfterToolCallContext context) {
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
