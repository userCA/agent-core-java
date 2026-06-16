package io.agentcore.providers.base;

import io.agentcore.providers.types.ProviderAuth;
import io.agentcore.providers.types.StreamEvent;
import io.agentcore.tools.base.ToolDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;

/**
 * Model provider interface — all providers implement this to serve LLM requests.
 * Extends AutoCloseable so providers holding resources (e.g., HttpClient) can be cleaned up.
 */
public interface ModelProvider extends AutoCloseable {
    String name();

    List<io.agentcore.providers.types.Model> listModels();

    Flow.Publisher<StreamEvent> stream(StreamRequest request);

    /**
     * Convert tool definitions to provider-specific format.
     * Default implementation uses OpenAI function-calling schema.
     * Override for providers with different formats (e.g., Anthropic).
     */
    default List<Map<String, Object>> toolsToProviderFormat(List<ToolDefinition> tools) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (var tool : tools) {
            Map<String, Object> fn = new LinkedHashMap<>();
            fn.put("name", tool.name());
            fn.put("description", tool.description());
            fn.put("parameters", tool.parameters());
            result.add(Map.of("type", "function", "function", fn));
        }
        return result;
    }

    /**
     * Release any resources held by this provider (e.g., HttpClient threads).
     * Default implementation is a no-op for stateless providers.
     */
    @Override
    default void close() {}
}
