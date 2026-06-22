package io.agentcore.extensions;

import io.agentcore.core.AgentEvent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base adapter for {@link Extension} with no-op defaults.
 *
 * <p>Concrete extensions can extend this class and only override the hooks they care about.
 */
public abstract class ExtensionAdapter implements Extension {

    private final String name;

    protected ExtensionAdapter(String name) {
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Map<String, Object> beforeToolCall(Map<String, Object> callContext) {
        return null;
    }

    @Override
    public Map<String, Object> afterToolCall(Map<String, Object> callContext) {
        return null;
    }

    @Override
    public List<Map<String, Object>> transformContext(
            List<Map<String, Object>> messages, AtomicBoolean signal) {
        return messages;
    }

    @Override
    public void onEvent(AgentEvent event) {}
}
