package io.agentcore.tools.base;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Tool interface — every tool provides a definition and an execute method.
 */
public interface Tool {
    ToolDefinition definition();

    CompletableFuture<ToolResult> execute(String toolCallId, Map<String, Object> params, ToolContext ctx);
}
