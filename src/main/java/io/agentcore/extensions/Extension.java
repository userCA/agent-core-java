package io.agentcore.extensions;

import io.agentcore.model.AgentEvent;
import io.agentcore.extensions.HookTypes.*;

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
     * Execution priority. Lower values run first.
     *
     * <p>For example, a security policy extension should run before
     * a logging extension to ensure commands are validated first.
     * Default is 0; use negative values for higher priority.
     */
    default int order() { return 0; }

    /**
     * Called before the agent loop starts. Can return a typed result:
     * <ul>
     *   <li>{@link BeforeAgentStartResult.ModifySystemPrompt} — replace system prompt</li>
     *   <li>{@code null} — no modification</li>
     * </ul>
     */
    default BeforeAgentStartResult onBeforeAgentStart(String prompt, String systemPrompt) {
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
     * Called on each agent event (for logging, metrics, etc.).
     */
    default void onEvent(AgentEvent event) {}
}
