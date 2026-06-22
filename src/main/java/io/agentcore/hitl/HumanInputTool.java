package io.agentcore.hitl;

import io.agentcore.tools.Tool;
import io.agentcore.tools.ToolContext;
import io.agentcore.tools.ToolDefinition;
import io.agentcore.tools.ToolResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * General-purpose Human-in-the-Loop tool.
 *
 * <p>When called by the LLM, this tool:
 * <ol>
 *   <li>Registers a pending input request with {@link io.agentcore.core.HumanInputGate}</li>
 *   <li>Blocks (virtual thread) until the external caller provides input</li>
 *   <li>The result text contains the user's provided values</li>
 * </ol>
 */
public class HumanInputTool implements Tool {

    private final io.agentcore.core.HumanInputGate gate;

    public HumanInputTool(io.agentcore.core.HumanInputGate gate) {
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
        String prompt = (String) params.get("prompt");
        if (prompt == null || prompt.isBlank()) {
            return new ToolResult("ERROR: 'prompt' parameter is required");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> inputSchema = (Map<String, Object>) params.get("input_schema");

        // Register a pending input request — blocks until external caller provides input
        CompletableFuture<Map<String, Object>> future = gate.requireInput(toolCallId);

        // In the new architecture, the AgentLoop catches RequiresHumanInput exceptions
        // and emits a HumanInputRequired event. For now, just block until input arrives.
        try {
            Map<String, Object> values = future.get();
            return new ToolResult(formatValues(values));
        } catch (java.util.concurrent.CancellationException e) {
            return new ToolResult("Input request was cancelled.");
        }
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
    public io.agentcore.core.HumanInputGate gate() {
        return gate;
    }
}
