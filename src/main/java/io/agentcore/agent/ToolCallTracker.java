package io.agentcore.agent;

import io.agentcore.model.AgentEvent;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Tracks pending tool calls via agent events.
 * Separates tool-call state tracking from event dispatch logic (SRP).
 */
final class ToolCallTracker {
    private final Set<String> pending = Collections.synchronizedSet(new LinkedHashSet<>());

    void onEvent(AgentEvent evt) {
        if (evt instanceof AgentEvent.ToolExecutionStart tes) {
            pending.add(tes.toolCallId());
        } else if (evt instanceof AgentEvent.ToolExecutionEnd tee) {
            pending.remove(tee.toolCallId());
        }
    }

    Set<String> snapshot() { return Set.copyOf(pending); }
    void clear() { pending.clear(); }
}
