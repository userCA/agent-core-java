package io.agentcore.core.context;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Optional hook to transform the message list before sending to the LLM.
 */
@FunctionalInterface
public interface TransformContext {
    CompletableFuture<List<Map<String, Object>>> apply(
            List<Map<String, Object>> messages, AtomicBoolean signal);
}
