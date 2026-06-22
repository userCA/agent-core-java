package io.agentcore.tools;

import io.agentcore.tools.util.FileInfo;
import io.agentcore.tools.util.FileOperations;

import java.util.List;
import java.util.Map;

/**
 * Directory listing tool.
 */
public class LsTool implements Tool {

    private final FileOperations fileOps;

    public LsTool(FileOperations fileOps) {
        this.fileOps = fileOps;
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "ls",
                "List directory contents. Shows [D] for directories and [F] for files.",
                Map.of("type", "object", "properties", Map.of(
                        "path", Map.of("type", "string",
                                "description", "Directory path (default: working directory)")
                )),
                null, null, null
        );
    }

    @Override
    public ToolResult execute(String toolCallId, Map<String, Object> params, ToolContext ctx) throws Exception {
        String path = (String) params.getOrDefault("path", fileOps.cwd().toString());

        try {
            List<FileInfo> files = fileOps.ls(path);
            StringBuilder sb = new StringBuilder();
            for (var f : files) {
                sb.append(f.isDir() ? "[D] " : "[F] ").append(f.name()).append('\n');
            }
            return new ToolResult(sb.toString());
        } catch (Exception e) {
            return new ToolResult("Error listing directory: " + e.getMessage());
        }
    }
}
