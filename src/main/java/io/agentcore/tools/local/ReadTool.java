package io.agentcore.tools.local;

import io.agentcore.core.content.TextContent;
import io.agentcore.tools.base.*;
import io.agentcore.tools.operations.FileOperations;
import io.agentcore.tools.truncate.Truncate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ReadTool implements Tool {
    private final String cwd;
    private final FileOperations fileOps;
    private final int maxBytes;
    private final int maxLines;

    public ReadTool(String cwd, FileOperations fileOps) {
        this(cwd, fileOps, 32000, 500);
    }

    public ReadTool(String cwd, FileOperations fileOps, int maxBytes, int maxLines) {
        this.cwd = cwd;
        this.fileOps = fileOps;
        this.maxBytes = maxBytes;
        this.maxLines = maxLines;
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition("read", "Read file contents with optional offset and limit.",
                Map.of("type", "object", "properties", Map.of(
                        "path", Map.of("type", "string", "description", "File path"),
                        "offset", Map.of("type", "integer", "description", "0-indexed line offset"),
                        "limit", Map.of("type", "integer", "description", "Max lines to read")
                ), "required", List.of("path")),
                "Read file contents",
                List.of("Output is automatically truncated if too large."));
    }

    @Override
    public CompletableFuture<ToolResult> execute(String toolCallId, Map<String, Object> params, ToolContext ctx) {
        String path = (String) params.get("path");
        int offset = params.containsKey("offset") ? ((Number) params.get("offset")).intValue() : 0;
        Integer limit = params.containsKey("limit") ? ((Number) params.get("limit")).intValue() : null;

        return fileOps.read(path, offset, limit).thenApply(content -> {
            String truncated = Truncate.truncateLine(content, maxLines);
            truncated = Truncate.truncateTail(truncated, maxBytes);
            // Detect both ReadTool-level and upstream (FileOperations) truncation
            boolean wasTruncated = !truncated.equals(content) || content.contains("... (truncated at");
            return new ToolResult(
                    List.of(new TextContent(truncated)),
                    Map.of("path", path, "truncated", wasTruncated)
            );
        }).exceptionally(e -> new ToolResult(
                List.of(new TextContent("Error reading file: " + e.getMessage()))));
    }
}
