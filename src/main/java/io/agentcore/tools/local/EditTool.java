package io.agentcore.tools.local;

import io.agentcore.core.content.TextContent;
import io.agentcore.tools.base.*;
import io.agentcore.tools.operations.FileOperations;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class EditTool implements Tool {
    private final String cwd;
    private final FileOperations fileOps;

    public EditTool(String cwd, FileOperations fileOps) {
        this.cwd = cwd;
        this.fileOps = fileOps;
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition("edit", "Edit a file by replacing old text with new text.",
                Map.of("type", "object", "properties", Map.of(
                        "path", Map.of("type", "string", "description", "File path"),
                        "old_string", Map.of("type", "string", "description", "Text to find"),
                        "new_string", Map.of("type", "string", "description", "Replacement text")
                ), "required", List.of("path", "old_string", "new_string")),
                "Edit file by text replacement",
                List.of("Always use 'edit' for small changes instead of rewriting entire files."));
    }

    @Override
    public CompletableFuture<ToolResult> execute(String toolCallId, Map<String, Object> params, ToolContext ctx) {
        String path = (String) params.get("path");
        String oldString = (String) params.get("old_string");
        String newString = (String) params.get("new_string");

        CompletableFuture<Boolean> editFuture;
        if (ctx.mutationQueue() != null) {
            editFuture = ctx.mutationQueue().editLocked(fileOps, path, oldString, newString);
        } else {
            editFuture = fileOps.edit(path, oldString, newString);
        }

        return editFuture.thenApply(success -> {
            if (success) {
                return new ToolResult(List.of(new TextContent("File edited: " + path)));
            } else {
                return new ToolResult(List.of(new TextContent("Edit failed: old_string not found in " + path)));
            }
        }).exceptionally(e -> new ToolResult(
                List.of(new TextContent("Error editing file: " + e.getMessage()))));
    }
}
