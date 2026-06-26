package io.agentcore.tools;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import io.agentcore.model.ToolResult;

/**
 * Context passed to a tool during execution.
 *
 * <p>Changed from record to class because this context carries mutable
 * state ({@link AtomicBoolean} signals) which conflicts with record's
 * value-based immutability semantics.
 *
 * <p>Mirrors Python {@code agent_core/tools/base.py} ToolContext.
 */
public final class ToolContext {

    private final AtomicBoolean abortSignal;
    private final Consumer<ToolResult> onUpdate;
    private final Map<String, Object> metadata;
    private final AtomicBoolean terminateSignal;
    private final Map<String, Object> userInput;

    public ToolContext(AtomicBoolean abortSignal, Consumer<ToolResult> onUpdate,
                       Map<String, Object> metadata, AtomicBoolean terminateSignal) {
        this(abortSignal, onUpdate, metadata, terminateSignal, null);
    }

    public ToolContext(AtomicBoolean abortSignal, Consumer<ToolResult> onUpdate,
                       Map<String, Object> metadata, AtomicBoolean terminateSignal,
                       Map<String, Object> userInput) {
        this.abortSignal = abortSignal != null ? abortSignal : new AtomicBoolean(false);
        this.onUpdate = onUpdate;
        this.metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        this.terminateSignal = terminateSignal != null ? terminateSignal : new AtomicBoolean(false);
        this.userInput = userInput != null ? Map.copyOf(userInput) : null;
    }

    /** Abort signal (check to support cancellation). */
    public AtomicBoolean abortSignal() { return abortSignal; }

    /** Callback for intermediate result updates (nullable). */
    public Consumer<ToolResult> onUpdate() { return onUpdate; }

    /** Extra metadata injected by hooks. */
    public Map<String, Object> metadata() { return metadata; }

    /** Set to true by the tool to request agent loop termination. */
    public AtomicBoolean terminateSignal() { return terminateSignal; }

    /**
     * User input provided by HITL (Human-in-the-Loop) mechanism.
     * Null on first execution; populated when the tool is re-executed
     * after the user responds to a {@link ToolResult#requiresHumanInput} request.
     */
    public Map<String, Object> userInput() { return userInput; }

    /**
     * Check if the tool should abort.
     */
    public boolean isAborted() {
        return abortSignal.get();
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
