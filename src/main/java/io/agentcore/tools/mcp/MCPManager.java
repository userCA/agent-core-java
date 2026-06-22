package io.agentcore.tools.mcp;

import io.agentcore.tools.ToolDefinition;
import io.agentcore.tools.ToolRegistry;

import java.nio.file.Path;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages multiple MCP server connections and their tool registrations.
 *
 * <p>Mirrors Python {@code agent_core/tools/mcp_tool.py} MCPManager.
 *
 * <p>Usage:
 * <pre>{@code
 * MCPManager manager = MCPManager.fromConfigs(configs);
 * manager.start();
 * manager.registerTools(toolRegistry);
 * // ... agent runs ...
 * manager.stop();
 * }</pre>
 */
public class MCPManager {

    private static final Logger log = LoggerFactory.getLogger(MCPManager.class);

    private final List<MCPServerConfig> configs;
    private final List<MCPConnection> connections = new ArrayList<>();
    private final List<MCPToolAdapter> adapters = new ArrayList<>();

    public MCPManager(List<MCPServerConfig> configs) {
        this.configs = configs != null ? new ArrayList<>(configs) : new ArrayList<>();
    }

    /**
     * Create an MCPManager from .mcp.json or MCP_SERVERS env var.
     */
    public static MCPManager fromEnv() {
        return new MCPManager(MCPConfigParser.loadConfigs());
    }

    /**
     * Create an MCPManager from .mcp.json in the given directory.
     */
    public static MCPManager fromCwd(Path cwd) {
        return new MCPManager(MCPConfigParser.loadConfigs(cwd));
    }

    /**
     * Get all discovered tool adapters.
     */
    public List<MCPToolAdapter> adapters() {
        return List.copyOf(adapters);
    }

    /**
     * Connect to all configured MCP servers and discover their tools.
     */
    public void start() {
        for (MCPServerConfig cfg : configs) {
            MCPConnection conn = null;
            try {
                conn = new MCPConnection(cfg);
                conn.connect();

                List<Map<String, Object>> toolDefs = conn.listTools();
                for (Map<String, Object> td : toolDefs) {
                    adapters.add(new MCPToolAdapter(conn, td, cfg.name()));
                }

                connections.add(conn);
                log.info("MCP server '{}' connected — {} tools discovered", cfg.name(), toolDefs.size());
            } catch (Exception e) {
                log.warn("Failed to connect MCP server '{}'", cfg.name(), e);
                if (conn != null) {
                    try { conn.close(); } catch (Exception e2) {
                        log.debug("Error closing failed MCP connection", e2);
                    }
                }
            }
        }
    }

    /**
     * Close all MCP connections.
     */
    public void stop() {
        for (MCPConnection conn : connections) {
            try {
                conn.close();
            } catch (Exception e) {
                log.warn("Error closing MCP connection", e);
            }
        }
        connections.clear();
        adapters.clear();
    }

    /**
     * Ping all connections. Returns health status for each server.
     */
    public List<Map<String, Object>> checkHealth() {
        List<Map<String, Object>> results = new ArrayList<>();
        for (int i = 0; i < connections.size(); i++) {
            MCPConnection conn = connections.get(i);
            String name = i < configs.size() ? configs.get(i).name() : "conn-" + i;
            boolean healthy = conn.ping();
            results.add(Map.of("name", name, "healthy", healthy));
        }
        return results;
    }

    /**
     * Stop all connections and reload configs from .mcp.json.
     */
    public void reload(Path cwd) {
        stop();
        configs.clear();
        configs.addAll(MCPConfigParser.loadConfigs(cwd));
        start();
    }

    /**
     * Return info about each connected MCP server and its tools.
     */
    public List<Map<String, Object>> getConnectorInfo() {
        Map<String, List<String>> toolsByServer = new LinkedHashMap<>();
        for (MCPToolAdapter adapter : adapters) {
            toolsByServer.computeIfAbsent(adapter.serverName(), k -> new ArrayList<>())
                    .add(adapter.definition().name());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (MCPServerConfig cfg : configs) {
            List<String> tools = toolsByServer.getOrDefault(cfg.name(), List.of());
            String status = !tools.isEmpty() ? "connected" : "error";
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", cfg.name());
            info.put("transport", cfg.transport());
            info.put("status", status);
            info.put("type", cfg.serverType());
            info.put("tools", tools.stream().sorted().toList());
            result.add(info);
        }
        return result;
    }

    /**
     * Register all discovered MCP tools into the given registry.
     *
     * <p>If a tool name conflicts with an existing tool, prefix with server_name
     * to avoid overwriting.
     *
     * @return number of tools registered
     */
    public int registerTools(ToolRegistry registry) {
        int count = 0;
        for (MCPToolAdapter adapter : adapters) {
            String name = adapter.definition().name();
            if (!adapter.serverName().isEmpty() && registry.contains(name)) {
                // Rename on conflict
                String newName = adapter.serverName() + "_" + name;
                adapter.setDefinition(new ToolDefinition(
                        newName,
                        adapter.definition().description(),
                        adapter.definition().parameters()));
            }
            registry.register(adapter);
            count++;
        }
        return count;
    }
}
