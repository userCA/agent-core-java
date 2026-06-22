package io.agentcore.tools;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Context passed to a tool during execution.
 *
 * <p>Mirrors Python {@code agent_core/tools/base.py} ToolContext.
 *
 * @param signal         abort signal (check to support cancellation)
 * @param onUpdate       callback for intermediate result updates (nullable)
 * @param metadata       extra metadata injected by hooks
 * @param mutationQueue  mutation queue for state-changing tools (nullable)
 */
public record ToolContext(
        AtomicBoolean signal,
        Consumer<ToolResult> onUpdate,
        Map<String, Object> metadata,
        Object mutationQueue
) {
    public ToolContext {
        if (signal == null) signal = new AtomicBoolean(false);
        if (metadata == null) metadata = Map.of();
        else metadata = Map.copyOf(metadata);
    }

    /**
     * Check if the tool should abort.
     */
    public boolean isAborted() {
        return signal != null && signal.get();
    }
}
