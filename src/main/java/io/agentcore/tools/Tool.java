package io.agentcore.tools;

import java.util.Map;
import io.agentcore.model.ToolResult;
import io.agentcore.tools.ToolRenderer.RenderedOutput;

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

    // ── Default rendering hooks ──────────────────────────────────

    /**
     * Render a short summary of the tool call (e.g. 'read /path/to/file').
     * Override for custom display; default returns "name(arg1, arg2)".
     */
    default String renderCall(String toolCallId, String name, Map<String, Object> arguments) {
        if (arguments.isEmpty()) return name + "()";
        String argsSummary = arguments.entrySet().stream()
                .map(e -> e.getKey() + "=" + summarizeValue(e.getValue()))
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        return name + "(" + argsSummary + ")";
    }

    /**
     * Render the tool execution result for frontend display.
     * Override for custom rendering; default returns plain text.
     */
    default RenderedOutput renderResult(String toolCallId, String name,
                                        ToolResult result, boolean isError) {
        return new RenderedOutput(result.text());
    }

    private static String summarizeValue(Object v) {
        if (v == null) return "null";
        String s = String.valueOf(v);
        return s.length() > 50 ? s.substring(0, 47) + "..." : s;
    }
}
