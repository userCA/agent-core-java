package io.agentcore.tools.mcp;

import java.util.List;
import java.util.Map;

/**
 * Configuration for a single MCP server.
 *
 * <p>Mirrors Python {@code agent_core/tools/mcp_tool.py} MCPServerConfig.
 *
 * @param name        server identifier
 * @param transport   "stdio" | "sse" | "streamable_http"
 * @param command     command + args for stdio transport (nullable)
 * @param url         URL for SSE / streamable_http transports (nullable)
 * @param env         extra environment variables (nullable)
 * @param serverType  "tool" | "knowledge"
 */
public record MCPServerConfig(
        String name,
        String transport,
        List<String> command,
        String url,
        Map<String, String> env,
        String serverType
) {
    public MCPServerConfig {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name required");
        if (transport == null || transport.isBlank()) throw new IllegalArgumentException("transport required");
        if (serverType == null || serverType.isBlank()) serverType = "tool";
    }

    /**
     * Convenience constructor for stdio transport.
     */
    public static MCPServerConfig stdio(String name, List<String> command) {
        return new MCPServerConfig(name, "stdio", command, null, null, "tool");
    }

    /**
     * Convenience constructor for stdio transport with env.
     */
    public static MCPServerConfig stdio(String name, List<String> command, Map<String, String> env) {
        return new MCPServerConfig(name, "stdio", command, null, env, "tool");
    }

    /**
     * Convenience constructor for SSE transport.
     */
    public static MCPServerConfig sse(String name, String url) {
        return new MCPServerConfig(name, "sse", null, url, null, "tool");
    }

    /**
     * Convenience constructor for streamable HTTP transport.
     */
    public static MCPServerConfig streamableHttp(String name, String url) {
        return new MCPServerConfig(name, "streamable_http", null, url, null, "tool");
    }
}
