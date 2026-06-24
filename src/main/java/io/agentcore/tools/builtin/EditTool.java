package io.agentcore.tools.builtin;

import io.agentcore.tools.ParamSchema;
import io.agentcore.tools.ToolParams;
import io.agentcore.tools.shell.FileOperations;

import java.util.Map;
import io.agentcore.model.ToolResult;
import io.agentcore.tools.Tool;
import io.agentcore.tools.ToolContext;
import io.agentcore.tools.ToolDefinition;

/**
 * File editing tool — text replacement.
 */
public class EditTool implements Tool {

    private static final ToolDefinition DEF = new ToolDefinition(
            "edit",
            "Edit a file by replacing the first occurrence of old_string "
                    + "with new_string. Always prefer edit over rewriting entire files "
                    + "for small changes.",
            ParamSchema.object()
                    .prop("path", ParamSchema.string("File path").required())
                    .prop("old_string", ParamSchema.string("Exact text to find (must match uniquely)").required())
                    .prop("new_string", ParamSchema.string("Replacement text").required())
                    .build(),
            null, null, null
    );

    private final FileOperations fileOps;

    public EditTool(FileOperations fileOps) {
        this.fileOps = fileOps;
    }

    @Override
    public ToolDefinition definition() {
        return DEF;
    }

    @Override
    public ToolResult execute(String toolCallId, Map<String, Object> params, ToolContext ctx) throws Exception {
        ToolParams p = new ToolParams(params);

        String path;
        String oldString;
        String newString;
        try {
            path = p.requireString("path");
            oldString = p.requireString("old_string");
            newString = p.requireString("new_string");
        } catch (IllegalArgumentException e) {
            return ToolResult.error("missing_param", e.getMessage());
        }

        try {
            boolean success = fileOps.edit(path, oldString, newString);

            if (success) {
                return new ToolResult("File edited: " + path);
            } else {
                return ToolResult.error("not_found", "old_string not found in " + path);
            }
        } catch (Exception e) {
            return ToolResult.error("edit_failed", e.getMessage());
        }
    }
}
