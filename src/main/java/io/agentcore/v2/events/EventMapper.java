package io.agentcore.v2.events;

import io.agentscope.core.event.AgentStartEvent;
import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.CustomEvent;
import io.agentscope.core.event.ModelCallStartEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.event.TextBlockStartEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.TextBlockEndEvent;
import io.agentscope.core.event.ThinkingBlockStartEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockEndEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.event.ToolResultStartEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.message.ToolResultState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Factory for AgentScope Java events corresponding to Claude Code concepts.
 *
 * <p>Turn lifecycle events ({@code TurnStart}, {@code TurnEnd}) use
 * {@link CustomEvent} with well-known type strings.
 */
public final class EventMapper {

    private EventMapper() {}

    // ── Custom event type constants ──

    public static final String CC_TURN_START = "claude_code.turn_start";
    public static final String CC_TURN_END = "claude_code.turn_end";
    public static final String META_TURN = "turn";
    public static final String META_STOP_REASON = "stop_reason";

    // ── Agent lifecycle ──

    public static AgentStartEvent agentStart(String agentId) {
        return new AgentStartEvent(newId(), newId(), agentId);
    }

    public static AgentEndEvent agentEnd() {
        return new AgentEndEvent(newId());
    }

    // ── Turn lifecycle ──

    public static CustomEvent turnStart(int turn) {
        return new CustomEvent(CC_TURN_START, Map.of(META_TURN, turn));
    }

    public static CustomEvent turnEnd(int turn, String stopReason) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put(META_TURN, turn);
        meta.put(META_STOP_REASON, stopReason);
        return new CustomEvent(CC_TURN_END, meta);
    }

    // ── Model call ──

    public static ModelCallStartEvent modelCallStart() {
        return new ModelCallStartEvent(newId());
    }

    public static ModelCallEndEvent modelCallEnd(ChatUsage usage) {
        return new ModelCallEndEvent(newId(), usage);
    }

    // ── Text block ──

    public static TextBlockStartEvent textBlockStart() {
        return new TextBlockStartEvent(newId(), "text");
    }

    public static TextBlockDeltaEvent textDelta(String text) {
        return new TextBlockDeltaEvent(newId(), "text", text);
    }

    public static TextBlockEndEvent textBlockEnd() {
        return new TextBlockEndEvent(newId(), "text");
    }

    // ── Thinking block ──

    public static ThinkingBlockStartEvent thinkingBlockStart() {
        return new ThinkingBlockStartEvent(newId(), "thinking");
    }

    public static ThinkingBlockDeltaEvent thinkingDelta(String text) {
        return new ThinkingBlockDeltaEvent(newId(), "thinking", text);
    }

    public static ThinkingBlockEndEvent thinkingBlockEnd() {
        return new ThinkingBlockEndEvent(newId(), "thinking");
    }

    // ── Tool call ──

    public static ToolCallStartEvent toolCallStart(String callId, String name) {
        return new ToolCallStartEvent(newId(), callId, name);
    }

    public static ToolCallDeltaEvent toolCallDelta(String callId, String name, String delta) {
        return new ToolCallDeltaEvent(newId(), callId, name, delta);
    }

    public static ToolCallEndEvent toolCallEnd(String callId, String name) {
        return new ToolCallEndEvent(newId(), callId, name);
    }

    // ── Tool result ──

    public static ToolResultStartEvent toolResultStart(String callId, String name) {
        return new ToolResultStartEvent(newId(), callId, name);
    }

    public static ToolResultTextDeltaEvent toolResultDelta(String callId, String text) {
        return new ToolResultTextDeltaEvent(newId(), callId, nameForDelta(callId), text);
    }

    public static ToolResultEndEvent toolResultEnd(String callId, boolean isError) {
        return new ToolResultEndEvent(newId(), callId, nameForDelta(callId),
                isError ? ToolResultState.ERROR : ToolResultState.SUCCESS);
    }

    // ── Helpers ──

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** Stub name for tool-result events where the tool name isn't tracked. */
    private static String nameForDelta(String callId) {
        return callId != null && callId.length() > 8 ? callId.substring(0, 8) : callId;
    }
}
