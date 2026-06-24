package io.agentcore.session;

import com.fasterxml.jackson.databind.node.TextNode;

import io.agentcore.session.compaction.CompactionResult;
import io.agentcore.session.compaction.Compactor;
import io.agentcore.agent.Agent;
import io.agentcore.model.AgentEvent;
import io.agentcore.model.AgentEvent.*;
import io.agentcore.agent.AgentLoopConfig;
import io.agentcore.model.Content;
import io.agentcore.model.Message;
import io.agentcore.model.Message.*;
import io.agentcore.extensions.Extension;
import io.agentcore.extensions.ExtensionRunner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * AgentSession — composition layer: Agent + Store + event persistence.
 *
 * <p>Mirrors Python {@code agent_core/session/session.py} AgentSession.
 */
public class AgentSession implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AgentSession.class);

    private static final int DEFAULT_CONTEXT_WINDOW = 128000;

    private final Agent agent;
    private final SessionStore store;
    private final String sessionId;
    private final Compactor compactor;
    private final List<Extension> extensions;
    private final int contextWindow;
    private final List<Consumer<AgentEvent>> listeners = new CopyOnWriteArrayList<>();

    private Runnable agentUnsub;
    private volatile boolean started = false;
    private volatile boolean closed = false;
    private ExtensionRunner extRunner;

    public AgentSession(Agent agent, SessionStore store, String sessionId) {
        this(agent, store, sessionId, null, List.of(), DEFAULT_CONTEXT_WINDOW);
    }

    public AgentSession(Agent agent, SessionStore store, String sessionId,
                        Compactor compactor, List<Extension> extensions) {
        this(agent, store, sessionId, compactor, extensions, DEFAULT_CONTEXT_WINDOW);
    }

    public AgentSession(Agent agent, SessionStore store, String sessionId,
                        Compactor compactor, List<Extension> extensions, int contextWindow) {
        this.agent = agent;
        this.store = store;
        this.sessionId = sessionId;
        this.compactor = compactor;
        this.extensions = extensions != null ? List.copyOf(extensions) : List.of();
        this.contextWindow = contextWindow > 0 ? contextWindow : DEFAULT_CONTEXT_WINDOW;
    }

    /**
     * Create session in store, restore messages, and subscribe to agent events.
     */
    public void start() {
        if (started || closed) return;

        SessionHeader header = new SessionHeader(
                sessionId,
                DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC)),
                System.getProperty("user.dir", "")
        );

        if (store.sessionExists(sessionId)) {
            try {
                SessionSnapshot snapshot = store.loadSession(sessionId);
                restoreMessages(snapshot);
            } catch (Exception e) {
                log.warn("Failed to load session {}: {}", sessionId, e.getMessage());
            }
        } else {
            store.createSession(sessionId, header);
        }

        agentUnsub = agent.subscribe(this::onAgentEvent);

        // Wire extension runner: merge session-level extensions with agent's runner
        if (!extensions.isEmpty()) {
            extRunner = new ExtensionRunner(extensions);
        } else if (agent.extensionRunner().hasExtensions()) {
            extRunner = agent.extensionRunner();
        }

        started = true;
    }

    // ── External listeners ────────────────────────────────

    public Runnable subscribe(Consumer<AgentEvent> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    // ── Proxy to agent ────────────────────────────────────

    public List<Message> messages() {
        return agent.messages();
    }

    public void prompt(String text) {
        prompt(text, null);
    }

    public void prompt(String text, Consumer<AgentEvent> onEvent) {
        checkReady();
        agent.prompt(text, onEvent, createCompactCallback());
    }

    public void continueSession() {
        continueSession(null);
    }

    public void continueSession(Consumer<AgentEvent> onEvent) {
        checkReady();
        agent.continueLoop(onEvent, createCompactCallback());
    }

    /**
     * Manually trigger compaction.
     */
    public void compact(String instructions) {
        checkReady();
        if (compactor == null) return;

        List<Message> msgs = agent.context().messagesSnapshot();
        CompactionResult result = compactor.compact(msgs, "manual", instructions, null);

        if (result.summary() != null) {
            store.appendEntry(sessionId, new SessionEntry.CompactionEntry(
                    "compaction-" + UUID.randomUUID(),
                    result.summary(),
                    result.firstKeptEntryId(),
                    result.tokensBefore()
            ));
        }
    }

    public void abort() {
        agent.abort();
    }

    /**
     * Provide human input for a pending HITL request.
     *
     * @param toolCallId  the tool_call_id from the HumanInputRequired event
     * @param values      the user-provided input values
     * @return true if the input was accepted
     */
    public boolean provideHumanInput(String toolCallId, Map<String, Object> values) {
        return agent.provideHumanInput(toolCallId, values);
    }

    public void dispose() {
        if (closed) return;
        closed = true;
        if (agentUnsub != null) {
            agentUnsub.run();
            agentUnsub = null;
        }
        agent.close();
        store.close();
    }

    @Override
    public void close() {
        dispose();
    }

    // ── State ─────────────────────────────────────────────

    public boolean isStarted() { return started; }
    public boolean isClosed() { return closed; }
    public Agent agent() { return agent; }
    public String sessionId() { return sessionId; }
    public int contextWindow() { return contextWindow; }

    // ── Internal ──────────────────────────────────────────

    private void checkReady() {
        if (closed) throw new IllegalStateException("AgentSession is disposed");
        if (!started) throw new IllegalStateException("AgentSession not started; call start() first");
    }

    private AgentLoopConfig.CompactCallback createCompactCallback() {
        if (compactor == null) return null;
        return messages -> {
            try {
                CompactionResult result = compactor.compact(messages, "overflow", null, null);
                return applyCompactionResult(messages, result);
            } catch (Exception e) {
                log.warn("Overflow compaction failed for session {}: {}", sessionId, e.getMessage());
                return false;
            }
        };
    }

    /**
     * Apply a compaction result: create summary message, replace context messages.
     * Shared between overflow callback and threshold compaction.
     */
    private boolean applyCompactionResult(List<Message> messages, CompactionResult result) {
        if (result.summary() != null && result.keptCount() > 0) {
            if (!result.summary().isEmpty()) {
                store.appendEntry(sessionId, new SessionEntry.CompactionEntry(
                        "compaction-" + UUID.randomUUID(),
                        result.summary(),
                        result.firstKeptEntryId(),
                        result.tokensBefore()
                ));
            }

            Message summaryMsg = new CustomMessage(
                    "compaction_summary", new TextNode(result.summary()),
                    null, null, System.currentTimeMillis() / 1000.0);

            List<Message> kept = new ArrayList<>(
                    messages.subList(messages.size() - result.keptCount(), messages.size()));
            List<Message> replacement = new ArrayList<>();
            replacement.add(summaryMsg);
            replacement.addAll(kept);
            agent.context().replaceMessages(replacement);
            return true;
        }
        return false;
    }

    private void restoreMessages(SessionSnapshot snapshot) {
        List<Message> restored = new ArrayList<>();
        for (SessionEntry entry : snapshot.entries()) {
            if (entry instanceof SessionEntry.MessageEntry me && me.message() != null) {
                try {
                    Message msg = MessageSerializer.deserialize(me.message());
                    if (msg != null) restored.add(msg);
                } catch (Exception e) {
                    log.warn("Failed to restore message {} in session {}: {}",
                            me.id(), sessionId, e.getMessage());
                }
            }
        }
        if (!restored.isEmpty()) {
            agent.context().addMessages(restored);
        }
    }

    private void onAgentEvent(AgentEvent evt) {
        if (evt instanceof MessageEnd me) {
            persistMessage(me.message());
        } else if (evt instanceof ToolExecutionEnd tee) {
            persistToolResult(tee);
        } else if (evt instanceof AgentEnd) {
            maybeCompact();
        }

        if (extRunner != null) {
            extRunner.onEvent(evt);
        }

        for (Consumer<AgentEvent> listener : listeners) {
            try {
                listener.accept(evt);
            } catch (Exception e) {
                log.warn("Session listener failed: {}", e.getMessage());
            }
        }
    }

    private void persistMessage(Message message) {
        Map<String, Object> msgData = MessageSerializer.serialize(message);
        store.appendEntry(sessionId, new SessionEntry.MessageEntry(
                "msg-" + UUID.randomUUID(), msgData, null));
    }

    private void persistToolResult(ToolExecutionEnd tee) {
        String resultText = "";
        if (tee.result() != null) {
            for (var c : tee.result().content()) {
                if (c instanceof Content.TextContent tc) {
                    resultText = tc.text();
                    break;
                }
            }
        }

        Map<String, Object> msgData = new LinkedHashMap<>();
        msgData.put("role", "tool_result");
        msgData.put("tool_call_id", tee.toolCallId());
        msgData.put("tool_name", tee.toolName());
        msgData.put("content", resultText);
        msgData.put("is_error", tee.isError());
        msgData.put("timestamp", System.currentTimeMillis() / 1000.0);

        store.appendEntry(sessionId, new SessionEntry.MessageEntry(
                "tool-" + UUID.randomUUID(), msgData, null));
    }

    private void maybeCompact() {
        if (compactor == null) return;
        try {
            // Thread-safe snapshot via AgentContext.messagesSnapshot()
            List<Message> msgs = agent.context().messagesSnapshot();
            if (!compactor.shouldCompact(msgs, contextWindow)) return;

            CompactionResult result = compactor.compact(msgs, "threshold", null, null);
            applyCompactionResult(msgs, result);
        } catch (Exception e) {
            log.warn("Compaction failed for session {}: {}", sessionId, e.getMessage());
        }
    }
}