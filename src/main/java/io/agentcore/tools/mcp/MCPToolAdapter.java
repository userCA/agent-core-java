package io.agentcore.tools.mcp;

import io.agentcore.core.Content.TextContent;
import io.agentcore.tools.Tool;
import io.agentcore.tools.ToolContext;
import io.agentcore.tools.ToolDefinition;
import io.agentcore.tools.ToolResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps a single MCP server tool as an agent_core Tool.
 *
 * <p>Mirrors Python {@code agent_core/tools/mcp_tool.py} MCPToolAdapter.
 */
public class MCPToolAdapter implements Tool {

    private static final Logger log = LoggerFactory.getLogger(MCPToolAdapter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MCPConnection connection;
    private final Map<String, Object> toolDef;
    private final String serverName;
    private ToolDefinition definition;

    public MCPToolAdapter(MCPConnection connection, Map<String, Object> toolDef, String serverName) {
        this.connection = connection;
        this.toolDef = toolDef;
        this.serverName = serverName;

        String name = (String) toolDef.get("name");
        String description = (String) toolDef.getOrDefault("description", "");
        @SuppressWarnings("unchecked")
        Map<String, Object> parameters = (Map<String, Object>) toolDef.getOrDefault(
                "inputSchema", Map.of("type", "object", "properties", Map.of()));

        this.definition = new ToolDefinition(name, description, parameters);
    }

    @Override
    public ToolDefinition definition() {
        return definition;
    }

    /**
     * Replace the definition (used by MCPManager for conflict resolution).
     */
    public void setDefinition(ToolDefinition newDef) {
        this.definition = newDef;
    }

    /**
     * Get the server name this tool belongs to.
     */
    public String serverName() {
        return serverName;
    }

    @Override
    public ToolResult execute(String toolCallId, Map<String, Object> params, ToolContext ctx) throws Exception {
        try {
            String originalName = (String) toolDef.get("name");
            JsonNode result = connection.callTool(originalName, params);
            String text = extractMcpResult(result);
            return new ToolResult(List.of(new TextContent(text)));
        } catch (Exception e) {
            return new ToolResult("MCP tool error: " + e.getMessage());
        }
    }

    /**
     * Convert an MCP CallToolResult to a plain-text string.
     */
    static String extractMcpResult(JsonNode result) {
        if (result == null) return "(null result)";

        JsonNode content = result.get("content");
        if (content != null && content.isArray()) {
            List<String> parts = new ArrayList<>();
            for (JsonNode block : content) {
                if (block.has("text")) {
                    parts.add(block.get("text").asText());
                } else {
                    parts.add(block.toString());
                }
            }
            return String.join("\n", parts);
        }
        return result.toString();
    }
}
