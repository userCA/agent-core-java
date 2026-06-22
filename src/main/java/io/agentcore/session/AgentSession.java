package io.agentcore.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;

import io.agentcore.compaction.CompactionResult;
import io.agentcore.compaction.Compactor;
import io.agentcore.core.Agent;
import io.agentcore.core.AgentEvent;
import io.agentcore.core.AgentEvent.*;
import io.agentcore.core.AgentLoopConfig;
import io.agentcore.core.Content;
import io.agentcore.core.Message;
import io.agentcore.core.Message.*;
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
public class AgentSession {

    private static final Logger log = LoggerFactory.getLogger(AgentSession.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final int DEFAULT_CONTEXT_WINDOW = 128000;

    private final Agent agent;
    private final SessionStore store;
    private final String sessionId;
    private final Compactor compactor;
    private final List<Extension> extensions;
    private final int contextWindow;
    private final List<Consumer<AgentEvent>> listeners = new CopyOnWriteArrayList<>();

    private Runnable agentUnsub;
    private boolean started = false;
    private boolean closed = false;
    private ExtensionRunner extRunner;

    public AgentSession(Agent agent, SessionStore store, String sessionId) {
        this(agent, store, sessionId, null, null, DEFAULT_CONTEXT_WINDOW);
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

        try {
            SessionSnapshot snapshot = store.loadSession(sessionId);
            restoreMessages(snapshot);
        } catch (IllegalArgumentException e) {
            store.createSession(sessionId, header);
        } catch (Exception e) {
            log.warn("Failed to load session {}: {}", sessionId, e.getMessage());
        }

        agentUnsub = agent.subscribe(this::onAgentEvent);

        if (!extensions.isEmpty()) {
            extRunner = new ExtensionRunner(extensions);
            agent.addBeforeToolCallHook(extRunner::beforeToolCall);
            agent.addAfterToolCallHook(callCtx -> extRunner.afterToolCall(callCtx, null, false));
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
        agent.continue_(onEvent, createCompactCallback());
    }

    /**
     * Manually trigger compaction.
     */
    public void compact(String instructions) {
        checkReady();
        if (compactor == null) return;

        List<Message> msgs = new ArrayList<>(agent.context().messages());
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

    public void dispose() {
        if (closed) return;
        closed = true;
        if (agentUnsub != null) {
            agentUnsub.run();
            agentUnsub = null;
        }
        store.close();
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
                if (result.summary() != null && result.keptCount() > 0) {
                    store.appendEntry(sessionId, new SessionEntry.CompactionEntry(
                            "compaction-" + UUID.randomUUID(),
                            result.summary(),
                            result.firstKeptEntryId(),
                            result.tokensBefore()
                    ));

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
            } catch (Exception e) {
                log.warn("Overflow compaction failed for session {}: {}", sessionId, e.getMessage());
                return false;
            }
        };
    }

    private void restoreMessages(SessionSnapshot snapshot) {
        List<Message> restored = new ArrayList<>();
        for (SessionEntry entry : snapshot.entries()) {
            if (entry instanceof SessionEntry.MessageEntry me && me.message() instanceof Map<?, ?> msgMap) {
                try {
                    Message msg = deserializeMessage(msgMap);
                    if (msg != null) restored.add(msg);
                } catch (Exception e) {
                    log.warn("Failed to restore message {} in session {}: {}",
                            me.id(), sessionId, e.getMessage());
                }
            }
        }
        if (!restored.isEmpty()) {
            agent.context().messages().addAll(restored);
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
        Map<String, Object> msgData = serializeMessage(message);
        store.appendEntry(sessionId, new SessionEntry.MessageEntry(
                "msg-" + UUID.randomUUID(), msgData, null));
    }

    private void persistToolResult(ToolExecutionEnd tee) {
        String resultText = "";
        if (tee.result() instanceof io.agentcore.tools.ToolResult tr) {
            for (var c : tr.content()) {
                if (c instanceof Content.TextContent tc) {
                    resultText = tc.text();
                    break;
                }
            }
        } else if (tee.result() != null) {
            resultText = tee.result().toString();
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
            List<Message> msgs = new ArrayList<>(agent.context().messages());
            if (!compactor.shouldCompact(msgs, contextWindow)) return;

            CompactionResult result = compactor.compact(msgs, "threshold", null, null);
            store.appendEntry(sessionId, new SessionEntry.CompactionEntry(
                    "compaction-" + UUID.randomUUID(),
                    result.summary(), result.firstKeptEntryId(), result.tokensBefore()));

            if (result.summary() != null && result.keptCount() > 0) {
                Message summaryMsg = new CustomMessage(
                        "compaction_summary", new TextNode(result.summary()),
                        null, null, System.currentTimeMillis() / 1000.0);
                List<Message> current = agent.context().messages();
                List<Message> kept = new ArrayList<>(
                        current.subList(current.size() - result.keptCount(), current.size()));
                List<Message> replacement = new ArrayList<>();
                replacement.add(summaryMsg);
                replacement.addAll(kept);
                agent.context().replaceMessages(replacement);
            }
        } catch (Exception e) {
            log.warn("Compaction failed for session {}: {}", sessionId, e.getMessage());
        }
    }

    // ── Message serialization helpers ─────────────────────

    private Map<String, Object> serializeMessage(Message msg) {
        Map<String, Object> map = new LinkedHashMap<>();
        switch (msg) {
            case UserMessage um -> {
                map.put("role", "user");
                map.put("content", serializeContent(um.content()));
                map.put("timestamp", um.timestamp());
            }
            case AssistantMessage am -> {
                map.put("role", "assistant");
                map.put("content", serializeContent(am.content()));
                map.put("timestamp", am.timestamp());
            }
            case ToolResultMessage trm -> {
                map.put("role", "tool_result");
                map.put("tool_call_id", trm.toolCallId());
                map.put("content", serializeContent(trm.content()));
                map.put("is_error", trm.isError());
                map.put("timestamp", trm.timestamp());
            }
            case CustomMessage cm -> {
                map.put("role", "custom");
                map.put("custom_type", cm.customType());
                map.put("content", cm.content() != null ? cm.content().toString() : null);
                map.put("timestamp", cm.timestamp());
            }
        }
        return map;
    }

    private List<Map<String, Object>> serializeContent(List<Content> contents) {
        if (contents == null) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (var c : contents) {
            Map<String, Object> item = new LinkedHashMap<>();
            if (c instanceof Content.TextContent tc) {
                item.put("type", "text");
                item.put("text", tc.text());
            } else if (c instanceof Content.ImageContent ic) {
                item.put("type", "image");
                item.put("data", ic.data());
                item.put("mimeType", ic.mimeType());
            } else if (c instanceof Content.ToolCallContent tcc) {
                item.put("type", "tool_call");
                item.put("id", tcc.id());
                item.put("name", tcc.name());
                item.put("arguments", tcc.arguments());
            }
            result.add(item);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Message deserializeMessage(Map<?, ?> msgMap) {
        String role = (String) msgMap.get("role");
        double timestamp = msgMap.get("timestamp") instanceof Number n ? n.doubleValue() : 0;

        return switch (role) {
            case "user" -> {
                List<Content> content = deserializeContent(msgMap.get("content"));
                yield new UserMessage(content, timestamp);
            }
            case "assistant" -> {
                List<Content> content = deserializeContent(msgMap.get("content"));
                yield new AssistantMessage(content, null, null, null, false, false, "", "", 0);
            }
            case "tool_result" -> {
                List<Content> content = deserializeContent(msgMap.get("content"));
                String toolCallId = (String) msgMap.get("tool_call_id");
                String toolName = msgMap.get("tool_name") instanceof String tn ? tn : "";
                boolean isError = Boolean.TRUE.equals(msgMap.get("is_error"));
                yield new ToolResultMessage(toolCallId, toolName, content, isError, timestamp);
            }
            case "custom" -> {
                String customType = (String) msgMap.get("custom_type");
                com.fasterxml.jackson.databind.JsonNode content = null;
                Object rawContent = msgMap.get("content");
                if (rawContent instanceof String s) {
                    content = new TextNode(s);
                } else if (rawContent != null) {
                    content = OBJECT_MAPPER.valueToTree(rawContent);
                }
                yield new CustomMessage(customType, content, null, null, timestamp);
            }
            default -> null;
        };
    }

    private List<Content> deserializeContent(Object contentObj) {
        if (contentObj instanceof List<?> list) {
            List<Content> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    String type = (String) m.get("type");
                    if ("text".equals(type)) {
                        result.add(new Content.TextContent((String) m.get("text")));
                    }
                }
            }
            return result;
        }
        return List.of();
    }
}
