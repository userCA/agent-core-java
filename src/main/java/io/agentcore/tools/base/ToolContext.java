package io.agentcore.tools.base;

import io.agentcore.tools.mutation.FileMutationQueue;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Runtime context passed to tool execution.
 *
 * @param signal         abort signal
 * @param onUpdate       callback for intermediate results (long-running tools)
 * @param metadata       injected by before-hooks
 * @param mutationQueue  per-file lock for concurrent write safety
 */
public record ToolContext(
        AtomicBoolean signal,
        Consumer<ToolResult> onUpdate,
        Map<String, Object> metadata,
        FileMutationQueue mutationQueue
) {
    public ToolContext {
        if (metadata == null) metadata = Map.of();
    }

    public ToolContext(AtomicBoolean signal) {
        this(signal, null, Map.of(), null);
    }

    public ToolContext(AtomicBoolean signal, Consumer<ToolResult> onUpdate) {
        this(signal, onUpdate, Map.of(), null);
    }
}
