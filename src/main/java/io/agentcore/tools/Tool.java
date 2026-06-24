package io.agentcore.tools;

import java.util.Map;

/**
 * Tool interface — all executable tools implement this.
 *
 * <p>Mirrors Python {@code agent_core/tools/base.py} Tool Protocol.
 */
public interface Tool {

    /**
     * The tool's definition (metadata).
     */
    ToolDefinition definition();

    /**
     * Pre-process raw arguments before schema validation and execution.
     *
     * <p>Mirrors pi-mono {@code AgentTool.prepareArguments}.
     * Override to normalize, coerce, or fill default values.
     *
     * @param raw the raw arguments parsed from the LLM output
     * @return processed arguments (may be the same or a new map)
     */
    default Map<String, Object> prepareArguments(Map<String, Object> raw) {
        return raw;
    }

    /**
     * Execute the tool with the given parameters.
     *
     * @param toolCallId unique ID for this tool call invocation
     * @param params     parsed parameters from the LLM (after prepareArguments)
     * @param ctx        execution context (signal, metadata, update callback)
     * @return the tool result
     * @throws Exception if execution fails
     */
    ToolResult execute(String toolCallId, Map<String, Object> params, ToolContext ctx) throws Exception;
}
