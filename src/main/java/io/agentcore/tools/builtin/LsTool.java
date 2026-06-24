package io.agentcore.tools.builtin;

import io.agentcore.tools.ParamSchema;
import io.agentcore.tools.ToolParams;
import io.agentcore.tools.shell.FileInfo;
import io.agentcore.tools.shell.FileOperations;

import java.util.List;
import java.util.Map;
import io.agentcore.model.ToolResult;
import io.agentcore.tools.Tool;
import io.agentcore.tools.ToolContext;
import io.agentcore.tools.ToolDefinition;

/**
 * Directory listing tool.
 */
public class LsTool implements Tool {

    private static final ToolDefinition DEF = new ToolDefinition(
            "ls",
            "List directory contents. Shows [D] for directories and [F] for files.",
            ParamSchema.object()
                    .prop("path", ParamSchema.string("Directory path (default: working directory)"))
                    .build(),
            null, null, null
    );

    private final FileOperations fileOps;

    public LsTool(FileOperations fileOps) {
        this.fileOps = fileOps;
    }

    @Override
    public ToolDefinition definition() {
        return DEF;
    }

    @Override
    public ToolResult execute(String toolCallId, Map<String, Object> params, ToolContext ctx) throws Exception {
        ToolParams p = new ToolParams(params);
        String path = p.getString("path", fileOps.cwd().toString());

        try {
            List<FileInfo> files = fileOps.ls(path);
            StringBuilder sb = new StringBuilder();
            for (var f : files) {
                sb.append(f.isDir() ? "[D] " : "[F] ").append(f.name()).append('\n');
            }
            return new ToolResult(sb.toString());
        } catch (Exception e) {
            return ToolResult.error("ls_failed", e.getMessage());
        }
    }
}
