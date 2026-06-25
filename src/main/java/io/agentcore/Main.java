package io.agentcore;

import io.agentcore.app.cli.AgentCli;
import io.agentcore.config.AgentConfig;
import io.agentcore.agent.Agent;
import io.agentcore.app.http.AgentHttpServer;

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
        AgentConfig config = AgentConfig.load();
        log.info("Configuration: {}", config);

        // ── Parse CLI flags ────────────────────────────────────
        boolean cliMode = false;
        boolean noTools = false;
        String singlePrompt = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--cli" -> cliMode = true;
                case "--no-tools" -> noTools = true;
                case "-p", "--prompt" -> {
                    cliMode = true;
                    singlePrompt = (i + 1 < args.length) ? args[++i] : "-";
                }
            }
        }

        if (cliMode) {
            int exitCode = runCli(config, !noTools, singlePrompt);
            if (exitCode != 0) System.exit(exitCode);
        } else {
            runServer(config);
        }
    }

    // ── CLI Mode ─────────────────────────────────────────────

    private static int runCli(AgentConfig config, boolean withTools, String singlePrompt) {
        try (AgentCli cli = new AgentCli(config, withTools, singlePrompt == null)) {
            if (singlePrompt != null) {
                return cli.executeSingle(singlePrompt);
            } else {
                cli.run();
                return 0;
            }
        }
    }

    // ── Server Mode (default) ────────────────────────────────

    private static void runServer(AgentConfig config) throws Exception {
        Agent agent = config.createAgent();
        log.info("Agent created with provider={}, model={}", config.provider(), config.model());

        AgentHttpServer server = AgentHttpServer.builder()
                .port(config.httpPort())
                .agentFactory(() -> config.createAgent())
                .contextWindow(config.contextWindow())
                .build();

        server.start();
        log.info("HTTP SSE server started on http://localhost:{}", config.httpPort());
        log.info("Endpoints:");
        log.info("  POST /api/chat       — send a message, receive SSE stream");
        log.info("  POST /api/abort      — abort a running session");
        log.info("  GET  /api/sessions   — list active sessions");
        log.info("  GET  /api/health     — health check");
        log.info("  POST /api/human-input — provide human input for HITL");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down...");
            try {
                server.close();
                agent.close();
            } catch (Exception e) {
                log.warn("Error during shutdown", e);
            }
        }));

        Thread.currentThread().join();
    }
}
