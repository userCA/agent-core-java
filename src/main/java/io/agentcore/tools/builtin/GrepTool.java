package io.agentcore.tools.builtin;

import io.agentcore.tools.ParamSchema;
import io.agentcore.tools.ToolParams;
import io.agentcore.tools.shell.FileOperations;

import java.util.List;
import java.util.Map;
import io.agentcore.model.ToolResult;
import io.agentcore.tools.Tool;
import io.agentcore.tools.ToolContext;
import io.agentcore.tools.ToolDefinition;

/**
 * Regex search tool for file contents.
 */
public class GrepTool implements Tool {

    private static final int MAX_DISPLAY_RESULTS = 500;

    private static final ToolDefinition DEF = new ToolDefinition(
            "grep",
            "Search for regex patterns in files. "
                    + "Returns matching lines as file:lineNum:content. "
                    + "Prefer grep over bash when searching files.",
            ParamSchema.object()
                    .prop("pattern", ParamSchema.string("Regex pattern to search for").required())
                    .prop("path", ParamSchema.string("File or directory path (default: working directory)"))
                    .prop("recursive", ParamSchema.bool("Search recursively (default: true)").defaultValue(true))
                    .prop("include", ParamSchema.string("Glob filter for filenames (e.g. '*.java')"))
                    .build(),
            null, null, null
    );

    private final FileOperations fileOps;

    public GrepTool(FileOperations fileOps) {
        this.fileOps = fileOps;
    }

    @Override
    public ToolDefinition definition() {
        return DEF;
    }

    @Override
    public ToolResult execute(String toolCallId, Map<String, Object> params, ToolContext ctx) throws Exception {
        ToolParams p = new ToolParams(params);

        String pattern;
        try {
            pattern = p.requireString("pattern");
        } catch (IllegalArgumentException e) {
            return ToolResult.error("missing_param", e.getMessage());
        }

        String path = p.getString("path", fileOps.cwd().toString());
        boolean recursive = !p.has("recursive") || p.getBoolean("recursive", true);
        String include = p.getString("include");

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
            return ToolResult.error("grep_failed", e.getMessage());
        }
    }
}
