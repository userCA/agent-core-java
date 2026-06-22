package io.agentcore.memory;

import io.agentcore.core.AgentEvent;
import io.agentcore.core.AgentEvent.MessageEnd;
import io.agentcore.core.AgentEvent.TurnEnd;
import io.agentcore.core.Content;
import io.agentcore.core.Message;
import io.agentcore.core.Message.UserMessage;
import io.agentcore.extensions.Extension;
import io.agentcore.providers.LlmMessageUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Extension that persists user messages and recalls relevant memory.
 *
 * <p>Mirrors Python {@code agent_core/memory/extension.py} MemoryExtension.
 */
public class MemoryExtension implements Extension {

    private static final Logger log = LoggerFactory.getLogger(MemoryExtension.class);
    private static final int MAX_PENDING = 100;

    private final MemoryStore store;
    private final String sessionId;
    private final int topK;
    private final List<String> pending = new ArrayList<>();

    public MemoryExtension(MemoryStore store, String sessionId) {
        this(store, sessionId, 5);
    }

    public MemoryExtension(MemoryStore store, String sessionId, int topK) {
        this.store = store;
        this.sessionId = sessionId;
        this.topK = topK;
    }

    @Override
    public String name() {
        return "memory";
    }

    @Override
    public void onEvent(AgentEvent event) {
        if (event instanceof MessageEnd me) {
            Message msg = me.message();
            if (!(msg instanceof UserMessage um)) return;

            List<String> textParts = new ArrayList<>();
            for (Content c : um.content()) {
                if (c instanceof Content.TextContent tc) {
                    textParts.add(tc.text());
                }
            }
            if (textParts.isEmpty()) return;

            pending.add(String.join("\n", textParts));
            if (pending.size() > MAX_PENDING) {
                pending.remove(0);
            }
        } else if (event instanceof TurnEnd) {
            if (sessionId == null || pending.isEmpty()) return;

            for (String text : pending) {
                try {
                    store.remember(sessionId, text, null).join();
                } catch (Exception e) {
                    log.warn("memory remember failed for session {}: {}", sessionId, e.getMessage());
                }
            }
            pending.clear();
        }
    }

    @Override
    public List<Map<String, Object>> transformContext(
            List<Map<String, Object>> llmMessages, AtomicBoolean signal) {
        if (sessionId == null) return llmMessages;

        String queryText = LlmMessageUtils.latestUserText(llmMessages);
        if (queryText == null || queryText.isEmpty()) return llmMessages;

        List<MemoryRecord> records;
        try {
            records = store.recall(sessionId, queryText, topK).join();
        } catch (Exception e) {
            log.warn("memory recall failed for session {}: {}", sessionId, e.getMessage());
            return llmMessages;
        }

        if (signal != null && signal.get()) return llmMessages;
        if (records == null || records.isEmpty()) return llmMessages;

        StringBuilder body = new StringBuilder("Recalled memory for this session:");
        for (int i = 0; i < records.size(); i++) {
            body.append("\n[").append(i + 1).append("] ").append(records.get(i).text());
        }

        return LlmMessageUtils.injectSystemMessageAtLatestUser(llmMessages, body.toString());
    }
}
