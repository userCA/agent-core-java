package io.agentcore.tools.builtin;

import java.util.List;
import java.util.Map;
import io.agentcore.model.HumanInputGate;
import io.agentcore.model.ToolResult;
import io.agentcore.tools.ParamSchema;
import io.agentcore.tools.Tool;
import io.agentcore.tools.ToolContext;
import io.agentcore.tools.ToolDefinition;

/**
 * Human-in-the-loop confirmation tool.
 *
 * <p>When executed, returns {@link ToolResult#requiresHumanInput} which causes
 * the agent loop to pause via the {@link HumanInputGate} mechanism,
 * emit a {@code HumanInputRequired} event, and resume when user input arrives.
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
        // HITL re-execution path: user already responded via HumanInputGate
        if (ctx.userInput() != null) {
            String answer = ctx.userInput().containsKey("answer")
                    ? String.valueOf(ctx.userInput().get("answer"))
                    : "confirmed";
            return new ToolResult("User confirmed: " + answer);
        }

        Object promptObj = params.get("prompt");
        if (promptObj == null || (promptObj instanceof String s && s.isBlank())) {
            return ToolResult.error("missing_param", "'prompt' parameter is required");
        }
        String prompt = String.valueOf(promptObj);

        // Return requiresInput — ToolRunner checks this and handles HITL flow
        return ToolResult.requiresHumanInput(prompt, Map.of());
    }
}
