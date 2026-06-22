package io.agentcore.tools;

import io.agentcore.tools.util.FileOperations;

import java.util.List;
import java.util.Map;

/**
 * Regex search tool for file contents.
 */
public class GrepTool implements Tool {

    private static final int MAX_DISPLAY_RESULTS = 500;

    private final FileOperations fileOps;

    public GrepTool(FileOperations fileOps) {
        this.fileOps = fileOps;
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "grep",
                "Search for regex patterns in files. "
                        + "Returns matching lines as file:lineNum:content. "
                        + "Prefer grep over bash when searching files.",
                Map.of("type", "object", "properties", Map.of(
                        "pattern", Map.of("type", "string",
                                "description", "Regex pattern to search for"),
                        "path", Map.of("type", "string",
                                "description", "File or directory path (default: working directory)"),
                        "recursive", Map.of("type", "boolean",
                                "description", "Search recursively (default: true)"),
                        "include", Map.of("type", "string",
                                "description", "Glob filter for filenames (e.g. '*.java')")
                ), "required", List.of("pattern")),
                null, null, null
        );
    }

    @Override
    public ToolResult execute(String toolCallId, Map<String, Object> params, ToolContext ctx) throws Exception {
        String pattern = (String) params.get("pattern");
        if (pattern == null || pattern.isBlank()) {
            return new ToolResult("ERROR: 'pattern' parameter is required");
        }

        String path = (String) params.getOrDefault("path", fileOps.cwd().toString());
        boolean recursive = !params.containsKey("recursive")
                || Boolean.TRUE.equals(params.get("recursive"));
        String include = (String) params.get("include");

        try {
            List<String> results = fileOps.grep(pattern, path, recursive, include);
            List<String> capped = results.size() > MAX_DISPLAY_RESULTS
                    ? results.subList(0, MAX_DISPLAY_RESULTS) : results;
            String output = String.join("\n", capped);
            if (results.size() > MAX_DISPLAY_RESULTS) {
                output += "\n... (truncated, " + results.size() + " total matches)";
            }
            return new ToolResult(output);
        } catch (Exception e) {
            return new ToolResult("Error searching: " + e.getMessage());
        }
    }
}
