package io.agentcore.v2.config;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.InMemoryAgentStateStore;

import java.nio.file.Path;

/**
 * Bootstrap configuration for the AgentScope Java infrastructure layer.
 *
 * <p>Backend selection:
 * <ul>
 *   <li><b>Dev / testing:</b> {@link #inMemory()} — no persistence</li>
 *   <li><b>Single-node:</b> {@link #jsonFile(Path)} — JSONL file per session</li>
 *   <li><b>Distributed:</b> Redis / MySQL — configured via connection URL
 *       when the corresponding extension module is on the classpath</li>
 * </ul>
 *
 * <p>Multi-tenancy:
 * Every {@link RuntimeContext} carries {@code userId} and {@code sessionId}.
 * When the state store supports it, different tenants are fully isolated.
 */
public final class AgentScopeConfig {

    private AgentScopeConfig() {}

    // ── State store factories ──

    /** In-memory store — no persistence, suitable for dev and testing. */
    public static AgentStateStore inMemory() {
        return new InMemoryAgentStateStore();
    }

    /**
     * JSON-file store — one JSONL file per session directory.
     * Suitable for single-node deployments.
     */
    public static AgentStateStore jsonFile(Path directory) {
        return new io.agentscope.core.state.JsonFileAgentStateStore(directory);
    }

    /**
     * Default store used when none is explicitly configured.
     * Currently returns an in-memory store; swap for production.
     */
    public static AgentStateStore defaultStateStore() {
        return inMemory();
    }

    // ── RuntimeContext — multi-tenant entry point ──

    /**
     * Creates a minimal per-call context builder.
     * Callers MUST set {@code userId} and {@code sessionId} before
     * passing the context into {@code ClaudeCodeAgent}.
     *
     * <pre>{@code
     * RuntimeContext ctx = AgentScopeConfig.runtimeContextBuilder()
     *     .userId("tenant-123")
     *     .sessionId("session-abc")
     *     .build();
     * }</pre>
     */
    public static RuntimeContext.Builder runtimeContextBuilder() {
        return RuntimeContext.builder();
    }

    /**
     * Convenience: creates a RuntimeContext with the given tenant and session.
     */
    public static RuntimeContext runtimeContext(String userId, String sessionId) {
        return RuntimeContext.builder()
                .userId(userId)
                .sessionId(sessionId)
                .build();
    }
}
