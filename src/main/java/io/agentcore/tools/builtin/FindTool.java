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
 * File finding tool by name pattern.
 */
public class FindTool implements Tool {

    private static final int MAX_DISPLAY_RESULTS = 500;

    private static final ToolDefinition DEF = new ToolDefinition(
            "find",
            "Find files by name pattern (glob). "
                    + "Searches recursively up to 20 levels deep.",
            ParamSchema.object()
                    .prop("pattern", ParamSchema.string("Filename glob pattern (e.g. '*.java', 'pom.xml')").required())
                    .prop("path", ParamSchema.string("Root directory (default: working directory)"))
                    .build(),
            null, null, null
    );

    private final FileOperations fileOps;

    public FindTool(FileOperations fileOps) {
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
            return ToolResult.error("find_failed", e.getMessage());
        }
    }
}
