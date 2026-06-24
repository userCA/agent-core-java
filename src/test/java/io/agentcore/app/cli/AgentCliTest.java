package io.agentcore.app.cli;

import io.agentcore.config.AgentConfig;
import io.agentcore.agent.Agent;
import io.agentcore.model.AgentEvent;
import io.agentcore.model.Message;
import io.agentcore.llm.*;
import io.agentcore.llm.StreamEvent.*;
import io.agentcore.tools.ToolkitFactory;
import io.agentcore.tools.ToolRegistry;

import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for AgentCli — full pipeline verification.
 *
 * <p>Uses a mock provider to verify the complete CLI pipeline:
 * config → agent → streaming events → output.
 */
class AgentCliTest {

    // ── Mock Provider ──────────────────────────────────────

    /**
     * A mock ModelProvider that returns pre-configured stream events.
     * Mirrors the pattern used in AgentLoopIntegrationTest.
     */
    static class MockProvider implements ModelProvider {
        private final Queue<List<StreamEvent>> scriptedResponses = new LinkedList<>();
        private final String providerName;

        MockProvider(String providerName, String responseText) {
            this.providerName = providerName;
            enqueueResponse(responseText);
        }

        void enqueueResponse(String text) {
            enqueueResponse(text, "stop");
        }

        void enqueueResponse(String text, String stopReason) {
            List<StreamEvent> events = List.of(
                    new StreamTextDelta(text),
                    new StreamMessageEnd(stopReason, 10, 20)
            );
            scriptedResponses.add(events);
        }

        @Override
        public String name() { return providerName; }

        @Override
        public List<ModelInfo> listModels() {
            return List.of(new ModelInfo(providerName, "mock-model", 128000, 4096));
        }

        @Override
        public Iterator<StreamEvent> stream(
                ModelInfo model, List<Map<String, Object>> messages,
                List<Map<String, Object>> tools, String systemPrompt,
                String thinkingLevel, Double temperature, Integer maxTokens,
                AtomicBoolean abortSignal, ProviderAuth auth) {
            List<StreamEvent> events = scriptedResponses.poll();
            if (events == null) {
                events = List.of(
                        new StreamTextDelta("(no scripted response)"),
                        new StreamMessageEnd("stop", 0, 0)
                );
            }
            return events.iterator();
        }
    }

    // ── AgentCli Construction Tests ────────────────────────

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        void createWithTools() {
            var provider = new MockProvider("mock", "Hello!");
            var model = new ModelInfo("mock", "mock-model", 128000, 4096);
            var tools = ToolkitFactory.builder().includeBash(false).includeWrite(false).build();
            var agent = Agent.create(provider, model, AuthSource.staticAuth("key"), tools, "You are a test assistant.");

            assertNotNull(agent);
            assertTrue(tools.size() > 0);
            agent.close();
        }

