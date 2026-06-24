package io.agentcore.tools.builtin;

import io.agentcore.tools.ParamSchema;
import io.agentcore.tools.ToolParams;
import io.agentcore.tools.shell.FileOperations;

import java.util.Map;
import io.agentcore.model.ToolResult;
import io.agentcore.tools.Tool;
import io.agentcore.tools.ToolContext;
import io.agentcore.tools.ToolDefinition;

/**
 * File writing tool. Uses atomic write semantics (temp file + move).
 */
public class WriteTool implements Tool {

    private static final ToolDefinition DEF = new ToolDefinition(
            "write",
            "Write content to a file. Creates parent directories if needed. "
                    + "Uses atomic write (temp + move) for safety.",
            ParamSchema.object()
                    .prop("path", ParamSchema.string("File path").required())
                    .prop("content", ParamSchema.string("Full file content").required())
                    .build(),
            null, null, null
    );

    private final FileOperations fileOps;

    public WriteTool(FileOperations fileOps) {
        this.fileOps = fileOps;
    }

    @Override
    public ToolDefinition definition() {
        return DEF;
    }

    @Override
    public ToolResult execute(String toolCallId, Map<String, Object> params, ToolContext ctx) throws Exception {
        ToolParams p = new ToolParams(params);

        String path;
        String content;
        try {
            path = p.requireString("path");
            content = p.requireString("content");
        } catch (IllegalArgumentException e) {
            return ToolResult.error("missing_param", e.getMessage());
        }

        try {
            fileOps.write(path, content);
            return new ToolResult("File written: " + path);
        } catch (Exception e) {
            return ToolResult.error("write_failed", e.getMessage());
        }
    }
}
