package io.agentcore.core.context;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Hook called before each tool execution. Can block, inject metadata, or mutate args.
 * Return {@link BeforeToolCallResult#proceed()} to proceed normally.
 */
@FunctionalInterface
public interface BeforeToolCallHook {
    CompletableFuture<BeforeToolCallResult> apply(Map<String, Object> callContext);
}
