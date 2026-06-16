package io.agentcore.tools.local;

import io.agentcore.core.content.TextContent;
import io.agentcore.tools.base.*;
import io.agentcore.tools.operations.FileOperations;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class GrepTool implements Tool {
    private final String cwd;
    private final FileOperations fileOps;

    public GrepTool(String cwd, FileOperations fileOps) {
        this.cwd = cwd;
        this.fileOps = fileOps;
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition("grep", "Search for regex patterns in files.",
                Map.of("type", "object", "properties", Map.of(
                        "pattern", Map.of("type", "string", "description", "Regex pattern"),
                        "path", Map.of("type", "string", "description", "File or directory path"),
                        "include", Map.of("type", "string", "description", "Glob filter for filenames")
                ), "required", List.of("pattern")),
                "Search files with regex",
                List.of("Prefer grep/find/ls over bash when searching files."));
    }

    @Override
    public CompletableFuture<ToolResult> execute(String toolCallId, Map<String, Object> params, ToolContext ctx) {
        String pattern = (String) params.get("pattern");
        String path = (String) params.getOrDefault("path", cwd);
        String include = (String) params.get("include");

        return fileOps.grep(pattern, path, true, include).thenApply(results -> {
            List<String> capped = results.size() > 500 ? results.subList(0, 500) : results;
            String output = String.join("\n", capped);
            if (results.size() > 500) output += "\n... (truncated, " + results.size() + " total matches)";
            return new ToolResult(List.of(new TextContent(output)));
        }).exceptionally(e -> new ToolResult(
                List.of(new TextContent("Error searching: " + e.getMessage()))));
    }
}
