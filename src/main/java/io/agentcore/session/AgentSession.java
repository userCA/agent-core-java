package io.agentcore.session;

import io.agentcore.core.Agent;
import io.agentcore.core.events.*;
import io.agentcore.core.messages.AgentMessage;
import io.agentcore.core.messages.AssistantMessage;
import io.agentcore.core.messages.ToolResultMessage;
import io.agentcore.core.messages.UserMessage;
import io.agentcore.compaction.Compactor;
import io.agentcore.extensions.Extension;
import io.agentcore.session.store.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Composition layer: Agent + SessionStore + Compactor + Extensions.
 * Persists events, triggers compaction, and dispatches to extensions.
 */
public class AgentSession {
    private static final Logger log = LoggerFactory.getLogger(AgentSession.class);

    private final Agent agent;
    private final SessionStore store;
    private final String sessionId;
    private final Compactor compactor;
    private final List<Extension> extensions;
    private final List<Consumer<AgentEvent>> listeners = new CopyOnWriteArrayList<>();
    private Runnable unsubscribe;

    public AgentSession(Agent agent, SessionStore store, String sessionId,
                        Compactor compactor, List<Extension> extensions) {
        this.agent = agent;
        this.store = store;
        this.sessionId = sessionId;
        this.compactor = compactor;
        this.extensions = extensions != null ? extensions : List.of();
    }

    public CompletableFuture<Void> start() {
        return store.loadSession(sessionId)
                .thenAccept(snap -> { /* restore messages if needed */ })
                .exceptionallyCompose(e ->
                        store.createSession(sessionId,
                                new SessionHeader(sessionId, java.time.Instant.now().toString(), "")))
                .thenRun(() -> {
                    unsubscribe = agent.subscribe(this::onAgentEvent);
                });
    }

    public Runnable subscribe(Consumer<AgentEvent> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public List<AgentMessage> messages() {
        return agent.state().messages();
    }

    public CompletableFuture<Void> prompt(String text) {
        return agent.prompt(text);
    }

    public CompletableFuture<Void> continueFrom() {
        return agent.continueFrom();
    }

    public void abort() { agent.abort(); }

    public CompletableFuture<Void> dispose() {
        if (unsubscribe != null) unsubscribe.run();
        return store.close();
    }

    private void onAgentEvent(AgentEvent evt) {
        // Persist MessageEnd events
        if (evt instanceof MessageEnd me) {
            Map<String, Object> msgMap = messageToMap(me.message());
            store.appendEntry(sessionId, new SessionEntry.MessageEntry(
                    msgMap, null, UUID.randomUUID().toString()));
        }

        // Persist ToolExecutionEnd as tool_result messages
        if (evt instanceof ToolExecutionEnd tee) {
            Map<String, Object> msgMap = new LinkedHashMap<>();
            msgMap.put("role", "tool_result");
            msgMap.put("tool_call_id", tee.toolCallId());
            msgMap.put("tool_name", tee.toolName());
            msgMap.put("is_error", tee.isError());
            store.appendEntry(sessionId, new SessionEntry.MessageEntry(
                    msgMap, null, UUID.randomUUID().toString()));
        }

        // Check compaction on AgentEnd
        if (evt instanceof AgentEnd && compactor != null) {
            maybeCompact();
        }

        // Dispatch to extensions
        for (var ext : extensions) {
            try { ext.onEvent(evt); } catch (Exception e) {
                log.warn("Extension {} failed: {}", ext.name(), e.getMessage());
            }
        }

        // Fan out to external listeners
        for (var listener : listeners) {
            try { listener.accept(evt); } catch (Exception e) {
                log.warn("Session listener failed: {}", e.getMessage());
            }
        }
    }

    private void maybeCompact() {
        if (compactor == null || agent.state().model() == null) return;
        int contextWindow = agent.state().model().contextWindow();
        if (compactor.shouldCompact(agent.state().messages(), contextWindow)) {
            compactor.compact(agent.state().messages(), "threshold", null, null)
                    .thenAccept(result -> {
                        store.appendEntry(sessionId, new SessionEntry.CompactionEntry(
                                result.summary(), result.firstKeptEntryId(),
                                result.tokensBefore(), null, false,
                                UUID.randomUUID().toString()));
                        log.info("Compacted: {} -> {} tokens", result.tokensBefore(), result.tokensAfter());
                    })
                    .exceptionally(e -> {
                        log.warn("Compaction failed: {}", e.getMessage());
                        return null;
                    });
        }
    }

    private Map<String, Object> messageToMap(AgentMessage msg) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("role", msg.role());
        map.put("timestamp", msg.timestamp());
        // Include content based on message type
        if (msg instanceof UserMessage um) {
            map.put("content", um.content());
        } else if (msg instanceof AssistantMessage am) {
            map.put("content", am.content());
            if (am.usage() != null) map.put("usage", am.usage());
            if (am.stopReason() != null) map.put("stop_reason", am.stopReason());
            if (am.model() != null) map.put("model", am.model());
        } else if (msg instanceof ToolResultMessage trm) {
            map.put("tool_call_id", trm.toolCallId());
            map.put("tool_name", trm.toolName());
            map.put("content", trm.content());
            map.put("is_error", trm.isError());
        }
        return map;
    }
}