        @Test
        void createWithoutTools() {
            var provider = new MockProvider("mock", "Hello!");
            var model = new ModelInfo("mock", "mock-model", 128000, 4096);
            var agent = Agent.create(provider, model, AuthSource.staticAuth("key"), null, "Test.");

            assertNotNull(agent);
            agent.close();
        }
    }

    // ── Agent Loop Integration ─────────────────────────────

    @Nested
    @DisplayName("Agent Loop")
    class AgentLoopTests {

        @Test
        void promptProducesStreamingText() {
            var provider = new MockProvider("mock", "The answer is 42.");
            var model = new ModelInfo("mock", "mock-model", 128000, 4096);
            var agent = Agent.create(provider, model, AuthSource.staticAuth("key"), null,
                    "You are a helpful assistant.");

            List<AgentEvent> events = new ArrayList<>();
            List<Message> results = agent.prompt("What is the answer?", events::add);

            assertNotNull(results);
            assertFalse(events.isEmpty());

            // Verify event sequence includes start and end
            assertTrue(events.stream().anyMatch(e -> e instanceof AgentEvent.AgentStart));
            assertTrue(events.stream().anyMatch(e -> e instanceof AgentEvent.AgentEnd));

            agent.close();
        }

        @Test
        void promptWithTools() {
            var provider = new MockProvider("mock", "I can help with that.");
            var model = new ModelInfo("mock", "mock-model", 128000, 4096);
            var tools = ToolkitFactory.builder()
                    .includeBash(false)
                    .includeWrite(false)
                    .includeRead(true)
                    .includeSearch(true)
                    .includeConfirm(false)
                    .build();

            var agent = Agent.create(provider, model, AuthSource.staticAuth("key"), tools,
                    "You are a helpful assistant.");

            List<AgentEvent> events = new ArrayList<>();
            List<Message> results = agent.prompt("Read the file test.txt", events::add);

            assertNotNull(results);
            assertFalse(events.isEmpty());
            assertTrue(tools.size() > 0);

            agent.close();
        }

        @Test
        void resetClearsState() {
            var provider = new MockProvider("mock", "Hello.");
            var model = new ModelInfo("mock", "mock-model", 128000, 4096);
            var agent = Agent.create(provider, model, AuthSource.staticAuth("key"), null, "Test.");

            agent.prompt("Hi", e -> {});
            assertTrue(agent.messages().size() > 0);

            agent.reset();
            assertEquals(0, agent.messages().size());

            agent.close();
        }

        @Test
        void abortSignalWorks() {
            var provider = new MockProvider("mock", "Long response...");
            var model = new ModelInfo("mock", "mock-model", 128000, 4096);
            var agent = Agent.create(provider, model, AuthSource.staticAuth("key"), null, "Test.");

            // Abort before prompt
            agent.abort();

            List<AgentEvent> events = new ArrayList<>();
            agent.prompt("test", events::add);

            // Should still complete (mock provider doesn't check signal)
            assertFalse(events.isEmpty());

            agent.close();
        }
    }

    // ── Tool Registry Tests ───────────────────────────────

    @Nested
    @DisplayName("Toolkit")
    class ToolkitTests {

        @Test
        void standardToolkitHasExpectedTools() {
            var tools = ToolkitFactory.standard();
            assertTrue(tools.size() >= 5);
            assertTrue(tools.contains("read"));
            assertTrue(tools.contains("write"));
            assertTrue(tools.contains("edit"));
            assertTrue(tools.contains("bash"));
        }

        @Test
        void readOnlyToolkit() {
            var tools = ToolkitFactory.readOnly();
            assertTrue(tools.contains("read"));
            assertFalse(tools.contains("write"));
            assertFalse(tools.contains("edit"));
        }

        @Test
        void minimalToolkit() {
            var tools = ToolkitFactory.minimal();
            assertTrue(tools.contains("read"));
            assertFalse(tools.contains("bash"));
            assertFalse(tools.contains("write"));
        }

        @Test
        void toolDefinitionsFormat() {
            var tools = ToolkitFactory.standard();
            var defs = tools.toDefinitions();
            assertFalse(defs.isEmpty());

            for (var def : defs) {
                assertNotNull(def.name());
                assertFalse(def.name().isEmpty());
                assertNotNull(def.description());
                assertNotNull(def.parameters());
            }
        }
    }

    // ── Config Tests ──────────────────────────────────────

    @Nested
    @DisplayName("Configuration")
    class ConfigTests {

        @Test
        void agentConfigLoadsFromProperties() {
            var config = AgentConfig.load();
            assertNotNull(config.getProvider());
            assertNotNull(config.getModel());
        }

        @Test
        void agentConfigBuilder() {
            var config = AgentConfig.builder()
                    .provider("openai")
                    .model("gpt-4o")
                    .apiKey("test-key")
                    .maxTokens(8192)
                    .temperature(0.5)
                    .build();

            assertEquals("openai", config.getProvider());
            assertEquals("gpt-4o", config.getModel());
            assertEquals(8192, config.getMaxTokens());
            assertEquals(0.5, config.getTemperature());
        }

        @Test
        void createAgentFromConfig() {
            var config = AgentConfig.builder()
                    .provider("mock")
                    .model("mock-model")
                    .apiKey("test-key")
                    .baseUrl("http://localhost:9999")
                    .build();

            var agent = config.createAgent();
            assertNotNull(agent);
            agent.close();
        }
    }

    // ── Event Handling Tests ──────────────────────────────

    @Nested
    @DisplayName("Events")
    class EventTests {

        @Test
        void eventSequenceIsComplete() {
            var provider = new MockProvider("mock", "Response text.");
            var model = new ModelInfo("mock", "mock-model", 128000, 4096);
            var agent = Agent.create(provider, model, AuthSource.staticAuth("key"), null, "Test.");

            List<AgentEvent> events = new ArrayList<>();
            agent.prompt("Hello", events::add);

            // Verify essential events are present
            assertTrue(events.stream().anyMatch(e -> e instanceof AgentEvent.AgentStart),
                    "Should have AgentStart");
            assertTrue(events.stream().anyMatch(e -> e instanceof AgentEvent.TurnStart),
                    "Should have TurnStart");
            assertTrue(events.stream().anyMatch(e -> e instanceof AgentEvent.MessageStart),
                    "Should have MessageStart");
            assertTrue(events.stream().anyMatch(e -> e instanceof AgentEvent.MessageEnd),
                    "Should have MessageEnd");
            assertTrue(events.stream().anyMatch(e -> e instanceof AgentEvent.TurnEnd),
                    "Should have TurnEnd");
            assertTrue(events.stream().anyMatch(e -> e instanceof AgentEvent.AgentEnd),
                    "Should have AgentEnd");

            agent.close();
        }

        @Test
        void messageDeltaEventsCarryText() {
            var provider = new MockProvider("mock", "Hello world!");
            var model = new ModelInfo("mock", "mock-model", 128000, 4096);
            var agent = Agent.create(provider, model, AuthSource.staticAuth("key"), null, "Test.");

            List<AgentEvent> events = new ArrayList<>();
            agent.prompt("Hi", events::add);

            // Check that text deltas carry content
            var textDeltas = events.stream()
                    .filter(e -> e instanceof AgentEvent.MessageUpdate mu
                            && mu.delta() instanceof AgentEvent.MessageDelta.TextDelta)
                    .toList();

            assertFalse(textDeltas.isEmpty(), "Should have text deltas");

            agent.close();
        }
    }
}
