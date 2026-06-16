package io.agentcore.tools.base;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of tools by name.
 * Uses a single ConcurrentHashMap for atomic tool+source registration.
 */
public class ToolRegistry {
    private record ToolEntry(Tool tool, Object source) {}

    private final ConcurrentHashMap<String, ToolEntry> entries = new ConcurrentHashMap<>();

    public void register(Tool tool) {
        register(tool, null);
    }

    public void register(Tool tool, Object source) {
        String name = tool.definition().name();
        entries.put(name, new ToolEntry(tool, source));
    }

    public Tool get(String name) {
        ToolEntry entry = entries.get(name);
        return entry != null ? entry.tool() : null;
    }

    public List<ToolInfo> list() {
        List<ToolInfo> result = new ArrayList<>();
        entries.forEach((name, entry) -> result.add(new ToolInfo(
                name,
                entry.tool().definition().description(),
                entry.tool().definition().parameters(),
                entry.source()
        )));
        return result;
    }

    public List<ToolDefinition> toDefinitions() {
        return entries.values().stream().map(e -> e.tool().definition()).toList();
    }

    public boolean contains(String name) {
        return entries.containsKey(name);
    }
}
