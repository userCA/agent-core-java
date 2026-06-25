package io.agentcore.memory;

import io.agentcore.model.AgentEvent;
import io.agentcore.model.AgentEvent.MessageEnd;
import io.agentcore.model.AgentEvent.TurnEnd;
import io.agentcore.model.Content;
import io.agentcore.model.Message;
import io.agentcore.model.Message.UserMessage;
import io.agentcore.extensions.Extension;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

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
    private final List<String> pending = Collections.synchronizedList(new ArrayList<>());

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

            String text = Content.joinAllText(um.content());
            if (text.isEmpty()) return;

            synchronized (pending) {
                pending.add(text);
                if (pending.size() > MAX_PENDING) {
                    pending.removeFirst();
                }
            }
        } else if (event instanceof TurnEnd) {
            if (sessionId == null || pending.isEmpty()) return;

            // Snapshot under lock to avoid ConcurrentModificationException
            List<String> snapshot;
            synchronized (pending) {
                if (pending.isEmpty()) return;
                snapshot = new ArrayList<>(pending);
                pending.clear();
            }

            for (String text : snapshot) {
                try {
                    store.remember(sessionId, text, null);
                } catch (Exception e) {
                    log.warn("memory remember failed for session {}: {}", sessionId, e.getMessage());
                }
            }
        }
    }
}
