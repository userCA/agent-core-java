package io.agentcore.llm;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ModelRegistryTest {

    static class StubProvider implements ModelProvider {
        private final String providerName;
        private final List<ModelInfo> models;

        StubProvider(String name, ModelInfo... models) {
            this.providerName = name;
            this.models = List.of(models);
        }

        @Override public String name() { return providerName; }
        @Override public List<ModelInfo> listModels() { return models; }
        @Override public Iterator<StreamEvent> stream(ModelInfo model, List<Map<String, Object>> messages,
                List<Map<String, Object>> tools, String systemPrompt, String thinkingLevel,
                Double temperature, Integer maxTokens, AtomicBoolean abortSignal, ProviderAuth auth) {
            return Collections.emptyIterator();
        }
    }

    @Test
    void registerAndRetrieveProvider() {
        ModelRegistry registry = new ModelRegistry();
        ModelInfo model = new ModelInfo("test", "model-a", 4096, 1024);
        StubProvider provider = new StubProvider("test", model);
        AuthSource auth = AuthSource.staticAuth("test-key");

        registry.registerProvider(provider, auth);

        assertSame(provider, registry.getProvider("test"));
        assertEquals(model, registry.find("test", "model-a"));
        assertNull(registry.find("test", "nonexistent"));
        assertNull(registry.find("unknown", "model-a"));
    }

    @Test
    void listAvailableModels() {
        ModelRegistry registry = new ModelRegistry();
        ModelInfo m1 = new ModelInfo("p1", "a", 4096, 1024);
        ModelInfo m2 = new ModelInfo("p2", "b", 8192, 2048);

        registry.registerProvider(new StubProvider("p1", m1), AuthSource.staticAuth("k1"));
        registry.registerProvider(new StubProvider("p2", m2), AuthSource.staticAuth("k2"));

        List<ModelInfo> all = registry.listAvailable();
        assertEquals(2, all.size());
    }

    @Test
    void unknownProviderThrows() {
        ModelRegistry registry = new ModelRegistry();
        assertThrows(IllegalArgumentException.class, () -> registry.getProvider("nonexistent"));
    }

    @Test
    void hasConfiguredAuth() {
        ModelRegistry registry = new ModelRegistry();
        ModelInfo model = new ModelInfo("p1", "a", 4096, 1024);
        registry.registerProvider(new StubProvider("p1", model), AuthSource.staticAuth("key"));

        assertTrue(registry.hasConfiguredAuth(model));
        assertFalse(registry.hasConfiguredAuth(new ModelInfo("p2", "x", 100, 100)));
    }

    @Test
    void providerNames() {
        ModelRegistry registry = new ModelRegistry();
        registry.registerProvider(new StubProvider("alpha"), AuthSource.staticAuth("k1"));
        registry.registerProvider(new StubProvider("beta"), AuthSource.staticAuth("k2"));

        assertEquals(Set.of("alpha", "beta"), registry.providerNames());
    }

    @Test
    void getAuthResolvesCredentials() {
        ModelRegistry registry = new ModelRegistry();
        ModelInfo model = new ModelInfo("p1", "a", 4096, 1024);
        registry.registerProvider(new StubProvider("p1", model), AuthSource.staticAuth("my-key"));

        ProviderAuth auth = registry.getAuth(model);
        assertEquals("my-key", auth.apiKey());
    }
}
