package io.agentcore.tools.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parse MCP server configurations from .mcp.json files or MCP_SERVERS env var.
 *
 * <p>Mirrors Python {@code parse_mcp_servers()}, {@code parse_mcp_json()},
 * {@code load_mcp_server_configs()}, and related functions in mcp_tool.py.
 */
public final class MCPConfigParser {

    private static final Logger log = LoggerFactory.getLogger(MCPConfigParser.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MCPConfigParser() {}

    /**
     * Parse MCP_SERVERS env string into server configs.
     *
     * <p>Format: {@code stdio:name:cmd:arg1:arg2|sse:name:url|streamable_http:name:url}
     */
    public static List<MCPServerConfig> parseMcpServersEnv(String raw) {
        List<MCPServerConfig> servers = new ArrayList<>();
        if (raw == null || raw.isBlank()) return servers;

        for (String spec : raw.strip().split("\\|")) {
            String[] parts = spec.strip().split(":");
            if (parts.length < 3) {
                log.warn("Invalid MCP server spec (too few parts): {}", spec);
                continue;
            }

            String transport = parts[0].strip();
            String name = parts[1].strip();

            switch (transport) {
                case "stdio" -> {
                    List<String> cmd = new ArrayList<>();
                    for (int i = 2; i < parts.length; i++) cmd.add(parts[i].strip());
                    servers.add(new MCPServerConfig(name, "stdio", cmd, null, null, "tool"));
                }
                case "sse", "streamable_http" -> {
                    // Rejoin remaining parts (URL contains colons)
                    StringBuilder urlBuilder = new StringBuilder();
                    for (int i = 2; i < parts.length; i++) {
                        if (i > 2) urlBuilder.append(":");
                        urlBuilder.append(parts[i].strip());
                    }
                    servers.add(new MCPServerConfig(name, transport, null,
                            urlBuilder.toString(), null, "tool"));
                }
                default -> log.warn("Unknown MCP transport '{}' for server '{}'", transport, name);
            }
        }
        return servers;
    }

    /**
     * Parse a Claude Desktop-style .mcp.json config file.
     *
     * <p>Format: {@code {"mcpServers": {"name": {"command": "...", "args": [...], "env": {...}, "url": "..."}}}}
     */
    public static List<MCPServerConfig> parseMcpJson(Path path) {
        try {
            JsonNode root = MAPPER.readTree(Files.readString(path));
            JsonNode mcpServers = root.get("mcpServers");
            if (mcpServers == null || !mcpServers.isObject()) return List.of();

            List<MCPServerConfig> servers = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> fields = mcpServers.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String name = entry.getKey();
                JsonNode cfg = entry.getValue();
                if (!cfg.isObject()) continue;

                String command = cfg.has("command") ? cfg.get("command").asText("") : "";
                String url = cfg.has("url") ? cfg.get("url").asText(null) : null;
                String serverType = cfg.has("type") ? cfg.get("type").asText("tool") : "tool";

                // Parse args
                List<String> args = new ArrayList<>();
                if (cfg.has("args") && cfg.get("args").isArray()) {
                    for (JsonNode arg : cfg.get("args")) {
                        args.add(arg.asText());
                    }
                }

                // Parse env
                Map<String, String> env = null;
                if (cfg.has("env") && cfg.get("env").isObject()) {
                    env = new LinkedHashMap<>();
                    Iterator<Map.Entry<String, JsonNode>> envFields = cfg.get("env").fields();
                    while (envFields.hasNext()) {
                        Map.Entry<String, JsonNode> e = envFields.next();
                        env.put(e.getKey(), e.getValue().asText());
                    }
                }

                if (url != null && !url.isEmpty()) {
                    servers.add(new MCPServerConfig(name, "sse", null, url, env, serverType));
                } else if (!command.isEmpty()) {
                    List<String> fullCmd = new ArrayList<>();
                    fullCmd.add(command);
                    fullCmd.addAll(args);
                    servers.add(new MCPServerConfig(name, "stdio", fullCmd, null, env, serverType));
                }
            }
            return servers;
        } catch (Exception e) {
            log.debug("Failed to parse .mcp.json: {}", path, e);
            return List.of();
        }
    }

    /**
     * Load MCP server configs from .mcp.json (cwd), then fall back to MCP_SERVERS env var.
     */
    public static List<MCPServerConfig> loadConfigs() {
        return loadConfigs(Path.of(System.getProperty("user.dir")));
    }

    /**
     * Load MCP server configs from .mcp.json in the given directory, then fall back to env var.
     */
    public static List<MCPServerConfig> loadConfigs(Path cwd) {
        Path jsonPath = cwd.resolve(".mcp.json");
        if (Files.exists(jsonPath)) {
            List<MCPServerConfig> configs = parseMcpJson(jsonPath);
            if (!configs.isEmpty()) return configs;
        }
        // Fall back to env var
        return parseMcpServersEnv(System.getenv("MCP_SERVERS"));
    }

    /**
     * Read raw .mcp.json content as a JsonNode for editing.
     */
    public static JsonNode readMcpJsonRaw(Path cwd) {
        Path jsonPath = cwd.resolve(".mcp.json");
        try {
            return MAPPER.readTree(Files.readString(jsonPath));
        } catch (Exception e) {
            return MAPPER.createObjectNode().set("mcpServers", MAPPER.createObjectNode());
        }
    }

    /**
     * Write .mcp.json file with the given data.
     */
    public static void writeMcpJsonRaw(JsonNode data, Path cwd) throws Exception {
        Path jsonPath = cwd.resolve(".mcp.json");
        Files.writeString(jsonPath, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(data) + "\n");
    }

    /**
     * Add or update an MCP server entry in .mcp.json.
     */
    public static void addMcpServer(Path cwd, String name, MCPServerConfig config) throws Exception {
        JsonNode root = readMcpJsonRaw(cwd);
        if (!root.isObject()) {
            root = MAPPER.createObjectNode();
        }
        var obj = (com.fasterxml.jackson.databind.node.ObjectNode) root;
        if (!obj.has("mcpServers")) {
            obj.set("mcpServers", MAPPER.createObjectNode());
        }
        var servers = (com.fasterxml.jackson.databind.node.ObjectNode) obj.get("mcpServers");

        var cfg = MAPPER.createObjectNode();
        if ("stdio".equals(config.transport()) && config.command() != null && !config.command().isEmpty()) {
            cfg.put("command", config.command().get(0));
            if (config.command().size() > 1) {
                var argsArr = MAPPER.createArrayNode();
                for (int i = 1; i < config.command().size(); i++) argsArr.add(config.command().get(i));
                cfg.set("args", argsArr);
            }
        } else if (config.url() != null) {
            cfg.put("url", config.url());
        }
        if (config.env() != null && !config.env().isEmpty()) {
            var envNode = MAPPER.createObjectNode();
            config.env().forEach(envNode::put);
            cfg.set("env", envNode);
        }
        if (!"tool".equals(config.serverType())) {
            cfg.put("type", config.serverType());
        }

        servers.set(name, cfg);
        writeMcpJsonRaw(root, cwd);
    }

    /**
     * Remove an MCP server entry from .mcp.json. Returns true if found.
     */
    public static boolean removeMcpServer(Path cwd, String name) throws Exception {
        JsonNode root = readMcpJsonRaw(cwd);
        JsonNode servers = root.get("mcpServers");
        if (servers == null || !servers.has(name)) return false;
        ((com.fasterxml.jackson.databind.node.ObjectNode) servers).remove(name);
        writeMcpJsonRaw(root, cwd);
        return true;
    }
}
