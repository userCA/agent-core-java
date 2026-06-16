package io.agentcore.tools.render;

import io.agentcore.tools.base.ToolResult;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Optional protocol for customizing how tool calls and results are displayed.
 */
public interface ToolRenderer {
    CompletableFuture<String> renderCall(String toolCallId, String name, Map<String, Object> arguments);
    CompletableFuture<RenderedOutput> renderResult(String toolCallId, String name, ToolResult result, boolean isError);
}
