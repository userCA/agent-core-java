package io.agentcore.tools.builtin;

import io.agentcore.tools.shell.FileOperations;

import java.util.List;
import java.util.Map;
import io.agentcore.model.ToolResult;
import io.agentcore.tools.Tool;
import io.agentcore.tools.ToolContext;
import io.agentcore.tools.ToolDefinition;

/**
 * File editing tool — text replacement.
 */
public class EditTool implements Tool {

    private final FileOperations fileOps;

    public EditTool(FileOperations fileOps) {
        this.fileOps = fileOps;
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "edit",
                "Edit a file by replacing the first occurrence of old_string "
                        + "with new_string. Always prefer edit over rewriting entire files "
                        + "for small changes.",
                Map.of("type", "object", "properties", Map.of(
                        "path", Map.of("type", "string",
                                "description", "File path"),
                        "old_string", Map.of("type", "string",
                                "description", "Exact text to find (must match uniquely)"),
                        "new_string", Map.of("type", "string",
                                "description", "Replacement text")
                ), "required", List.of("path", "old_string", "new_string")),
                null, null, null
        );
    }

    @Override
    public ToolResult execute(String toolCallId, Map<String, Object> params, ToolContext ctx) throws Exception {
        String path = (String) params.get("path");
        String oldString = (String) params.get("old_string");
        String newString = (String) params.get("new_string");

        if (path == null || path.isBlank()) {
            return new ToolResult("ERROR: 'path' parameter is required");
        }
        if (oldString == null || newString == null) {
            return new ToolResult("ERROR: 'old_string' and 'new_string' parameters are required");
        }

        try {
            boolean success = fileOps.edit(path, oldString, newString);

            if (success) {
                return new ToolResult("File edited: " + path);
            } else {
                return new ToolResult("ERROR: Edit failed: old_string not found in " + path);
            }
        } catch (Exception e) {
            return new ToolResult("Error editing file: " + e.getMessage());
        }
    }
}
