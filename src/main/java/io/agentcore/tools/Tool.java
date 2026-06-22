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
     * Execute the tool with the given parameters.
     *
     * @param toolCallId unique ID for this tool call invocation
     * @param params     parsed parameters from the LLM
     * @param ctx        execution context (signal, metadata, update callback)
     * @return the tool result
     * @throws Exception if execution fails
     */
    ToolResult execute(String toolCallId, Map<String, Object> params, ToolContext ctx) throws Exception;
}
