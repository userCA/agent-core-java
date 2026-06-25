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

    /**
     * Register a tool.
     */
    public void register(Tool tool) {
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
    }

    /**
     * Unregister a tool by name, calling its lifecycle stop() if applicable.
     *
     * @return true if the tool was found and removed
     */
    public boolean unregister(String name) {
        Tool removed = tools.remove(name);
        if (removed == null) return false;

        if (removed instanceof ToolLifecycle lifecycle) {
            try {
                lifecycle.stop();
            } catch (Exception e) {
                log.warn("Tool '{}' lifecycle stop() failed", name, e);
            }
        }

        // Release resources for AutoCloseable tools (e.g. HttpClient in HttpTool)
        if (removed instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                log.warn("Tool '{}' close() failed", name, e);
            }
        }

        return true;
    }

    /**
     * Get a tool by name, or null if not found.
     */
    public Tool get(String name) {
        return tools.get(name);
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
            Tool tool = entry.getValue();
            String toolName = entry.getKey();
            if (tool instanceof ToolLifecycle lifecycle) {
                try {
                    lifecycle.stop();
                } catch (Exception e) {
                    log.warn("Tool '{}' lifecycle stop() failed during shutdown", toolName, e);
                }
            }
            if (tool instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception e) {
                    log.warn("Tool '{}' close() failed during shutdown", toolName, e);
                }
            }
        }
    }
}
