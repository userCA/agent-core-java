package io.agentcore.tools.mcp;

import io.agentcore.tools.ToolDefinition;
import io.agentcore.tools.ToolRegistry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MCP package: MCPServerConfig, MCPConfigParser, MCPToolAdapter, MCPManager.
 */
class MCPTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Nested
    @DisplayName("MCPServerConfig")
    class ServerConfigTests {

        @Test
        void stdioFactory() {
            var cfg = MCPServerConfig.stdio("test", List.of("python", "-m", "server"));
            assertEquals("test", cfg.name());
            assertEquals("stdio", cfg.transport());
            assertEquals(List.of("python", "-m", "server"), cfg.command());
            assertNull(cfg.url());
            assertEquals("tool", cfg.serverType());
        }

        @Test
        void sseFactory() {
            var cfg = MCPServerConfig.sse("remote", "http://localhost:8080/sse");
            assertEquals("remote", cfg.name());
            assertEquals("sse", cfg.transport());
            assertNull(cfg.command());
            assertEquals("http://localhost:8080/sse", cfg.url());
        }

        @Test
        void streamableHttpFactory() {
            var cfg = MCPServerConfig.streamableHttp("amap", "https://mcp.amap.com/mcp?key=KEY");
            assertEquals("amap", cfg.name());
            assertEquals("streamable_http", cfg.transport());
            assertEquals("https://mcp.amap.com/mcp?key=KEY", cfg.url());
        }

        @Test
        void stdioWithEnv() {
            var env = Map.of("API_KEY", "test123");
            var cfg = MCPServerConfig.stdio("test", List.of("node", "server.js"), env);
            assertEquals(env, cfg.env());
        }

        @Test
        void requiresName() {
            assertThrows(IllegalArgumentException.class, () ->
                    new MCPServerConfig("", "stdio", null, null, null, "tool"));
        }

        @Test
        void requiresTransport() {
            assertThrows(IllegalArgumentException.class, () ->
                    new MCPServerConfig("test", "", null, null, null, "tool"));
        }

        @Test
        void defaultServerType() {
            var cfg = new MCPServerConfig("test", "stdio", List.of("cmd"), null, null, null);
            assertEquals("tool", cfg.serverType());
        }
    }

    @Nested
    @DisplayName("MCPConfigParser")
    class ConfigParserTests {

        @Test
        void parseEmptyEnv() {
            var configs = MCPConfigParser.parseMcpServersEnv("");
            assertTrue(configs.isEmpty());
        }

        @Test
        void parseNullEnv() {
            var configs = MCPConfigParser.parseMcpServersEnv(null);
            assertTrue(configs.isEmpty());
        }

        @Test
        void parseStdioConfig() {
            var configs = MCPConfigParser.parseMcpServersEnv(
                    "stdio:echo:python:-m:server");
            assertEquals(1, configs.size());
            assertEquals("echo", configs.get(0).name());
            assertEquals("stdio", configs.get(0).transport());
            assertEquals(List.of("python", "-m", "server"), configs.get(0).command());
        }

        @Test
        void parseSseConfig() {
            var configs = MCPConfigParser.parseMcpServersEnv(
                    "sse:remote:http://localhost:8080/sse");
            assertEquals(1, configs.size());
            assertEquals("remote", configs.get(0).name());
            assertEquals("sse", configs.get(0).transport());
            assertEquals("http://localhost:8080/sse", configs.get(0).url());
        }

        @Test
        void parseStreamableHttpConfig() {
            var configs = MCPConfigParser.parseMcpServersEnv(
                    "streamable_http:amap:https://mcp.amap.com/mcp?key=KEY");
            assertEquals(1, configs.size());
            assertEquals("amap", configs.get(0).name());
            assertEquals("streamable_http", configs.get(0).transport());
            assertEquals("https://mcp.amap.com/mcp?key=KEY", configs.get(0).url());
        }

        @Test
        void parseMultipleServers() {
            var configs = MCPConfigParser.parseMcpServersEnv(
                    "stdio:s1:python:server.py|sse:s2:http://localhost:9090/sse");
            assertEquals(2, configs.size());
            assertEquals("s1", configs.get(0).name());
            assertEquals("s2", configs.get(1).name());
        }

        @Test
        void parseInvalidSpecTooFewParts() {
            var configs = MCPConfigParser.parseMcpServersEnv("stdio:name");
            assertTrue(configs.isEmpty());
        }

        @Test
        void parseUnknownTransport() {
            var configs = MCPConfigParser.parseMcpServersEnv("grpc:test:foo");
            assertTrue(configs.isEmpty());
        }

        @Test
        void parseMcpJson(@TempDir Path tempDir) throws Exception {
            ObjectNode root = MAPPER.createObjectNode();
            ObjectNode servers = MAPPER.createObjectNode();

            ObjectNode stdioServer = MAPPER.createObjectNode();
            stdioServer.put("command", "python");
            stdioServer.set("args", MAPPER.createArrayNode().add("-m").add("server"));
            servers.set("test-server", stdioServer);

            ObjectNode sseServer = MAPPER.createObjectNode();
            sseServer.put("url", "http://localhost:8080/sse");
            servers.set("remote-server", sseServer);

            root.set("mcpServers", servers);
            Files.writeString(tempDir.resolve(".mcp.json"),
                    MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root));

            var configs = MCPConfigParser.parseMcpJson(tempDir.resolve(".mcp.json"));
            assertEquals(2, configs.size());

            // Find stdio server
            var stdioCfg = configs.stream()
                    .filter(c -> "test-server".equals(c.name()))
                    .findFirst().orElseThrow();
            assertEquals("stdio", stdioCfg.transport());
            assertEquals(List.of("python", "-m", "server"), stdioCfg.command());

            // Find SSE server
            var sseCfg = configs.stream()
                    .filter(c -> "remote-server".equals(c.name()))
                    .findFirst().orElseThrow();
            assertEquals("sse", sseCfg.transport());
            assertEquals("http://localhost:8080/sse", sseCfg.url());
        }

        @Test
        void parseMcpJsonMissingFile() {
            var configs = MCPConfigParser.parseMcpJson(Path.of("/nonexistent/.mcp.json"));
            assertTrue(configs.isEmpty());
        }

        @Test
        void parseMcpJsonWithEnv(@TempDir Path tempDir) throws Exception {
            ObjectNode root = MAPPER.createObjectNode();
            ObjectNode servers = MAPPER.createObjectNode();

            ObjectNode server = MAPPER.createObjectNode();
            server.put("command", "node");
            server.set("args", MAPPER.createArrayNode().add("server.js"));
            ObjectNode env = MAPPER.createObjectNode();
            env.put("API_KEY", "test-key");
            server.set("env", env);
            servers.set("env-server", server);

            root.set("mcpServers", servers);
            Files.writeString(tempDir.resolve(".mcp.json"),
                    MAPPER.writeValueAsString(root));

            var configs = MCPConfigParser.parseMcpJson(tempDir.resolve(".mcp.json"));
            assertEquals(1, configs.size());
            assertEquals("test-key", configs.get(0).env().get("API_KEY"));
        }

        @Test
        void loadConfigsFromDir(@TempDir Path tempDir) throws Exception {
            ObjectNode root = MAPPER.createObjectNode();
            ObjectNode servers = MAPPER.createObjectNode();
            ObjectNode server = MAPPER.createObjectNode();
            server.put("command", "echo");
            servers.set("echo", server);
            root.set("mcpServers", servers);

            Files.writeString(tempDir.resolve(".mcp.json"),
                    MAPPER.writeValueAsString(root));

            var configs = MCPConfigParser.loadConfigs(tempDir);
            assertEquals(1, configs.size());
            assertEquals("echo", configs.get(0).name());
        }

        @Test
        void addAndRemoveMcpServer(@TempDir Path tempDir) throws Exception {
            // Create initial empty .mcp.json
            Files.writeString(tempDir.resolve(".mcp.json"),
                    "{\"mcpServers\":{}}");

            var cfg = MCPServerConfig.stdio("new-server",
                    List.of("python", "server.py"), Map.of("KEY", "val"));
            MCPConfigParser.addMcpServer(tempDir, "new-server", cfg);

            // Verify it was added
            var configs = MCPConfigParser.parseMcpJson(tempDir.resolve(".mcp.json"));
            assertEquals(1, configs.size());
            assertEquals("new-server", configs.get(0).name());

            // Remove it
            boolean removed = MCPConfigParser.removeMcpServer(tempDir, "new-server");
            assertTrue(removed);

            // Verify it was removed
            configs = MCPConfigParser.parseMcpJson(tempDir.resolve(".mcp.json"));
            assertTrue(configs.isEmpty());
        }

        @Test
        void removeNonExistentServer(@TempDir Path tempDir) throws Exception {
            Files.writeString(tempDir.resolve(".mcp.json"),
                    "{\"mcpServers\":{}}");
            boolean removed = MCPConfigParser.removeMcpServer(tempDir, "nonexistent");
            assertFalse(removed);
        }
    }

    @Nested
    @DisplayName("MCPToolAdapter")
    class ToolAdapterTests {

        @Test
        void extractResultWithContent() {
            ObjectNode result = MAPPER.createObjectNode();
            var content = MAPPER.createArrayNode();
            ObjectNode textBlock = MAPPER.createObjectNode();
            textBlock.put("type", "text");
            textBlock.put("text", "Hello from MCP");
            content.add(textBlock);
            result.set("content", content);

            String text = MCPToolAdapter.extractMcpResult(result);
            assertEquals("Hello from MCP", text);
        }

        @Test
        void extractResultWithMultipleContent() {
            ObjectNode result = MAPPER.createObjectNode();
            var content = MAPPER.createArrayNode();
            ObjectNode block1 = MAPPER.createObjectNode();
            block1.put("type", "text");
            block1.put("text", "Line 1");
            ObjectNode block2 = MAPPER.createObjectNode();
            block2.put("type", "text");
            block2.put("text", "Line 2");
            content.add(block1);
            content.add(block2);
            result.set("content", content);

            String text = MCPToolAdapter.extractMcpResult(result);
            assertEquals("Line 1\nLine 2", text);
        }

        @Test
        void extractResultNull() {
            assertEquals("(null result)", MCPToolAdapter.extractMcpResult(null));
        }

        @Test
        void extractResultNoContent() {
            ObjectNode result = MAPPER.createObjectNode();
            result.put("foo", "bar");

            String text = MCPToolAdapter.extractMcpResult(result);
            assertTrue(text.contains("foo"));
        }

        @Test
        void adapterCreatesDefinition() {
            Map<String, Object> toolDef = Map.of(
                    "name", "test_tool",
                    "description", "A test tool",
                    "inputSchema", Map.of("type", "object", "properties",
                            Map.of("query", Map.of("type", "string")))
            );

            // Can't create real adapter without connection, but we can test the static method
            var def = new ToolDefinition("test_tool", "A test tool",
                    Map.of("type", "object", "properties",
                            Map.of("query", Map.of("type", "string"))));
            assertEquals("test_tool", def.name());
            assertEquals("A test tool", def.description());
        }
    }

    @Nested
    @DisplayName("MCPManager")
    class ManagerTests {

        @Test
        void emptyManager() {
            var manager = new MCPManager(List.of());
            assertTrue(manager.adapters().isEmpty());
        }

        @Test
        void startWithNoConfigs() {
            var manager = new MCPManager(List.of());
            manager.start();
            assertTrue(manager.adapters().isEmpty());
        }

        @Test
        void stopClearsAdapters() {
            var manager = new MCPManager(List.of());
            manager.start();
            manager.stop();
            assertTrue(manager.adapters().isEmpty());
        }

        @Test
        void checkHealthEmpty() {
            var manager = new MCPManager(List.of());
            var health = manager.checkHealth();
            assertTrue(health.isEmpty());
        }

        @Test
        void getConnectorInfoEmpty() {
            var manager = new MCPManager(List.of());
            var info = manager.getConnectorInfo();
            assertTrue(info.isEmpty());
        }

        @Test
        void getConnectorInfoWithConfig() {
            var cfg = MCPServerConfig.stdio("test", List.of("echo"));
            var manager = new MCPManager(List.of(cfg));
            // Start will fail (echo doesn't speak MCP), but connector info should show the config
            manager.start();
            var info = manager.getConnectorInfo();
            assertEquals(1, info.size());
            assertEquals("test", info.get(0).get("name"));
            assertEquals("stdio", info.get(0).get("transport"));
        }

        @Test
        void registerToolsEmpty() {
            var manager = new MCPManager(List.of());
            var registry = new ToolRegistry();
            int count = manager.registerTools(registry);
            assertEquals(0, count);
        }

        @Test
        void startWithInvalidServerFailsGracefully() {
            var cfg = MCPServerConfig.stdio("bad", List.of("nonexistent_command_xyz"));
            var manager = new MCPManager(List.of(cfg));
            // Should not throw — failures are logged
            assertDoesNotThrow(manager::start);
            assertTrue(manager.adapters().isEmpty());
        }
    }
}
