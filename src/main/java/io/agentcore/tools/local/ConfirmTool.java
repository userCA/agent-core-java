package io.agentcore.tools.local;

import io.agentcore.core.humaninput.RequiresHumanInput;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import io.agentcore.tools.base.*;

/**
 * Human-in-the-loop confirmation tool.
 * Raises RequiresHumanInput to pause the agent loop until user responds.
 */
public class ConfirmTool implements Tool {
    @Override
    public ToolDefinition definition() {
        return new ToolDefinition("confirm", "Ask the user for confirmation or input.",
                Map.of("type", "object", "properties", Map.of(
                        "prompt", Map.of("type", "string", "description", "Question to show the user"),
                        "fields", Map.of("type", "array", "description", "Input field definitions")
                ), "required", List.of("prompt")));
    }

    @Override
    public CompletableFuture<ToolResult> execute(String toolCallId, Map<String, Object> params, ToolContext ctx) {
        String prompt = (String) params.get("prompt");
        @SuppressWarnings("unchecked")
        List<Object> fields = (List<Object>) params.getOrDefault("fields", List.of());
        Map<String, Object> schema = Map.of("type", "object", "fields", fields);
        return CompletableFuture.failedFuture(new RequiresHumanInput(prompt, schema));
    }
}
