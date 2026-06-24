package io.agentcore;

import io.agentcore.config.AgentConfig;
import io.agentcore.core.Agent;
import io.agentcore.http.AgentHttpServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for agent-core-java.
 *
 * <p>Demonstrates the three ways to configure and run an agent:
 *
 * <h3>1. From Environment Variables:</h3>
 * <pre>
 * export AGENT_PROVIDER=minimax
 * export AGENT_MODEL=MiniMax-M2.7
 * export MINIMAX_API_KEY=sk-...
 * java -jar agent-core.jar
 * </pre>
 *
 * <h3>2. From agent.properties:</h3>
 * <pre>
 * agent.provider=minimax
 * agent.model=MiniMax-M2.7
 * agent.api-key=sk-...
 * java -jar agent-core.jar
 * </pre>
 *
 * <h3>3. Combined (env overrides properties):</h3>
 * <pre>
 * # agent.properties has defaults
 * # Override just the API key via env:
 * export MINIMAX_API_KEY=sk-...
 * java -jar agent-core.jar
 * </pre>
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        // ── Load configuration ─────────────────────────────────
        // AgentConfig.load() reads agent.properties then overlays env vars
        AgentConfig config = AgentConfig.load();
        log.info("Configuration: {}", config);

        // ── Create agent ───────────────────────────────────────
        Agent agent = config.createAgent();
        log.info("Agent created with provider={}, model={}", config.getProvider(), config.getModel());

        // ── Start HTTP SSE server ──────────────────────────────
        AgentHttpServer server = AgentHttpServer.builder()
                .port(config.getHttpPort())
                .agentFactory(() -> config.createAgent())
                .contextWindow(config.getContextWindow())
                .build();

        server.start();
        log.info("HTTP SSE server started on http://localhost:{}", config.getHttpPort());
        log.info("Endpoints:");
        log.info("  POST /api/chat       — send a message, receive SSE stream");
        log.info("  POST /api/abort      — abort a running session");
        log.info("  GET  /api/sessions   — list active sessions");
        log.info("  GET  /api/health     — health check");
        log.info("  POST /api/human-input — provide human input for HITL");

        // ── Register shutdown hook ─────────────────────────────
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down...");
            try {
                server.close();
                agent.close();
            } catch (Exception e) {
                log.warn("Error during shutdown", e);
            }
        }));

        // Keep the main thread alive
        Thread.currentThread().join();
    }
}
