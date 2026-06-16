package io.agentcore.tools.local;

import io.agentcore.core.content.TextContent;
import io.agentcore.tools.base.*;
import io.agentcore.tools.operations.FileOperations;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class LsTool implements Tool {
    private final String cwd;
    private final FileOperations fileOps;

    public LsTool(String cwd, FileOperations fileOps) {
        this.cwd = cwd;
        this.fileOps = fileOps;
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition("ls", "List directory contents.",
                Map.of("type", "object", "properties", Map.of(
                        "path", Map.of("type", "string", "description", "Directory path")
                )));
    }

    @Override
    public CompletableFuture<ToolResult> execute(String toolCallId, Map<String, Object> params, ToolContext ctx) {
        String path = (String) params.getOrDefault("path", cwd);
        return fileOps.ls(path).thenApply(files -> {
            StringBuilder sb = new StringBuilder();
            for (var f : files) {
                sb.append(f.isDir() ? "[D] " : "[F] ").append(f.name()).append('\n');
            }
            return new ToolResult(List.of(new TextContent(sb.toString())));
        }).exceptionally(e -> new ToolResult(
                List.of(new TextContent("Error listing directory: " + e.getMessage()))));
    }
}
