package io.agentcore.tools.builtin;

import java.util.List;
import java.util.Map;
import io.agentcore.model.ToolResult;
import io.agentcore.tools.ParamSchema;
import io.agentcore.tools.Tool;
import io.agentcore.tools.ToolContext;
import io.agentcore.tools.ToolDefinition;

/**
 * Human-in-the-loop confirmation tool.
 *
 * <p>When executed, throws {@link ConfirmSuspendedException} which causes the
 * agent loop to suspend execution and wait for user confirmation.
 */
public class ConfirmTool implements Tool {

    private static final ToolDefinition DEF = new ToolDefinition(
            "confirm",
            "Ask the user for confirmation or input before proceeding. "
                    + "Use this when you need explicit user approval for an action.",
            ParamSchema.object()
                    .prop("prompt", ParamSchema.string("Question or message to show the user").required())
                    .prop("fields", ParamSchema.array("Optional input field definitions for structured input"))
                    .build(),
            null, null, null
    );

    public ConfirmTool() {
    }

    @Override
    public ToolDefinition definition() {
        return DEF;
    }

    @Override
    public ToolResult execute(String toolCallId, Map<String, Object> params, ToolContext ctx) throws Exception {
        Object promptObj = params.get("prompt");
        if (promptObj == null || (promptObj instanceof String s && s.isBlank())) {
            return ToolResult.error("missing_param", "'prompt' parameter is required");
        }
        String prompt = String.valueOf(promptObj);

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
