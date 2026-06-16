package io.agentcore.providers.registry;

import io.agentcore.providers.auth.AuthSource;
import io.agentcore.providers.base.ModelProvider;
import io.agentcore.providers.types.Model;
import io.agentcore.providers.types.ProviderAuth;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry mapping provider names to adapters and their auth sources.
 */
public class ModelRegistry {
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    public void registerProvider(ModelProvider provider, AuthSource authSource) {
        Map<String, Model> modelMap = new LinkedHashMap<>();
        for (var m : provider.listModels()) {
            modelMap.put(m.id(), m);
        }
        entries.put(provider.name(), new Entry(provider, authSource, modelMap));
    }

    public ModelProvider getProvider(String name) {
        var entry = entries.get(name);
        if (entry == null) throw new UnknownProviderException(name);
        return entry.provider;
    }

    public Optional<Model> find(String provider, String modelId) {
        var entry = entries.get(provider);
        if (entry == null) return Optional.empty();
        return Optional.ofNullable(entry.models.get(modelId));
    }

    public List<Model> listAvailable() {
        List<Model> all = new ArrayList<>();
        entries.values().forEach(e -> all.addAll(e.models.values()));
        return all;
    }

    public CompletableFuture<ProviderAuth> getAuth(Model model) {
        var entry = entries.get(model.provider());
        if (entry == null) {
            return CompletableFuture.failedFuture(new UnknownProviderException(model.provider()));
        }
        return entry.authSource.resolve(model.provider());
    }

    public boolean hasConfiguredAuth(Model model) {
        return entries.containsKey(model.provider());
    }

    private record Entry(ModelProvider provider, AuthSource authSource, Map<String, Model> models) {}

    public static class UnknownProviderException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public UnknownProviderException(String name) {
            super("Unknown provider: " + name);
        }
    }
}
