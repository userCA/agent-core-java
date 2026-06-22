package io.agentcore.providers;

import java.util.*;

/**
 * ModelRegistry — maps provider name → ModelProvider + credentials.
 *
 * <p>Mirrors Python {@code agent_core/providers/registry.py} ModelRegistry.
 */
public class ModelRegistry {

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    /**
     * Register a provider with its auth source.
     */
    public void registerProvider(ModelProvider provider, AuthSource authSource) {
        Map<String, ModelInfo> models = new LinkedHashMap<>();
        for (ModelInfo m : provider.listModels()) {
            models.put(m.id(), m);
        }
        entries.put(provider.name(), new Entry(provider, authSource, models));
    }

    /**
     * Get a registered provider by name.
     *
     * @throws IllegalArgumentException if provider not found
     */
    public ModelProvider getProvider(String name) {
        Entry entry = entries.get(name);
        if (entry == null) {
            throw new IllegalArgumentException("Unknown provider: " + name);
        }
        return entry.provider;
    }

    /**
     * Find a specific model by provider and model id.
     */
    public ModelInfo find(String provider, String modelId) {
        Entry entry = entries.get(provider);
        if (entry == null) return null;
        return entry.models.get(modelId);
    }

    /**
     * List all available models across all registered providers.
     */
    public List<ModelInfo> listAvailable() {
        List<ModelInfo> result = new ArrayList<>();
        for (Entry entry : entries.values()) {
            result.addAll(entry.models.values());
        }
        return result;
    }

    /**
     * Resolve auth for a given model's provider.
     */
    public ProviderAuth getAuth(ModelInfo model) {
        Entry entry = entries.get(model.provider());
        if (entry == null) {
            throw new IllegalArgumentException("Unknown provider: " + model.provider());
        }
        return entry.authSource.resolve(model.provider());
    }

    /**
     * Check if a provider has been registered.
     */
    public boolean hasConfiguredAuth(ModelInfo model) {
        return entries.containsKey(model.provider());
    }

    /**
     * List registered provider names.
     */
    public Set<String> providerNames() {
        return Collections.unmodifiableSet(entries.keySet());
    }

    private record Entry(
        ModelProvider provider,
        AuthSource authSource,
        Map<String, ModelInfo> models
    ) {}
}
