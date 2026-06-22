package io.agentcore.tools;

import io.agentcore.tools.util.FileMutationQueue;
import io.agentcore.tools.util.FileOperations;

import java.util.List;
import java.util.Map;

/**
 * File editing tool — text replacement.
 */
public class EditTool implements Tool {

    private final FileOperations fileOps;
    private final FileMutationQueue mutationQueue;

    public EditTool(FileOperations fileOps, FileMutationQueue mutationQueue) {
        this.fileOps = fileOps;
        this.mutationQueue = mutationQueue;
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
            boolean success;
            if (mutationQueue != null) {
                success = mutationQueue.editLocked(fileOps, path, oldString, newString).join();
            } else {
                success = fileOps.edit(path, oldString, newString);
            }

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
