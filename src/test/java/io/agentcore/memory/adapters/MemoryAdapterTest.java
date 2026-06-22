package io.agentcore.memory.adapters;

import io.agentcore.memory.InMemoryMemoryStore;
import io.agentcore.memory.MemoryStore;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Nested;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for memory adapter classes.
 */
class MemoryAdapterTest {

    @Nested
    class FactoryTests {

        @Test
        void create_inmemory() {
            MemoryStore store = MemoryAdapterFactory.create("inmemory");
            assertTrue(store instanceof InMemoryMemoryStore);
        }

        @Test
        void create_memory_alias() {
            MemoryStore store = MemoryAdapterFactory.create("memory");
            assertTrue(store instanceof InMemoryMemoryStore);
        }

        @Test
        void create_mem0() {
            MemoryStore store = MemoryAdapterFactory.create("mem0");
            assertTrue(store instanceof Mem0MemoryStore);
        }

        @Test
        void create_openviking() {
            MemoryStore store = MemoryAdapterFactory.create("openviking");
            assertTrue(store instanceof OpenVikingMemoryStore);
        }

        @Test
        void create_mem0_withConfig() {
            MemoryStore store = MemoryAdapterFactory.create("mem0",
                    Map.of("url", "http://custom:9090", "api_key", "test-key"));
            assertTrue(store instanceof Mem0MemoryStore);
        }

        @Test
        void create_openviking_withConfig() {
            MemoryStore store = MemoryAdapterFactory.create("openviking",
                    Map.of("url", "http://custom:2000", "api_key", "sk-xxx", "agent_id", "agent-1"));
            assertTrue(store instanceof OpenVikingMemoryStore);
        }

        @Test
        void create_unknownType_throws() {
            assertThrows(IllegalArgumentException.class, () ->
                    MemoryAdapterFactory.create("redis"));
        }
    }

    @Nested
    class Mem0Tests {

        @Test
        void defaultConstructor() {
            Mem0MemoryStore store = new Mem0MemoryStore();
            assertNotNull(store);
        }

        @Test
        void customConstructor() {
            Mem0MemoryStore store = new Mem0MemoryStore("http://mem0.example.com", "my-key");
            assertNotNull(store);
        }

        @Test
        void implementsMemoryStore() {
            Mem0MemoryStore store = new Mem0MemoryStore();
            assertTrue(store instanceof MemoryStore);
        }
    }

    @Nested
    class OpenVikingTests {

        @Test
        void defaultConstructor() {
            OpenVikingMemoryStore store = new OpenVikingMemoryStore();
            assertNotNull(store);
        }

        @Test
        void builderPattern() {
            OpenVikingMemoryStore store = OpenVikingMemoryStore.builder()
                    .url("http://localhost:1933")
                    .apiKey("sk-test")
                    .agentId("agent-1")
                    .build();
            assertNotNull(store);
        }

        @Test
        void builder_withApiKeys() {
            OpenVikingMemoryStore store = OpenVikingMemoryStore.builder()
                    .url("http://localhost:1933")
                    .apiKeys(Map.of("user1", "key1", "user2", "key2"))
                    .build();
            assertNotNull(store);
        }

        @Test
        void resolveApiKey_directMatch() {
            OpenVikingMemoryStore store = OpenVikingMemoryStore.builder()
                    .apiKey("default-key")
                    .apiKeys(Map.of("session1", "session-key"))
                    .build();
            assertEquals("session-key", store.resolveApiKey("session1"));
        }

        @Test
        void resolveApiKey_fallbackToDefault() {
            OpenVikingMemoryStore store = OpenVikingMemoryStore.builder()
                    .apiKey("default-key")
                    .build();
            assertEquals("default-key", store.resolveApiKey("unknown-session"));
        }

        @Test
        void resolveApiKey_userIdFromPath() {
            OpenVikingMemoryStore store = OpenVikingMemoryStore.builder()
                    .apiKey("default-key")
                    .apiKeys(Map.of("user123", "user-key"))
                    .build();
            assertEquals("user-key", store.resolveApiKey("user123/session456"));
        }

        @Test
        void resolveApiKey_customFunction() {
            OpenVikingMemoryStore store = OpenVikingMemoryStore.builder()
                    .resolveApiKey(sessionId -> "custom-" + sessionId)
                    .build();
            assertEquals("custom-s1", store.resolveApiKey("s1"));
        }

        @Test
        void implementsMemoryStore() {
            OpenVikingMemoryStore store = new OpenVikingMemoryStore();
            assertTrue(store instanceof MemoryStore);
        }
    }
}
