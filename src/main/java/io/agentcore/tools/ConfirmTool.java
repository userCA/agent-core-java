package io.agentcore.tools;

import java.util.List;
import java.util.Map;

/**
 * Human-in-the-loop confirmation tool.
 *
 * <p>When executed, throws {@link ConfirmSuspendedException} which causes the
 * agent loop to suspend execution and wait for user confirmation.
 */
public class ConfirmTool implements Tool {

    public ConfirmTool() {
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "confirm",
                "Ask the user for confirmation or input before proceeding. "
                        + "Use this when you need explicit user approval for an action.",
                Map.of("type", "object", "properties", Map.of(
                        "prompt", Map.of("type", "string",
                                "description", "Question or message to show the user"),
                        "fields", Map.of("type", "array",
                                "description", "Optional input field definitions for structured input")
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

        // Suspend tool execution — the agent loop will catch this and
        // emit a HumanInputRequest event, pausing until the user responds.
        throw new ConfirmSuspendedException(prompt);
    }

    /**
     * Exception thrown to signal that the agent loop should suspend
     * and wait for user confirmation.
     */
    public static class ConfirmSuspendedException extends Exception {
        private final String prompt;

        public ConfirmSuspendedException(String prompt) {
            super("Confirmation required: " + prompt);
            this.prompt = prompt;
        }

        public String prompt() {
            return prompt;
        }
    }
}
