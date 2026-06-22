package io.agentcore.memory.adapters;

import io.agentcore.memory.InMemoryMemoryStore;
import io.agentcore.memory.MemoryStore;

import java.util.Map;

/**
 * Factory for creating {@link MemoryStore} instances by type.
 *
 * <p>Mirrors Python {@code agent_core/memory/adapters/__init__.py} import pattern.
 * Supported types:
 * <ul>
 *   <li>{@code "inmemory"} — {@link InMemoryMemoryStore}</li>
 *   <li>{@code "mem0"} — {@link Mem0MemoryStore}</li>
 *   <li>{@code "openviking"} — {@link OpenVikingMemoryStore}</li>
 * </ul>
 */
public final class MemoryAdapterFactory {

    private MemoryAdapterFactory() {}

    /**
     * Create a MemoryStore of the given type.
     *
     * @param type   the store type ("inmemory", "mem0", "openviking")
     * @param config optional configuration map
     * @return a new MemoryStore instance
     */
    public static MemoryStore create(String type, Map<String, String> config) {
        if (config == null) config = Map.of();

        return switch (type.toLowerCase()) {
            case "inmemory", "memory", "in_memory" -> new InMemoryMemoryStore();
            case "mem0" -> new Mem0MemoryStore(
                    config.getOrDefault("url", "http://localhost:8081"),
                    config.getOrDefault("api_key", ""));
            case "openviking" -> {
                OpenVikingMemoryStore.Builder builder = OpenVikingMemoryStore.builder()
                        .url(config.getOrDefault("url", "http://localhost:1933"))
                        .apiKey(config.getOrDefault("api_key", ""));
                if (config.containsKey("agent_id")) {
                    builder.agentId(config.get("agent_id"));
                }
                yield builder.build();
            }
            default -> throw new IllegalArgumentException("Unknown memory store type: " + type);
        };
    }

    /**
     * Create a MemoryStore with default configuration.
     */
    public static MemoryStore create(String type) {
        return create(type, Map.of());
    }
}
