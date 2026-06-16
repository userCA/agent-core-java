package io.agentcore.tools.local;

import io.agentcore.core.content.TextContent;
import io.agentcore.tools.base.*;
import io.agentcore.tools.operations.FileOperations;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class FindTool implements Tool {
    private final String cwd;
    private final FileOperations fileOps;

    public FindTool(String cwd, FileOperations fileOps) {
        this.cwd = cwd;
        this.fileOps = fileOps;
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition("find", "Find files by name pattern.",
                Map.of("type", "object", "properties", Map.of(
                        "pattern", Map.of("type", "string", "description", "Filename glob pattern"),
                        "path", Map.of("type", "string", "description", "Root directory")
                ), "required", List.of("pattern")));
    }

    @Override
    public CompletableFuture<ToolResult> execute(String toolCallId, Map<String, Object> params, ToolContext ctx) {
        String pattern = (String) params.get("pattern");
        String path = (String) params.getOrDefault("path", cwd);

        return fileOps.find(path, pattern).thenApply(results -> {
            List<String> capped = results.size() > 500 ? results.subList(0, 500) : results;
            String output = String.join("\n", capped);
            if (results.size() > 500) output += "\n... (truncated, " + results.size() + " total)";
            return new ToolResult(List.of(new TextContent(output)));
        }).exceptionally(e -> new ToolResult(
                List.of(new TextContent("Error finding files: " + e.getMessage()))));
    }
}
