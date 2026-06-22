package io.agentcore.tools;

import io.agentcore.tools.util.FileOperations;

import java.util.List;
import java.util.Map;

/**
 * File finding tool by name pattern.
 */
public class FindTool implements Tool {

    private static final int MAX_DISPLAY_RESULTS = 500;

    private final FileOperations fileOps;

    public FindTool(FileOperations fileOps) {
        this.fileOps = fileOps;
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "find",
                "Find files by name pattern (glob). "
                        + "Searches recursively up to 20 levels deep.",
                Map.of("type", "object", "properties", Map.of(
                        "pattern", Map.of("type", "string",
                                "description", "Filename glob pattern (e.g. '*.java', 'pom.xml')"),
                        "path", Map.of("type", "string",
                                "description", "Root directory (default: working directory)")
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

        try {
            List<String> results = fileOps.find(path, pattern);
            List<String> capped = results.size() > MAX_DISPLAY_RESULTS
                    ? results.subList(0, MAX_DISPLAY_RESULTS) : results;
            String output = String.join("\n", capped);
            if (results.size() > MAX_DISPLAY_RESULTS) {
                output += "\n... (truncated, " + results.size() + " total)";
            }
            return new ToolResult(output);
        } catch (Exception e) {
            return new ToolResult("Error finding files: " + e.getMessage());
        }
    }
}
