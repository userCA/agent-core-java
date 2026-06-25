package io.agentcore.app.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentcore.agent.Agent;
import io.agentcore.agent.AgentContext;
import io.agentcore.agent.AgentLoopConfig;
import io.agentcore.model.Message;
import io.agentcore.model.Message.*;
import io.agentcore.model.Content;
import io.agentcore.llm.*;
import io.agentcore.tools.ToolRegistry;

import org.junit.jupiter.api.*;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for AgentHttpServer SSE endpoints.
 */
class AgentHttpServerTest {

    private static AgentHttpServer server;
    private static int port;

    /**
     * Mock provider that emits a canned streaming response.
     */
    static Iterator<StreamEvent> mockStream(
            ModelInfo model, List<Map<String, Object>> messages,
            List<Map<String, Object>> tools, String systemPrompt,
            String thinkingLevel, Double temperature, Integer maxTokens,
            java.util.concurrent.atomic.AtomicBoolean signal, ProviderAuth auth) {

        List<StreamEvent> events = List.of(
                new StreamEvent.StreamTextDelta("Hello "),
                new StreamEvent.StreamTextDelta("world!"),
                new StreamEvent.StreamMessageEnd("stop", 10, 5)
        );
        return events.iterator();
    }

    @BeforeAll
    static void startServer() throws Exception {
        port = findFreePort();

        ModelInfo model = new ModelInfo("mock", "mock-model", 128000, 4096);
        AgentLoopConfig config = AgentLoopConfig.builder()
                .model(model)
                .streamFn(AgentHttpServerTest::mockStream)
                .messageAssembler(new MessageConverter()::convert)
                .authResolver(p -> new ProviderAuth("mock-key"))
                .build();

        String tmpDir = System.getProperty("java.io.tmpdir") + "/agent-test-sessions-" + port;

        server = AgentHttpServer.builder()
                .port(port)
                .agentFactory(() -> new Agent(config, new AgentContext()))
                .sessionDir(tmpDir)
                .build();
        server.start();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) server.close();
    }

    @Test
    void healthCheck() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/health"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("\"status\":\"ok\""));
    }

    @Test
    void sessionsListEmpty() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/sessions"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("\"sessions\""));
    }

    @Test
    void chatSseStreaming() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String body = new ObjectMapper().writeValueAsString(
                new ChatRequest("Hello agent", "test-session-1"));

        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/chat"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
        String responseBody = resp.body();

        // Verify SSE format: should contain event: and data: lines
        assertTrue(responseBody.contains("event: agent_start"), "Missing agent_start event");
        assertTrue(responseBody.contains("event: message_update"), "Missing message_update events");
        assertTrue(responseBody.contains("event: message_end"), "Missing message_end event");
        assertTrue(responseBody.contains("event: agent_end"), "Missing agent_end event");

        // Verify streaming text deltas
        assertTrue(responseBody.contains("\"text\":\"Hello \""), "Missing first text delta");
        assertTrue(responseBody.contains("\"text\":\"world!\""), "Missing second text delta");

        // Verify session header
        assertTrue(resp.headers().firstValue("X-Session-Id").isPresent());
        assertEquals("test-session-1", resp.headers().firstValue("X-Session-Id").get());
    }

    @Test
    void chatRequiresMessage() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String body = new ObjectMapper().writeValueAsString(new ChatRequest(""));

        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/chat"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(400, resp.statusCode());
        assertTrue(resp.body().contains("error"));
    }

    @Test
    void multipleChatRequests() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String sessionId = "multi-chat-" + System.nanoTime();

        // First request
        String body1 = new ObjectMapper().writeValueAsString(
                new ChatRequest("First message", sessionId));
        HttpResponse<String> resp1 = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/chat"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body1))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp1.statusCode());

        // Second request (same session — should accumulate messages)
        String body2 = new ObjectMapper().writeValueAsString(
                new ChatRequest("Second message", sessionId));
        HttpResponse<String> resp2 = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/chat"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body2))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp2.statusCode());

        // Verify both responses are valid SSE
        assertTrue(resp1.body().contains("event: agent_end"));
        assertTrue(resp2.body().contains("event: agent_end"));
    }

    @Test
    void abortSession() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        // Abort a non-existent session
        String body = new ObjectMapper().writeValueAsString(Map.of("sessionId", "nonexistent"));
        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/abort"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(404, resp.statusCode());
    }

    private static int findFreePort() throws IOException {
        try (var socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
