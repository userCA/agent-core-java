package io.agentcore.tools;

import io.agentcore.core.Content.TextContent;
import io.agentcore.tools.util.FileMutationQueue;
import io.agentcore.tools.util.FileOperations;
import io.agentcore.tools.util.Truncation;

import java.nio.file.NoSuchFileException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * File reading tool.
 */
public class ReadTool implements Tool {

    private static final int DEFAULT_MAX_BYTES = 32000;
    private static final int DEFAULT_MAX_LINES = 500;

    private final FileOperations fileOps;
    private final FileMutationQueue mutationQueue;
    private final int maxBytes;
    private final int maxLines;

    public ReadTool(FileOperations fileOps, FileMutationQueue mutationQueue) {
        this(fileOps, mutationQueue, DEFAULT_MAX_BYTES, DEFAULT_MAX_LINES);
    }

    public ReadTool(FileOperations fileOps, FileMutationQueue mutationQueue,
                    int maxBytes, int maxLines) {
        this.fileOps = fileOps;
        this.mutationQueue = mutationQueue;
        this.maxBytes = maxBytes;
        this.maxLines = maxLines;
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "read",
                "Read file contents with optional offset and limit. "
                        + "Output is automatically truncated if too large.",
                Map.of("type", "object", "properties", Map.of(
                        "path", Map.of("type", "string",
                                "description", "File path"),
                        "offset", Map.of("type", "integer",
                                "description", "0-indexed line offset (default 0)"),
                        "limit", Map.of("type", "integer",
                                "description", "Max lines to read")
                ), "required", List.of("path")),
                null, null, null
        );
    }

    @Override
    public ToolResult execute(String toolCallId, Map<String, Object> params, ToolContext ctx) throws Exception {
        String path = (String) params.get("path");
        if (path == null || path.isBlank()) {
            return new ToolResult("ERROR: 'path' parameter is required");
        }

        Object offsetObj = params.get("offset");
        int offset = offsetObj instanceof Number n ? n.intValue() : 0;
        Object limitObj = params.get("limit");
        Integer limit = limitObj instanceof Number n ? n.intValue() : null;

        try {
            String content;
            if (mutationQueue != null) {
                content = mutationQueue.readLocked(fileOps, path).join();
            } else {
                content = fileOps.read(path, offset, limit);
            }

            boolean truncated = false;
            String[] lines = content.split("\n", -1);
            if (lines.length > maxLines) {
                content = String.join("\n",
                        java.util.Arrays.asList(lines).subList(0, maxLines))
                        + "\n... (truncated)";
                truncated = true;
            } else if (content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > maxBytes) {
                content = Truncation.truncateTail(content, maxBytes, "... (truncated)");
                truncated = true;
            }

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("truncated", truncated);
            details.put("path", path);

            return new ToolResult(
                    List.of(new TextContent(content)),
                    details, null);
        } catch (NoSuchFileException e) {
            return new ToolResult(
                    List.of(new TextContent("File not found: " + path)),
                    Map.of("error", "not_found"), null);
        } catch (java.nio.file.FileSystemException e) {
            if (e.getMessage() != null && e.getMessage().contains("Is a directory")) {
                return new ToolResult(
                        List.of(new TextContent("Path is a directory: " + path)),
                        Map.of("error", "is_directory"), null);
            }
            return new ToolResult(
                    List.of(new TextContent(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())),
                    Map.of("error", "read_failed"), null);
        } catch (Exception e) {
            return new ToolResult(
                    List.of(new TextContent(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())),
                    Map.of("error", "read_failed"), null);
        }
    }
}
