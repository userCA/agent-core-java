package io.agentcore.tools;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import io.agentcore.tools.util.FileMutationQueue;

/**
 * Context passed to a tool during execution.
 *
 * <p>Mirrors Python {@code agent_core/tools/base.py} ToolContext.
 *
 * @param signal         abort signal (check to support cancellation)
 * @param onUpdate       callback for intermediate result updates (nullable)
 * @param metadata       extra metadata injected by hooks
 * @param mutationQueue  mutation queue for state-changing tools (nullable)
 * @param terminateSignal  set to true by the tool to request agent loop termination
 */
public record ToolContext(
        AtomicBoolean signal,
        Consumer<ToolResult> onUpdate,
        Map<String, Object> metadata,
        FileMutationQueue mutationQueue,
        AtomicBoolean terminateSignal
) {
    /** Backward-compatible constructor without terminate signal. */
    public ToolContext(AtomicBoolean signal, Consumer<ToolResult> onUpdate,
                       Map<String, Object> metadata, FileMutationQueue mutationQueue) {
        this(signal, onUpdate, metadata, mutationQueue, new AtomicBoolean(false));
    }

    public ToolContext {
        if (signal == null) signal = new AtomicBoolean(false);
        if (metadata == null) metadata = Map.of();
        else metadata = Map.copyOf(metadata);
        if (terminateSignal == null) terminateSignal = new AtomicBoolean(false);
    }

    /**
     * Check if the tool should abort.
     */
    public boolean isAborted() {
        return signal != null && signal.get();
    }

    /**
     * Request the agent loop to terminate after this tool batch completes.
     * This separates the termination signal from the tool's data result.
     */
    public void requestTerminate() {
        terminateSignal.set(true);
    }

    /**
     * Check if termination was requested by the tool.
     */
    public boolean isTerminateRequested() {
        return terminateSignal.get();
    }
}
