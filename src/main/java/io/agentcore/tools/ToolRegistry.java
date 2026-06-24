package io.agentcore.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry that holds tool instances and provides lookup.
 *
 * <p>Uses {@link ConcurrentHashMap} for thread-safe reads with
 * registration/unregistration guarded by synchronization.
 * Supports {@link ToolLifecycle} for tools that need startup/shutdown hooks.
 *
 * <p>Mirrors Python {@code agent_core/tools/base.py} ToolRegistry.
 */
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, Tool> tools = new ConcurrentHashMap<>();
    private final Map<String, ToolSource> sources = new ConcurrentHashMap<>();

    /**
     * Register a tool with default builtin source.
     */
    public void register(Tool tool) {
        register(tool, ToolSource.Builtin.INSTANCE);
    }

    /**
     * Register a tool with an associated source.
     */
    public void register(Tool tool, ToolSource source) {
        String name = tool.definition().name();

        // Lifecycle: call start() before registration
        if (tool instanceof ToolLifecycle lifecycle) {
            try {
                lifecycle.start();
            } catch (Exception e) {
                log.warn("Tool '{}' lifecycle start() failed, skipping registration", name, e);
                return;
            }
        }

        tools.put(name, tool);
        sources.put(name, source != null ? source : ToolSource.Builtin.INSTANCE);
    }

    /**
     * Unregister a tool by name, calling its lifecycle stop() if applicable.
     *
     * @return true if the tool was found and removed
     */
    public boolean unregister(String name) {
        Tool removed = tools.remove(name);
        sources.remove(name);

        if (removed instanceof ToolLifecycle lifecycle) {
            try {
                lifecycle.stop();
            } catch (Exception e) {
                log.warn("Tool '{}' lifecycle stop() failed", name, e);
            }
        }

        return removed != null;
    }

    /**
     * Get a tool by name, or null if not found.
     */
    public Tool get(String name) {
        return tools.get(name);
    }

    /**
     * List all registered tool infos.
     */
    public List<ToolInfo> list() {
        return tools.entrySet().stream()
                .map(e -> new ToolInfo(
                        e.getValue().definition().name(),
                        e.getValue().definition().description(),
                        e.getValue().definition().parameters(),
                        sources.getOrDefault(e.getKey(), ToolSource.Builtin.INSTANCE)
                ))
                .toList();
    }

    /**
     * Get all tool definitions.
     */
    public List<ToolDefinition> toDefinitions() {
        return tools.values().stream()
                .map(Tool::definition)
                .toList();
    }

    /**
     * Get all definitions in OpenAI provider format.
     */
    public List<Map<String, Object>> toProviderFormat() {
        return tools.values().stream()
                .map(t -> t.definition().toProviderFormat())
                .toList();
    }

    /**
     * Check if a tool is registered.
     */
    public boolean contains(String name) {
        return tools.containsKey(name);
    }

    /**
     * Get all registered tool names.
     */
    public Set<String> names() {
        return Collections.unmodifiableSet(tools.keySet());
    }

    /**
     * Number of registered tools.
     */
    public int size() {
        return tools.size();
    }

    /**
     * Shut down all tools that implement {@link ToolLifecycle}.
     */
    public void shutdown() {
        for (var entry : tools.entrySet()) {
            if (entry.getValue() instanceof ToolLifecycle lifecycle) {
                try {
                    lifecycle.stop();
                } catch (Exception e) {
                    log.warn("Tool '{}' lifecycle stop() failed during shutdown", entry.getKey(), e);
                }
            }
        }
    }
}
