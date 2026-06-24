package io.agentcore.app.http;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import io.agentcore.session.compaction.Compactor;
import io.agentcore.agent.Agent;
import io.agentcore.model.AgentEvent;
import io.agentcore.agent.AgentLoopConfig;
import io.agentcore.model.Message;
import io.agentcore.extensions.Extension;
import io.agentcore.llm.*;
import io.agentcore.session.AgentSession;
import io.agentcore.session.JsonlSessionStore;
import io.agentcore.session.SessionStore;
import io.agentcore.tools.ToolRegistry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Lightweight HTTP server with SSE (Server-Sent Events) for agent interaction.
 *
 * <p>Uses {@link com.sun.net.httpserver.HttpServer} (JDK built-in, zero dependencies).
 * All agent loops run on virtual threads; events are streamed to clients via SSE.
 *
 * <h3>Endpoints:</h3>
 * <ul>
 *   <li>{@code POST /api/chat} — send a message, receive SSE stream</li>
 *   <li>{@code POST /api/abort} — abort a running session</li>
 *   <li>{@code GET  /api/sessions} — list active sessions</li>
 *   <li>{@code GET  /api/health} — health check</li>
 * </ul>
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 * AgentHttpServer server = AgentHttpServer.builder()
 *         .port(8080)
 *         .agentFactory(config -> Agent.create(provider, model, auth, tools, sysPrompt))
 *         .sessionDir("./sessions")
 *         .build();
 * server.start();
 * }</pre>
 */
