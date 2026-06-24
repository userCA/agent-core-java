package io.agentcore.agent;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import io.agentcore.model.Message;

/**
 * Synchronisation primitive that lets the agent loop pause for human input.
 *
 * <p>Mirrors Python {@code agent_core/core/human_input.py} HumanInputGate.
 *
 * <p>Usage:
 * <pre>{@code
 * HumanInputGate gate = new HumanInputGate();
 *
 * // In the agent loop (virtual thread):
 * CompletableFuture<Map<String,Object>> future = gate.requireInput(toolCallId);
 * // ... emit HumanInputRequired event ...
 * Map<String,Object> values = future.get();  // blocks until input arrives
 *
 * // From another thread / HTTP handler:
 * gate.provideInput(toolCallId, Map.of("address", "..."));
 * // future resolves, loop resumes
 * }</pre>
 */
public final class HumanInputGate {

    private final ConcurrentHashMap<String, CompletableFuture<Map<String, Object>>> futures =
            new ConcurrentHashMap<>();

    /**
     * Create and return a Future that the loop can block on.
     */
    public CompletableFuture<Map<String, Object>> requireInput(String toolCallId) {
        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        futures.put(toolCallId, future);
        return future;
    }

    /**
     * Resolve the pending future for the given tool_call_id.
     *
     * @return true if a pending future was found and completed, false otherwise
     */
    public boolean provideInput(String toolCallId, Map<String, Object> values) {
        CompletableFuture<Map<String, Object>> future = futures.remove(toolCallId);
        if (future == null) return false;
        if (future.isDone()) return false;
        future.complete(values);
        return true;
    }

    /**
     * Returns true if we are currently awaiting input for the given tool_call_id.
     */
    public boolean isWaiting(String toolCallId) {
        CompletableFuture<Map<String, Object>> future = futures.get(toolCallId);
        return future != null && !future.isDone();
    }

    /**
     * Cancel every outstanding future (e.g. on abort).
     * Thread-safe: iterates and removes atomically per entry.
     */
    public void cancelAll() {
        futures.forEach((id, future) -> {
            futures.remove(id);
            if (!future.isDone()) {
                future.cancel(true);
            }
        });
    }

    /**
     * Exception raised by a tool when it needs additional human input.
     */
    public static class RequiresHumanInput extends RuntimeException {
        private final String prompt;
        private final Map<String, Object> inputSchema;

        public RequiresHumanInput(String prompt, Map<String, Object> inputSchema) {
            super(prompt);
            this.prompt = prompt;
            this.inputSchema = inputSchema;
        }

        public String prompt() { return prompt; }
        public Map<String, Object> inputSchema() { return inputSchema; }
    }
}
