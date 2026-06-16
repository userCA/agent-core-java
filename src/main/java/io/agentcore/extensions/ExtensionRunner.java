package io.agentcore.extensions;

import io.agentcore.core.events.AgentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class ExtensionRunner {
    private static final Logger log = LoggerFactory.getLogger(ExtensionRunner.class);
    private final List<Extension> extensions;

    public ExtensionRunner(List<Extension> extensions) {
        this.extensions = extensions;
    }

    public CompletableFuture<Void> onEvent(AgentEvent evt) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (var ext : extensions) {
            chain = chain.thenCompose(v ->
                    ext.onEvent(evt)
                            .exceptionally(e -> {
                                log.warn("Extension {} onEvent failed: {}", ext.name(), e.getMessage());
                                return null;
                            })
            );
        }
        return chain;
    }

    public CompletableFuture<Map<String, Object>> onBeforeAgentStart(String prompt, String systemPrompt) {
        Map<String, Object> merged = new LinkedHashMap<>();
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (var ext : extensions) {
            chain = chain.thenCompose(prev ->
                    ext.onBeforeAgentStart(prompt, systemPrompt)
                            .thenAccept(result -> {
                                if (result != null) merged.putAll(result);
                            })
                            .exceptionally(e -> {
                                log.warn("Extension {} onBeforeAgentStart failed: {}", ext.name(), e.getMessage());
                                return null;
                            })
            );
        }
        return chain.thenApply(v -> merged.isEmpty() ? null : merged);
    }

    public CompletableFuture<Map<String, Object>> beforeToolCall(Map<String, Object> callContext) {
        Map<String, Object> merged = new LinkedHashMap<>();
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (var ext : extensions) {
            chain = chain.thenCompose(prev -> {
                // Check if already blocked
                if (Boolean.TRUE.equals(merged.get("block"))) {
                    return CompletableFuture.completedFuture(null);
                }
                return ext.onBeforeToolCall(callContext)
                        .thenAccept(result -> {
                            if (result != null) merged.putAll(result);
                        })
                        .exceptionally(e -> {
                            log.warn("Extension {} beforeToolCall failed: {}", ext.name(), e.getMessage());
                            return null;
                        });
            });
        }
        return chain.thenApply(v -> merged.isEmpty() ? null : merged);
    }

    public CompletableFuture<Map<String, Object>> afterToolCall(Map<String, Object> callContext) {
        Map<String, Object> merged = new LinkedHashMap<>();
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (var ext : extensions) {
            chain = chain.thenCompose(prev ->
                    ext.onAfterToolCall(callContext)
                            .thenAccept(result -> {
                                if (result != null) merged.putAll(result);
                            })
                            .exceptionally(e -> {
                                log.warn("Extension {} afterToolCall failed: {}", ext.name(), e.getMessage());
                                return null;
                            })
            );
        }
        return chain.thenApply(v -> merged.isEmpty() ? null : merged);
    }
}
