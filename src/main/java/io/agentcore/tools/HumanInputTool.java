package io.agentcore.tools;

import io.agentcore.model.HumanInputGate;
import io.agentcore.tools.Tool;
import io.agentcore.tools.ToolContext;
import io.agentcore.tools.ToolDefinition;
import io.agentcore.model.ToolResult;

import java.util.List;
import java.util.Map;

/**
 * General-purpose Human-in-the-Loop tool.
 *
 * <p>When called by the LLM, this tool:
 * <ol>
 *   <li>Returns {@link ToolResult#requiresHumanInput} to pause execution</li>
 *   <li>The agent loop emits a HumanInputRequired event and blocks on the gate</li>
 *   <li>When user input arrives, the tool is re-executed with
 *       {@link ToolContext#userInput()} populated, and returns the user's values</li>
 * </ol>
 */
public class HumanInputTool implements Tool {

    private final HumanInputGate gate;

    public HumanInputTool(HumanInputGate gate) {
        this.gate = gate;
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "human_input",
                "Pause execution and ask the user for input. "
                        + "Use this when you need user confirmation, clarification, "
                        + "or additional information before proceeding.",
                Map.of("type", "object", "properties", Map.of(
                        "prompt", Map.of("type", "string",
                                "description", "Question or message to display to the user"),
                        "input_schema", Map.of("type", "object",
                                "description", "Optional JSON Schema describing the expected input fields. "
                                        + "Example: {\"type\": \"object\", \"properties\": {\"answer\": {\"type\": \"string\"}}}")
                ), "required", List.of("prompt")),
                null, null, null
        );
    }

    @Override
    public ToolResult execute(String toolCallId, Map<String, Object> params, ToolContext ctx) throws Exception {
        // HITL re-execution path: user already responded via HumanInputGate
        if (ctx.userInput() != null) {
            return new ToolResult(formatValues(ctx.userInput()));
        }

        String prompt = (String) params.get("prompt");
        if (prompt == null || prompt.isBlank()) {
            return ToolResult.error("missing_param", "'prompt' parameter is required");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> inputSchema = (Map<String, Object>) params.get("input_schema");

        // Return requiresInput — ToolRunner checks this and handles HITL flow
        return ToolResult.requiresHumanInput(prompt, inputSchema);
    }

    private static String formatValues(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return "(no input provided)";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("User provided input:\n");
        for (var entry : values.entrySet()) {
            Object v = entry.getValue();
            // Truncate long base64 data
            if (v instanceof String s && s.startsWith("data:") && s.length() > 500) {
                String mime = s.contains(";") ? s.substring(s.indexOf(':') + 1, s.indexOf(';')) : "unknown";
                v = "[binary " + mime + " data, " + s.length() + " chars]";
            }
            sb.append("  ").append(entry.getKey()).append(": ").append(v).append('\n');
        }
        return sb.toString();
    }

    /** The gate associated with this tool. */
    public HumanInputGate gate() {
        return gate;
    }
}