public class AgentHttpServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AgentHttpServer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

    private final HttpServer httpServer;
    private final int port;
    private final SessionManager sessionManager;

    private AgentHttpServer(Builder b) throws IOException {
        this.port = b.port;
        this.sessionManager = new SessionManager(
                b.agentFactory, b.sessionStore, b.extensions, b.compactor, b.contextWindow);

        this.httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        httpServer.createContext("/api/chat", new ChatHandler());
        httpServer.createContext("/api/abort", new AbortHandler());
        httpServer.createContext("/api/human-input", new HumanInputHandler());
        httpServer.createContext("/api/sessions", new SessionsHandler());
        httpServer.createContext("/api/health", new HealthHandler());
        httpServer.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    }

    /** Start the HTTP server. */
    public void start() {
        httpServer.start();
        log.info("AgentHttpServer started on port {}", port);
    }

    /** Stop the HTTP server and close all sessions. */
    @Override
    public void close() {
        httpServer.stop(2);
        sessionManager.closeAll();
        log.info("AgentHttpServer stopped");
    }

    public int port() { return port; }
    public SessionManager sessionManager() { return sessionManager; }

    // ── Handlers ──────────────────────────────────────────────

    /**
     * POST /api/chat — SSE streaming chat endpoint.
     *
     * <p>Request body: {@link ChatRequest} JSON.
     * <p>Response: text/event-stream with real-time agent events.
     */
    private class ChatHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            ChatRequest req;
            try {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                req = MAPPER.readValue(body, ChatRequest.class);
            } catch (Exception e) {
                sendError(exchange, 400, "Invalid request body: " + e.getMessage());
                return;
            }

            if (req.message == null || req.message.isBlank()) {
                sendError(exchange, 400, "'message' field is required");
                return;
            }

            String sessionId = req.sessionId != null && !req.sessionId.isBlank()
                    ? req.sessionId : UUID.randomUUID().toString();

            // Set SSE headers
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.getResponseHeaders().set("Connection", "keep-alive");
            exchange.getResponseHeaders().set("X-Accel-Buffering", "no");
            exchange.getResponseHeaders().set("X-Session-Id", sessionId);
            exchange.sendResponseHeaders(200, 0);

            try (OutputStream out = exchange.getResponseBody()) {
                // Get or create session
                AgentSession session = sessionManager.getOrCreate(sessionId);

                // Subscribe to events and stream as SSE
                LinkedBlockingQueue<AgentEvent> eventQueue = new LinkedBlockingQueue<>();
                Runnable unsub = session.subscribe(eventQueue::offer);

                try {
                    // Run agent on virtual thread
                    final String sid = sessionId;
                    Thread.ofVirtual().name("chat-" + sid).start(() -> {
                        try {
                            session.prompt(req.message);
                        } catch (Exception e) {
                            log.warn("Agent prompt failed for session {}", sid, e);
                            try {
                                writeSseEvent(out, "error",
                                        Map.of("error", e.getMessage() != null ? e.getMessage() : "unknown"));
                                flush(out);
                            } catch (IOException ignored) {}
                        } finally {
                            // Sentinel to stop SSE loop
                            eventQueue.offer(new AgentEvent.AgentEnd(List.of()));
                        }
                    });

                    // SSE event loop — read from queue and write to output
                    boolean done = false;
                    while (!done) {
                        AgentEvent event = eventQueue.poll(60, TimeUnit.SECONDS);
                        if (event == null) {
                            // Send keepalive comment
                            writeSseComment(out, "keepalive");
                            flush(out);
                            continue;
                        }

                        String eventType = getSseEventType(event);
                        Map<String, Object> eventData = serializeEvent(event);
                        writeSseEvent(out, eventType, eventData);
                        flush(out);

                        if (event instanceof AgentEvent.AgentEnd) {
                            done = true;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.debug("SSE stream interrupted for session {}", sessionId);
                } finally {
                    unsub.run();
                }
            } catch (Exception e) {
                log.warn("SSE stream error for session {}", sessionId, e);
            }
        }
    }

    /**
     * POST /api/abort — abort a running session.
     */
    private class AbortHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String sessionId;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> req = MAPPER.readValue(body, Map.class);
                sessionId = (String) req.get("sessionId");
            } catch (Exception e) {
                sendError(exchange, 400, "Invalid request body");
                return;
            }

            if (sessionId == null || sessionId.isBlank()) {
                sendError(exchange, 400, "'sessionId' is required");
                return;
            }

            AgentSession session = sessionManager.get(sessionId);
            if (session != null) {
                session.abort();
                sendJson(exchange, 200, Map.of("ok", true, "sessionId", sessionId));
            } else {
                sendJson(exchange, 404, Map.of("ok", false, "error", "Session not found"));
            }
        }
    }

    /**
     * POST /api/human-input — provide input for a pending HITL request.
     *
     * <p>Request body:
     * <pre>{@code
     * {
     *   "sessionId": "...",
     *   "toolCallId": "...",
     *   "values": { "key": "value" }
     * }
     * }</pre>
     */
    private class HumanInputHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String sessionId;
            String toolCallId;
            @SuppressWarnings("unchecked")
            Map<String, Object> values;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> req = MAPPER.readValue(body, Map.class);
                sessionId = (String) req.get("sessionId");
                toolCallId = (String) req.get("toolCallId");
                Object rawValues = req.get("values");
                values = rawValues instanceof Map ? (Map<String, Object>) rawValues : Map.of();
            } catch (Exception e) {
                sendError(exchange, 400, "Invalid request body: " + e.getMessage());
                return;
            }

            if (sessionId == null || sessionId.isBlank()) {
                sendError(exchange, 400, "'sessionId' is required");
                return;
            }
            if (toolCallId == null || toolCallId.isBlank()) {
                sendError(exchange, 400, "'toolCallId' is required");
                return;
            }

            AgentSession session = sessionManager.get(sessionId);
            if (session == null) {
                sendJson(exchange, 404, Map.of("ok", false, "error", "Session not found"));
                return;
            }

            boolean accepted = session.provideHumanInput(toolCallId, values);
            if (accepted) {
                sendJson(exchange, 200, Map.of("ok", true, "toolCallId", toolCallId));
            } else {
                sendJson(exchange, 404, Map.of("ok", false,
                        "error", "No pending human input request for toolCallId: " + toolCallId));
            }
        }
    }

    /**
     * GET /api/sessions — list active sessions.
     */
    private class SessionsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            List<Map<String, Object>> sessions = new ArrayList<>();
            for (var entry : sessionManager.listSessions().entrySet()) {
                sessions.add(Map.of(
                        "sessionId", entry.getKey(),
                        "started", entry.getValue().isStarted(),
                        "messageCount", entry.getValue().messages().size()
                ));
            }
            sendJson(exchange, 200, Map.of("sessions", sessions));
        }
    }

    /**
     * GET /api/health — health check.
     */
    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            sendJson(exchange, 200, Map.of("status", "ok", "activeSessions", sessionManager.size()));
        }
    }

    // ── SSE helpers ───────────────────────────────────────────

    private static void writeSseEvent(OutputStream out, String event, Object data) throws IOException {
        String json = MAPPER.writeValueAsString(data);
        String sse = "event: " + event + "\ndata: " + json + "\n\n";
        out.write(sse.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeSseComment(OutputStream out, String comment) throws IOException {
        out.write((": " + comment + "\n\n").getBytes(StandardCharsets.UTF_8));
    }

    private static void flush(OutputStream out) throws IOException {
        out.flush();
    }

    private static String getSseEventType(AgentEvent event) {
        return switch (event) {
            case AgentEvent.AgentStart _ -> "agent_start";
            case AgentEvent.AgentEnd _ -> "agent_end";
            case AgentEvent.TurnStart _ -> "turn_start";
            case AgentEvent.TurnEnd _ -> "turn_end";
            case AgentEvent.MessageStart _ -> "message_start";
            case AgentEvent.MessageUpdate _ -> "message_update";
            case AgentEvent.MessageEnd _ -> "message_end";
            case AgentEvent.ToolExecutionStart _ -> "tool_execution_start";
            case AgentEvent.ToolExecutionUpdate _ -> "tool_execution_update";
            case AgentEvent.ToolExecutionEnd _ -> "tool_execution_end";
            case AgentEvent.HumanInputRequired _ -> "human_input_required";
        };
    }

    private static Map<String, Object> serializeEvent(AgentEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", getSseEventType(event));

        switch (event) {
            case AgentEvent.MessageUpdate mu -> {
                if (mu.delta() instanceof AgentEvent.MessageDelta.TextDelta td) {
                    data.put("delta", Map.of("type", "text", "text", td.text()));
                } else if (mu.delta() instanceof AgentEvent.MessageDelta.ThinkingDelta thd) {
                    data.put("delta", Map.of("type", "thinking", "text", thd.text()));
                } else if (mu.delta() instanceof AgentEvent.MessageDelta.ToolCallDelta tcd) {
                    Map<String, Object> delta = new LinkedHashMap<>();
                    delta.put("type", "tool_call");
                    delta.put("id", tcd.id());
                    delta.put("name", tcd.name());
                    if (tcd.argumentsDelta() != null) delta.put("arguments", tcd.argumentsDelta());
                    data.put("delta", delta);
                }
            }
            case AgentEvent.MessageEnd me -> {
                if (me.message() instanceof Message.AssistantMessage am) {
                    data.put("text", am.text());
                    data.put("stopReason", am.stopReason().toValue());
                    if (am.usage().totalTokensWithCache() > 0) {
                        data.put("usage", Map.of(
                                "inputTokens", am.usage().inputTokens(),
                                "outputTokens", am.usage().outputTokens()));
                    }
                }
            }
            case AgentEvent.ToolExecutionStart tes -> {
                data.put("toolCallId", tes.toolCallId());
                data.put("toolName", tes.toolName());
                data.put("args", tes.args());
            }
            case AgentEvent.ToolExecutionEnd tee -> {
                data.put("toolCallId", tee.toolCallId());
                data.put("toolName", tee.toolName());
                data.put("isError", tee.isError());
                if (tee.result() != null) {
                    data.put("result", tee.result().content().stream()
                            .filter(c -> c instanceof io.agentcore.model.Content.TextContent)
                            .map(c -> ((io.agentcore.model.Content.TextContent) c).text())
                            .reduce("", String::concat));
                }
            }
            case AgentEvent.TurnEnd te -> {
                data.put("toolResultCount", te.toolResults().size());
            }
            case AgentEvent.AgentEnd ae -> {
                data.put("messageCount", ae.messages().size());
            }
            case AgentEvent.HumanInputRequired hir -> {
                data.put("toolCallId", hir.toolCallId());
                data.put("prompt", hir.prompt());
            }
            default -> {}
        }
        return data;
    }

    private static void sendError(HttpExchange exchange, int code, String message) throws IOException {
        sendJson(exchange, code, Map.of("error", message));
    }

    private static void sendJson(HttpExchange exchange, int code, Object body) throws IOException {
        byte[] bytes = MAPPER.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    // ── Builder ───────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int port = 8080;
        private Supplier<Agent> agentFactory;
        private SessionStore sessionStore;
        private List<Extension> extensions = List.of();
        private Compactor compactor;
        private int contextWindow = 128000;

        public Builder port(int p) { this.port = p; return this; }
        public Builder agentFactory(Supplier<Agent> f) { this.agentFactory = f; return this; }
        public Builder sessionStore(SessionStore s) { this.sessionStore = s; return this; }
        public Builder sessionDir(String dir) {
            try { this.sessionStore = new JsonlSessionStore(dir); }
            catch (IOException e) { throw new RuntimeException(e); }
            return this;
        }
        public Builder extensions(List<Extension> e) { this.extensions = e; return this; }
        public Builder compactor(Compactor c) { this.compactor = c; return this; }
        public Builder contextWindow(int w) { this.contextWindow = w; return this; }

        public AgentHttpServer build() throws IOException {
            if (agentFactory == null) throw new IllegalStateException("agentFactory is required");
            if (sessionStore == null) {
                sessionStore = new JsonlSessionStore(System.getProperty("java.io.tmpdir") + "/agent-sessions");
            }
            return new AgentHttpServer(this);
        }
    }

    // ── Session Manager ───────────────────────────────────────

    /**
     * Manages AgentSession instances for the HTTP server.
     * Thread-safe via ConcurrentHashMap.
     */
    public static final class SessionManager {
        private final Supplier<Agent> agentFactory;
        private final SessionStore store;
        private final List<Extension> extensions;
        private final Compactor compactor;
        private final int contextWindow;
        private final ConcurrentHashMap<String, AgentSession> sessions = new ConcurrentHashMap<>();

        SessionManager(Supplier<Agent> agentFactory, SessionStore store,
                       List<Extension> extensions, Compactor compactor, int contextWindow) {
            this.agentFactory = agentFactory;
            this.store = store;
            this.extensions = extensions;
            this.compactor = compactor;
            this.contextWindow = contextWindow;
        }

        public AgentSession getOrCreate(String sessionId) {
            return sessions.computeIfAbsent(sessionId, id -> {
                Agent agent = agentFactory.get();
                AgentSession session = new AgentSession(
                        agent, store, id, compactor, extensions, contextWindow);
                session.start();
                return session;
            });
        }

        public AgentSession get(String sessionId) {
            return sessions.get(sessionId);
        }

        public Map<String, AgentSession> listSessions() {
            return Collections.unmodifiableMap(sessions);
        }

        public int size() { return sessions.size(); }

        public void closeAll() {
            sessions.values().forEach(s -> {
                try { s.close(); } catch (Exception e) {
                    log.warn("Error closing session", e);
                }
            });
            sessions.clear();
        }
    }
}
