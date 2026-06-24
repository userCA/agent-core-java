package io.agentcore.tools.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages a single MCP server connection lifecycle.
 *
 * <p>Mirrors Python {@code agent_core/tools/mcp_tool.py} MCPConnection.
 * Supports stdio and SSE/streamable_http transports via JSON-RPC 2.0.
 */
public class MCPConnection {

    private static final Logger log = LoggerFactory.getLogger(MCPConnection.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final MCPServerConfig config;
    private final int initTimeout;

    // Stdio transport state
    private Process stdioProcess;
    private OutputStream stdioIn;
    private BufferedReader stdioOut;
    private Thread stdioReaderThread;
    private final Map<Integer, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();
    private final AtomicInteger requestIdCounter = new AtomicInteger(1);

    // SSE / streamable_http transport state
    private HttpClient httpClient;
    private String sseUrl;
    private String messagesUrl; // endpoint to POST JSON-RPC requests (for SSE)

    private volatile boolean connected = false;

    public MCPConnection(MCPServerConfig config) {
        this(config, 30);
    }

    public MCPConnection(MCPServerConfig config, int initTimeoutSeconds) {
        this.config = config;
        this.initTimeout = initTimeoutSeconds;
    }

    /**
     * Connect to the MCP server and initialize the session.
     */
    public void connect() throws Exception {
        switch (config.transport()) {
            case "stdio" -> connectStdio();
            case "sse" -> connectSse();
            case "streamable_http" -> connectStreamableHttp();
            default -> throw new IllegalArgumentException("Unknown transport: " + config.transport());
        }

        // Initialize the MCP session
        initialize();
        connected = true;
    }

    /**
     * Check if the connection is active.
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Health check — try listing tools. Returns true if healthy.
     */
    public boolean ping() {
        try {
            listTools();
            return true;
        } catch (Exception e) {
            log.debug("MCP ping failed", e);
            return false;
        }
    }

    /**
     * Close the MCP server connection.
     */
    public void close() {
        connected = false;
        if (stdioProcess != null) {
            stdioProcess.destroyForcibly();
            stdioProcess = null;
        }
        if (stdioIn != null) {
            try { stdioIn.close(); } catch (Exception e) {
                log.debug("Error closing stdioIn", e);
            }
        }
        if (stdioOut != null) {
            try { stdioOut.close(); } catch (Exception e) {
                log.debug("Error closing stdioOut", e);
            }
        }
        if (stdioReaderThread != null && stdioReaderThread.isAlive()) {
            stdioReaderThread.interrupt();
        }
        if (httpClient != null) {
            httpClient.close();
            httpClient = null;
        }
        pendingRequests.values().forEach(f -> f.completeExceptionally(
                new IOException("Connection closed")));
        pendingRequests.clear();
    }

    /**
     * List available tools from the MCP server.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listTools() throws Exception {
        JsonNode result = sendRequest("tools/list", MAPPER.createObjectNode());
        JsonNode tools = result.get("tools");
        if (tools == null || !tools.isArray()) return List.of();

        List<Map<String, Object>> list = new ArrayList<>();
        for (JsonNode t : tools) {
            list.add(MAPPER.convertValue(t, Map.class));
        }
        return list;
    }

    /**
     * Call a tool on the MCP server.
     */
    public JsonNode callTool(String name, Map<String, Object> arguments) throws Exception {
        ObjectNode params = MAPPER.createObjectNode();
        params.put("name", name);
        params.set("arguments", MAPPER.valueToTree(arguments));
        return sendRequest("tools/call", params);
    }

    // -----------------------------------------------------------------------
    // Transport implementations
    // -----------------------------------------------------------------------

    private void connectStdio() throws Exception {
        if (config.command() == null || config.command().isEmpty()) {
            throw new IllegalArgumentException("stdio transport requires 'command'");
        }

        ProcessBuilder pb = new ProcessBuilder(config.command());
        pb.redirectErrorStream(false);

        // Set environment
        if (config.env() != null) {
            pb.environment().putAll(config.env());
        }

        stdioProcess = pb.start();
        stdioIn = stdioProcess.getOutputStream();
        stdioOut = new BufferedReader(new InputStreamReader(stdioProcess.getInputStream(), StandardCharsets.UTF_8));

        // Background reader thread for responses
        stdioReaderThread = Thread.ofVirtual().name("mcp-stdio-" + config.name()).start(() -> {
            try {
                String line;
                while ((line = stdioOut.readLine()) != null) {
                    try {
                        JsonNode msg = MAPPER.readTree(line);
                        JsonNode idNode = msg.get("id");
                        if (idNode != null && !idNode.isNull()) {
                            int id = idNode.asInt();
                            CompletableFuture<JsonNode> future = pendingRequests.remove(id);
                            if (future != null) {
                                JsonNode error = msg.get("error");
                                if (error != null && !error.isNull()) {
                                    future.completeExceptionally(new IOException(
                                            "JSON-RPC error: " + error.get("message").asText("unknown")));
                                } else {
                                    future.complete(msg.get("result"));
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.debug("Failed to parse MCP response: {}", line, e);
                    }
                }
            } catch (IOException e) {
                // Process ended
                pendingRequests.values().forEach(f -> f.completeExceptionally(e));
                pendingRequests.clear();
            }
        });

        // Drain stderr to prevent OS pipe buffer from filling up and deadlocking the MCP server
        Thread.ofVirtual().name("mcp-stderr-" + config.name()).start(() -> {
            try (var es = stdioProcess.getErrorStream()) {
                byte[] buf = new byte[4096];
                while (es.read(buf) != -1) { /* drain */ }
            } catch (IOException e) {
                log.debug("MCP stderr reader exited", e);
            }
        });
    }

    private void connectSse() throws Exception {
        if (config.url() == null) {
            throw new IllegalArgumentException("SSE transport requires 'url'");
        }
        httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        sseUrl = config.url();
        messagesUrl = sseUrl.replace("/sse", "/messages");
        if (messagesUrl.equals(sseUrl)) {
            messagesUrl = sseUrl;
        }
        // NOTE: SSE transport requires an active SSE stream to receive responses.
        // Currently only POST-based request/response works via sendHttpRequest().
        // Full bidirectional SSE support is planned for a future release.
        log.info("MCP SSE transport initialized (POST-only mode) for url={}", config.url());
    }

    private void connectStreamableHttp() throws Exception {
        if (config.url() == null) {
            throw new IllegalArgumentException("streamable_http transport requires 'url'");
        }
        httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        sseUrl = config.url();
        messagesUrl = config.url(); // streamable_http uses the same URL for POST
    }

    private void initialize() throws Exception {
        ObjectNode initParams = MAPPER.createObjectNode();
        ObjectNode clientInfo = MAPPER.createObjectNode();
        clientInfo.put("name", "agent-core-java");
        clientInfo.put("version", "1.0.0");
        initParams.set("clientInfo", clientInfo);
        ObjectNode capabilities = MAPPER.createObjectNode();
        initParams.set("capabilities", capabilities);

        sendRequest("initialize", initParams);

        // Send initialized notification (no response expected)
        if ("stdio".equals(config.transport())) {
            sendNotification("notifications/initialized", MAPPER.createObjectNode());
        }
    }

    // -----------------------------------------------------------------------
    // JSON-RPC 2.0 communication
    // -----------------------------------------------------------------------

    private JsonNode sendRequest(String method, ObjectNode params) throws Exception {
        if ("stdio".equals(config.transport())) {
            return sendStdioRequest(method, params);
        } else {
            return sendHttpRequest(method, params);
        }
    }

    private JsonNode sendStdioRequest(String method, ObjectNode params) throws Exception {
        int id = requestIdCounter.getAndIncrement();
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pendingRequests.put(id, future);

        ObjectNode msg = MAPPER.createObjectNode();
        msg.put("jsonrpc", "2.0");
        msg.put("id", id);
        msg.put("method", method);
        msg.set("params", params);

        String line = MAPPER.writeValueAsString(msg) + "\n";
        synchronized (stdioIn) {
            stdioIn.write(line.getBytes(StandardCharsets.UTF_8));
            stdioIn.flush();
        }

        return future.get(REQUEST_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    }

    private void sendNotification(String method, ObjectNode params) throws Exception {
        if (!"stdio".equals(config.transport())) return;

        ObjectNode msg = MAPPER.createObjectNode();
        msg.put("jsonrpc", "2.0");
        msg.put("method", method);
        msg.set("params", params);

        String line = MAPPER.writeValueAsString(msg) + "\n";
        synchronized (stdioIn) {
            stdioIn.write(line.getBytes(StandardCharsets.UTF_8));
            stdioIn.flush();
        }
    }

    private JsonNode sendHttpRequest(String method, ObjectNode params) throws Exception {
        int id = requestIdCounter.getAndIncrement();

        ObjectNode msg = MAPPER.createObjectNode();
        msg.put("jsonrpc", "2.0");
        msg.put("id", id);
        msg.put("method", method);
        msg.set("params", params);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(messagesUrl))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(msg)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("HTTP error " + response.statusCode() + ": " + response.body());
        }

        JsonNode result = MAPPER.readTree(response.body());
        JsonNode error = result.get("error");
        if (error != null && !error.isNull()) {
            throw new IOException("JSON-RPC error: " + error.get("message").asText("unknown"));
        }
        return result.get("result");
    }
}
