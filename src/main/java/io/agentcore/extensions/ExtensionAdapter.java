package io.agentcore.extensions;

import io.agentcore.model.AgentEvent;
import io.agentcore.extensions.HookTypes.*;

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
    public ToolCallHookResult beforeToolCall(ToolCallContext context) {
        return null;
    }

    @Override
    public AfterToolCallHookResult afterToolCall(AfterToolCallContext context) {
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
