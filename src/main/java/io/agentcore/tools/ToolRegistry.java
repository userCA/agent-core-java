package io.agentcore.tools;

import java.util.*;

/**
 * Registry that holds tool instances and provides lookup.
 *
 * <p>Mirrors Python {@code agent_core/tools/base.py} ToolRegistry.
 */
public class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private final Map<String, Object> sources = new LinkedHashMap<>();

    /**
     * Register a tool.
     */
    public void register(Tool tool) {
        register(tool, null);
    }

    /**
     * Register a tool with an associated source.
     */
    public void register(Tool tool, Object source) {
        String name = tool.definition().name();
        tools.put(name, tool);
        sources.put(name, source);
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
                        sources.get(e.getKey())
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
}
