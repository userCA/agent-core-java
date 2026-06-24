package io.agentcore.tools.builtin;

import io.agentcore.tools.shell.FileOperations;

import java.util.List;
import java.util.Map;
import io.agentcore.model.ToolResult;
import io.agentcore.tools.Tool;
import io.agentcore.tools.ToolContext;
import io.agentcore.tools.ToolDefinition;

/**
 * File writing tool. Uses atomic write semantics (temp file + move).
 */
public class WriteTool implements Tool {

    private final FileOperations fileOps;

    public WriteTool(FileOperations fileOps) {
        this.fileOps = fileOps;
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "write",
                "Write content to a file. Creates parent directories if needed. "
                        + "Uses atomic write (temp + move) for safety.",
                Map.of("type", "object", "properties", Map.of(
                        "path", Map.of("type", "string",
                                "description", "File path"),
                        "content", Map.of("type", "string",
                                "description", "Full file content")
                ), "required", List.of("path", "content")),
                null, null, null
        );
    }

    @Override
    public ToolResult execute(String toolCallId, Map<String, Object> params, ToolContext ctx) throws Exception {
        String path = (String) params.get("path");
        String content = (String) params.get("content");
        if (path == null || path.isBlank()) {
            return new ToolResult("ERROR: 'path' parameter is required");
        }
        if (content == null) {
            return new ToolResult("ERROR: 'content' parameter is required");
        }

        try {
            fileOps.write(path, content);
            return new ToolResult("File written: " + path);
        } catch (Exception e) {
            return new ToolResult("Error writing file: " + e.getMessage());
        }
    }
}
