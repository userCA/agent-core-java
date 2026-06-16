package io.agentcore.extensions;

import io.agentcore.core.events.AgentEvent;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface Extension {
    String name();
    default CompletableFuture<Void> onEvent(AgentEvent evt) { return CompletableFuture.completedFuture(null); }
    default CompletableFuture<Map<String, Object>> onBeforeAgentStart(String prompt, String systemPrompt) {
        return CompletableFuture.completedFuture(null);
    }
    default CompletableFuture<Map<String, Object>> onBeforeToolCall(Map<String, Object> callContext) {
        return CompletableFuture.completedFuture(null);
    }
    default CompletableFuture<Map<String, Object>> onAfterToolCall(Map<String, Object> callContext) {
        return CompletableFuture.completedFuture(null);
    }
}
