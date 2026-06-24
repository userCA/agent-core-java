package io.agentcore.integration;

import io.agentcore.agent.Agent;
import io.agentcore.model.AgentEvent;
import io.agentcore.agent.AgentLoopConfig;
import io.agentcore.model.Message;
import io.agentcore.model.Message.AssistantMessage;
import io.agentcore.model.AgentEvent.*;
import io.agentcore.llm.openai.OpenAIProvider;
import io.agentcore.app.http.AgentHttpServer;
import io.agentcore.llm.*;
import io.agentcore.tools.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import io.agentcore.model.ToolResult;

/**
 * End-to-end integration test using MiniMax-M2.7 via OpenAI-compatible API.
 *
 * <p>Run with: MINIMAX_API_KEY=sk-xxx ./gradlew test --tests MiniMaxIntegrationTest
 * Tests are SKIPPED if MINIMAX_API_KEY is not set.
 */
@EnabledIfEnvironmentVariable(named = "MINIMAX_API_KEY", matches = ".+")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MiniMaxIntegrationTest {

    private static final String API_KEY = System.getenv("MINIMAX_API_KEY");
    private static final String BASE_URL = "https://api.minimaxi.com/v1";
    private static final String MODEL_ID = "MiniMax-M2.7";
    private static final int CONTEXT_WINDOW = 204_800;

    private static OpenAIProvider provider;
    private static ModelInfo model;
    private static AuthSource authSource;

    @BeforeAll
    static void setup() {
        provider = new OpenAIProvider(BASE_URL, "minimax", List.of(
                new ModelInfo("minimax", MODEL_ID, CONTEXT_WINDOW, 16_384)
        ), Duration.ofSeconds(120));
        model = new ModelInfo("minimax", MODEL_ID, CONTEXT_WINDOW, 16_384);
        authSource = AuthSource.staticAuth(API_KEY);
    }

    // ── Test 1: Raw Provider SSE Streaming ────────────────────

    @Test
    @Order(1)
    @DisplayName("1. Raw SSE streaming from MiniMax-M2.7")
    void rawSseStreaming() {
        System.out.println("\n=== Test 1: Raw SSE Streaming ===");

        List<Map<String, Object>> messages = List.of(
                Map.of("role", "user", "content", "用一句话介绍你自己")
        );

        Iterator<StreamEvent> stream = provider.stream(
                model, messages, List.of(), "你是一个有帮助的助手。",
                "off", 0.7, 500, null, authSource.resolve("minimax"));

        StringBuilder textBuf = new StringBuilder();
        int eventCount = 0;
        String stopReason = null;
        int inputTokens = 0, outputTokens = 0;

        while (stream.hasNext()) {
            StreamEvent evt = stream.next();
            eventCount++;
            if (evt instanceof StreamEvent.StreamTextDelta td) {
                textBuf.append(td.text());
                System.out.print(td.text());
            } else if (evt instanceof StreamEvent.StreamMessageEnd sme) {
                stopReason = sme.stopReason();
                inputTokens = sme.inputTokens();
                outputTokens = sme.outputTokens();
            } else if (evt instanceof StreamEvent.StreamError se) {
                fail("Stream error: " + se.message());
            }
        }

        System.out.println("\n--- Events: " + eventCount + ", Stop: " + stopReason
                + ", Tokens: " + inputTokens + "/" + outputTokens + " ---");

        assertFalse(textBuf.isEmpty(), "Should receive text content");
        assertNotNull(stopReason, "Should receive stop reason");
        assertTrue(eventCount >= 2, "Should receive multiple events (deltas + end)");
    }

    // ── Test 2: Agent Loop Full Cycle ─────────────────────────

    @Test
    @Order(2)
    @DisplayName("2. Agent loop with event flow (text response)")
    void agentLoopTextResponse() {
        System.out.println("\n=== Test 2: Agent Loop Text Response ===");

        Agent agent = Agent.create(provider, model, authSource, null,
                "你是一个简洁的助手，回答控制在两句话以内。");

        List<AgentEvent> events = new ArrayList<>();
        agent.subscribe(events::add);

        List<Message> result = agent.prompt("1+1等于几？", null);

        System.out.println("Events received: " + events.size());
        for (AgentEvent evt : events) {
            System.out.println("  " + evt.getClass().getSimpleName());
        }

        assertFalse(result.isEmpty(), "Should produce assistant messages");
        AssistantMessage assistant = (AssistantMessage) result.getFirst();
        System.out.println("Response: " + assistant.text());

        // Verify event flow: AgentStart → MessageStart → MessageUpdate* → MessageEnd → AgentEnd
        assertTrue(events.stream().anyMatch(e -> e instanceof AgentStart), "Should have AgentStart");
        assertTrue(events.stream().anyMatch(e -> e instanceof MessageStart), "Should have MessageStart");
        assertTrue(events.stream().anyMatch(e -> e instanceof MessageUpdate), "Should have MessageUpdate (streaming)");
        assertTrue(events.stream().anyMatch(e -> e instanceof MessageEnd), "Should have MessageEnd");
        assertTrue(events.stream().anyMatch(e -> e instanceof AgentEnd), "Should have AgentEnd");

        // Verify MessageStart comes BEFORE MessageUpdate (timing fix verification)
        int startIdx = -1, updateIdx = -1;
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i) instanceof MessageStart && startIdx == -1) startIdx = i;
            if (events.get(i) instanceof MessageUpdate && updateIdx == -1) updateIdx = i;
        }
        assertTrue(startIdx < updateIdx,
                "MessageStart (idx=" + startIdx + ") should come before first MessageUpdate (idx=" + updateIdx + ")");

        assertFalse(assistant.text().isEmpty(), "Response text should not be empty");
        agent.close();
    }

    // ── Test 3: Agent Loop with Tool Calls ────────────────────

    @Test
    @Order(3)
    @DisplayName("3. Agent loop with tool calling")
    void agentLoopWithToolCalls() {
        System.out.println("\n=== Test 3: Agent Loop with Tool Calls ===");

        // Register a simple calculator tool
        ToolRegistry registry = new ToolRegistry();
        registry.register(new CalculatorTool());

        Agent agent = Agent.create(provider, model, authSource, registry,
                "你是一个数学助手。使用 calculator 工具来计算。");

        List<AgentEvent> events = new ArrayList<>();
        agent.subscribe(events::add);

        List<Message> result = agent.prompt("请计算 23 乘以 17", null);

        System.out.println("Events received: " + events.size());
        for (AgentEvent evt : events) {
            String info = switch (evt) {
                case ToolExecutionStart tes -> "ToolExecutionStart: " + tes.toolName() + "(" + tes.args() + ")";
                case ToolExecutionEnd tee -> "ToolExecutionEnd: " + tee.toolName() + " → " +
                        (tee.result() != null ? tee.result().content() : "null");
                case MessageEnd me -> "MessageEnd: " + (me.message() instanceof AssistantMessage am ? am.text() : "?");
                default -> evt.getClass().getSimpleName();
            };
            System.out.println("  " + info);
        }

        // Verify tool execution events
        boolean hasToolStart = events.stream().anyMatch(e ->
                e instanceof ToolExecutionStart tes && "calculator".equals(tes.toolName()));
        boolean hasToolEnd = events.stream().anyMatch(e ->
                e instanceof ToolExecutionEnd tee && "calculator".equals(tee.toolName()));

        assertTrue(hasToolStart, "Should have ToolExecutionStart for calculator");
        assertTrue(hasToolEnd, "Should have ToolExecutionEnd for calculator");

        assertFalse(result.isEmpty(), "Should produce final assistant message");
        System.out.println("Final response: " + ((AssistantMessage) result.getFirst()).text());
        agent.close();
    }

    // ── Test 4: HTTP SSE Server End-to-End ────────────────────

    @Test
    @Order(4)
    @DisplayName("4. HTTP SSE Server end-to-end")
    void httpSseServerEndToEnd() throws Exception {
        System.out.println("\n=== Test 4: HTTP SSE Server End-to-End ===");

        // Start SSE server on random port
        AgentHttpServer server = AgentHttpServer.builder()
                .port(18080)
                .agentFactory(() -> Agent.create(provider, model, authSource, null,
                        "你是一个友好的助手，回答简洁。"))
                .build();
        server.start();
        int port = server.port();
        System.out.println("Server started on port " + port);

        try {
            // Test health endpoint
            HttpClient client = HttpClient.newBuilder().build();
            HttpResponse<String> healthResp = client.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:" + port + "/api/health"))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, healthResp.statusCode());
            System.out.println("Health: " + healthResp.body());

            // Test SSE chat endpoint
            String chatBody = """
                    {"message": "你好，请用一句话介绍Java语言", "sessionId": "test-session-1"}
                    """;
            HttpResponse<java.io.InputStream> chatResp = client.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:" + port + "/api/chat"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(chatBody))
                            .build(),
                    HttpResponse.BodyHandlers.ofInputStream());

            assertEquals(200, chatResp.statusCode());
            String sessionId = chatResp.headers().firstValue("X-Session-Id").orElse("unknown");
            System.out.println("Session: " + sessionId);

            // Read SSE events
            List<String> sseEventTypes = new ArrayList<>();
            StringBuilder fullText = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(chatResp.body(), StandardCharsets.UTF_8))) {
                String line;
                String currentEvent = null;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("event: ")) {
                        currentEvent = line.substring(7).trim();
                        sseEventTypes.add(currentEvent);
                    } else if (line.startsWith("data: ") && currentEvent != null) {
                        if ("message_update".equals(currentEvent)) {
                            // Extract text delta if present
                            String data = line.substring(6);
                            if (data.contains("\"text\"")) {
                                int idx = data.indexOf("\"text\"");
                                int start = data.indexOf("\"", idx + 6) + 1;
                                int end = data.indexOf("\"", start);
                                if (start > 0 && end > start) {
                                    String text = data.substring(start, end);
                                    fullText.append(text);
                                    System.out.print(text);
                                }
                            }
                        }
                    }
                    if (line.isEmpty() && "agent_end".equals(currentEvent)) break;
                }
            }

            System.out.println("\n--- SSE Event Types ---");
            for (String type : sseEventTypes) {
                System.out.println("  " + type);
            }

            assertTrue(sseEventTypes.contains("agent_start"), "SSE should contain agent_start");
            assertTrue(sseEventTypes.contains("message_start"), "SSE should contain message_start");
            assertTrue(sseEventTypes.contains("message_update"), "SSE should contain message_update");
            assertTrue(sseEventTypes.contains("message_end"), "SSE should contain message_end");
            assertTrue(sseEventTypes.contains("agent_end"), "SSE should contain agent_end");
            assertFalse(fullText.isEmpty(), "Should receive streamed text");

            System.out.println("Full response text: " + fullText);

        } finally {
            server.close();
        }
    }

    // ── Test 5: Multi-turn Conversation ───────────────────────

    @Test
    @Order(5)
    @DisplayName("5. Multi-turn conversation with context retention")
    void multiTurnConversation() {
        System.out.println("\n=== Test 5: Multi-turn Conversation ===");

        Agent agent = Agent.create(provider, model, authSource, null,
                "你是一个记忆助手。记住用户之前说的内容。");

        // Turn 1
        List<Message> r1 = agent.prompt("我最喜欢的颜色是蓝色", null);
        System.out.println("Turn 1: " + ((AssistantMessage) r1.getFirst()).text());

        // Turn 2
        List<Message> r2 = agent.prompt("我最喜欢的颜色是什么？", null);
        String response2 = ((AssistantMessage) r2.getFirst()).text();
        System.out.println("Turn 2: " + response2);

        assertTrue(response2.contains("蓝"), "Should remember the color from turn 1");

        System.out.println("Messages in context: " + agent.messages().size());
        agent.close();
    }

    // ── Calculator Tool ───────────────────────────────────────

    static class CalculatorTool implements Tool {
        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(
                    "calculator",
                    "Perform basic arithmetic calculations",
                    Map.of("type", "object", "properties", Map.of(
                            "expression", Map.of("type", "string",
                                    "description", "Math expression to evaluate, e.g. '23 * 17'")
                    ), "required", List.of("expression")),
                    null, null, null
            );
        }

        @Override
        public ToolResult execute(String toolCallId, Map<String, Object> params, ToolContext ctx) {
            String expr = (String) params.get("expression");
            if (expr == null) return new ToolResult("ERROR: expression required");
            try {
                // Simple eval: support basic +, -, *, /
                double result = evalSimple(expr);
                return new ToolResult(String.valueOf((long) result));
            } catch (Exception e) {
                return new ToolResult("ERROR: " + e.getMessage());
            }
        }

        private double evalSimple(String expr) {
            expr = expr.replaceAll("\\s+", "");
            // Handle multiplication
            if (expr.contains("*")) {
                String[] parts = expr.split("\\*", 2);
                return evalSimple(parts[0]) * evalSimple(parts[1]);
            }
            if (expr.contains("/")) {
                String[] parts = expr.split("/", 2);
                return evalSimple(parts[0]) / evalSimple(parts[1]);
            }
            if (expr.contains("+")) {
                String[] parts = expr.split("\\+", 2);
                return evalSimple(parts[0]) + evalSimple(parts[1]);
            }
            if (expr.lastIndexOf("-") > 0) {
                int idx = expr.lastIndexOf("-");
                return evalSimple(expr.substring(0, idx)) - evalSimple(expr.substring(idx + 1));
            }
            return Double.parseDouble(expr);
        }
    }
}
