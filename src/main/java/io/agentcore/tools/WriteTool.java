package io.agentcore.tools;

import io.agentcore.tools.util.FileMutationQueue;
import io.agentcore.tools.util.FileOperations;

import java.util.List;
import java.util.Map;

/**
 * File writing tool. Uses atomic write semantics (temp file + move).
 */
public class WriteTool implements Tool {

    private final FileOperations fileOps;
    private final FileMutationQueue mutationQueue;

    public WriteTool(FileOperations fileOps, FileMutationQueue mutationQueue) {
        this.fileOps = fileOps;
        this.mutationQueue = mutationQueue;
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
            if (mutationQueue != null) {
                mutationQueue.writeLocked(fileOps, path, content).join();
            } else {
                fileOps.write(path, content);
            }
            return new ToolResult("File written: " + path);
        } catch (Exception e) {
            return new ToolResult("Error writing file: " + e.getMessage());
        }
    }
}
