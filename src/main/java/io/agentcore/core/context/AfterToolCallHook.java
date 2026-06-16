package io.agentcore.core.context;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Hook called after each tool execution. Can override result.
 * Return {@link AfterToolCallResult#keepOriginal()} to keep the original result.
 */
@FunctionalInterface
public interface AfterToolCallHook {
    CompletableFuture<AfterToolCallResult> apply(Map<String, Object> callContext);
}
