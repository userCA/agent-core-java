package io.agentcore.tools.builtin;

import io.agentcore.model.Content.TextContent;
import io.agentcore.tools.ParamSchema;
import io.agentcore.tools.ToolParams;
import io.agentcore.tools.shell.FileOperations;
import io.agentcore.tools.shell.Truncation;

import java.nio.file.NoSuchFileException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import io.agentcore.model.ToolResult;
import io.agentcore.tools.Tool;
import io.agentcore.tools.ToolContext;
import io.agentcore.tools.ToolDefinition;

/**
 * File reading tool.
 */
public class ReadTool implements Tool {

    private static final int DEFAULT_MAX_BYTES = 32000;
    private static final int DEFAULT_MAX_LINES = 500;

    private static final ToolDefinition DEF = new ToolDefinition(
            "read",
            "Read file contents with optional offset and limit. "
                    + "Output is automatically truncated if too large.",
            ParamSchema.object()
                    .prop("path", ParamSchema.string("File path").required())
                    .prop("offset", ParamSchema.integer("0-indexed line offset (default 0)").defaultValue(0))
                    .prop("limit", ParamSchema.integer("Max lines to read"))
                    .build(),
            null, null, null
    );

    private final FileOperations fileOps;
    private final int maxBytes;
    private final int maxLines;

    public ReadTool(FileOperations fileOps) {
        this(fileOps, DEFAULT_MAX_BYTES, DEFAULT_MAX_LINES);
    }

    public ReadTool(FileOperations fileOps, int maxBytes, int maxLines) {
        this.fileOps = fileOps;
        this.maxBytes = maxBytes;
        this.maxLines = maxLines;
    }

    @Override
    public ToolDefinition definition() {
        return DEF;
    }

    @Override
    public ToolResult execute(String toolCallId, Map<String, Object> params, ToolContext ctx) throws Exception {
        ToolParams p = new ToolParams(params);

        String path;
        try {
            path = p.requireString("path");
        } catch (IllegalArgumentException e) {
            return ToolResult.error("missing_param", e.getMessage());
        }

        int offset = p.getInt("offset", 0);
        Integer limit = p.has("limit") ? p.getInt("limit", -1) : null;
        if (limit != null && limit == -1) limit = null;

        try {
            String content = fileOps.read(path, offset, limit);

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
            return ToolResult.error("not_found", "File not found: " + path);
        } catch (java.nio.file.FileSystemException e) {
            if (e.getMessage() != null && e.getMessage().contains("Is a directory")) {
                return ToolResult.error("is_directory", "Path is a directory: " + path);
            }
            return ToolResult.error("read_failed",
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        } catch (Exception e) {
            return ToolResult.error("read_failed",
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }
}
