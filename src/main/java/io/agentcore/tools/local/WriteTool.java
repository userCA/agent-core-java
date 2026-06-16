package io.agentcore.tools.local;

import io.agentcore.core.content.TextContent;
import io.agentcore.tools.base.*;
import io.agentcore.tools.operations.FileOperations;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class WriteTool implements Tool {
    private final String cwd;
    private final FileOperations fileOps;

    public WriteTool(String cwd, FileOperations fileOps) {
        this.cwd = cwd;
        this.fileOps = fileOps;
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition("write", "Write content to a file.",
                Map.of("type", "object", "properties", Map.of(
                        "path", Map.of("type", "string", "description", "File path"),
                        "content", Map.of("type", "string", "description", "Full file content")
                ), "required", List.of("path", "content")));
    }

    @Override
    public CompletableFuture<ToolResult> execute(String toolCallId, Map<String, Object> params, ToolContext ctx) {
        String path = (String) params.get("path");
        String content = (String) params.get("content");

        CompletableFuture<Void> writeFuture;
        if (ctx.mutationQueue() != null) {
            writeFuture = ctx.mutationQueue().writeLocked(fileOps, path, content);
        } else {
            writeFuture = fileOps.write(path, content);
        }

        return writeFuture.thenApply(v -> new ToolResult(
                List.of(new TextContent("File written: " + path)),
                Map.of("path", path)
        )).exceptionally(e -> new ToolResult(
                List.of(new TextContent("Error writing file: " + e.getMessage()))));
    }
}
