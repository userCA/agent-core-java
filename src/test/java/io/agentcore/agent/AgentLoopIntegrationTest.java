package io.agentcore.agent;

import io.agentcore.model.AgentEvent.*;
import io.agentcore.model.Content.TextContent;
import io.agentcore.model.Content.ToolCallContent;
import io.agentcore.model.Message.*;
import io.agentcore.llm.*;
import io.agentcore.llm.StreamEvent.*;
import io.agentcore.session.MemorySessionStore;
import io.agentcore.session.SessionHeader;
import io.agentcore.session.SessionSnapshot;
import io.agentcore.tools.Tool;
import io.agentcore.tools.ToolContext;
import io.agentcore.tools.ToolDefinition;
import io.agentcore.tools.ToolRegistry;
import io.agentcore.model.ToolResult;
import io.agentcore.extensions.Extension;
import io.agentcore.extensions.ExtensionRunner;
import io.agentcore.extensions.HookTypes.*;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import io.agentcore.model.Message;
import io.agentcore.model.AgentEvent;
import io.agentcore.model.Content;

/**
 * Integration tests for the full agent loop with mock providers.
 * Validates the end-to-end flow: prompt → LLM stream → tool execution → response.
 */
class AgentLoopIntegrationTest {

    // ── Mock Provider ─────────────────────────────────────────

    /**
     * A mock provider that returns pre-configured stream events.
     */
    static class MockProvider implements ModelProvider {
        private final Queue<List<StreamEvent>> scriptedResponses = new LinkedList<>();

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

        void enqueueToolCall(String toolId, String toolName, Map<String, Object> args) {
            List<StreamEvent> events = List.of(
                    new StreamToolCallStart(toolId, toolName),
                    new StreamToolCallEnd(toolId, args),
                    new StreamMessageEnd("tool_use", 15, 30)
            );
            scriptedResponses.add(events);
        }

        void enqueueToolCallThenText(String toolId, String toolName,
                                     Map<String, Object> args, String followUpText) {
            enqueueToolCall(toolId, toolName, args);
            enqueueResponse(followUpText);
        }

        @Override
        public String name() { return "mock"; }

        @Override
        public List<ModelInfo> listModels() {
            return List.of(new ModelInfo("mock", "mock-model", 4096, 2048));
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

    // ── Helper: simple echo tool ──────────────────────────────

    static class EchoTool implements Tool {
        @Override
        public ToolDefinition definition() {
            return new ToolDefinition("echo", "Echo the input",
                    Map.of("type", "object", "properties", Map.of(
                            "text", Map.of("type", "string", "description", "Text to echo")
                    ), "required", List.of("text")));
        }

        @Override
        public ToolResult execute(String toolCallId, Map<String, Object> params, ToolContext ctx) {
            return new ToolResult("Echo: " + params.get("text"));
        }
    }

    static class AddTool implements Tool {
        @Override
        public ToolDefinition definition() {
            return new ToolDefinition("add", "Add two numbers",
                    Map.of("type", "object", "properties", Map.of(
                            "a", Map.of("type", "number"),
                            "b", Map.of("type", "number")
                    ), "required", List.of("a", "b")));
        }

        @Override
        public ToolResult execute(String toolCallId, Map<String, Object> params, ToolContext ctx) {
            double a = ((Number) params.get("a")).doubleValue();
            double b = ((Number) params.get("b")).doubleValue();
            return new ToolResult(String.valueOf(a + b));
        }
    }

    // ── Helpers ────────────────────────────────────────────────

    private AgentLoopConfig buildConfig(MockProvider provider, ToolRegistry registry) {
        MessageConverter converter = new MessageConverter();
        return AgentLoopConfig.builder()
                .model(new ModelInfo("mock", "mock-model", 4096, 2048))
                .streamFn(provider::stream)
                .messageAssembler(converter::convert)
                .authResolver(name -> new ProviderAuth("test-key"))
                .toolRegistry(registry)
                .maxRetries(0)
                .build();
    }

    private List<AgentEvent> collectEvents(AgentLoop loop, List<Message> messages) {
        List<AgentEvent> events = new ArrayList<>();
        loop.run(messages, null, events::add);
        return events;
    }

    // ── Tests ─────────────────────────────────────────────────

    @Nested
    class SimpleTextResponse {

        @Test
        void emitsAgentStartAndEnd() {
            MockProvider provider = new MockProvider();
            provider.enqueueResponse("Hello!");
            AgentLoop loop = new AgentLoop(buildConfig(provider, new ToolRegistry()), new AgentContext());

            List<AgentEvent> events = collectEvents(loop, List.of(
                    new UserMessage(List.of(new TextContent("Hi")), 1000.0)));

            assertTrue(events.get(0) instanceof AgentStart);
            assertTrue(events.get(events.size() - 1) instanceof AgentEnd);
        }

        @Test
        void emitsTurnEvents() {
            MockProvider provider = new MockProvider();
            provider.enqueueResponse("Hello!");
            AgentLoop loop = new AgentLoop(buildConfig(provider, new ToolRegistry()), new AgentContext());

            List<AgentEvent> events = collectEvents(loop, List.of(
                    new UserMessage(List.of(new TextContent("Hi")), 1000.0)));

            assertTrue(events.stream().anyMatch(e -> e instanceof TurnStart));
            assertTrue(events.stream().anyMatch(e -> e instanceof TurnEnd));
        }

        @Test
        void assistantMessageContainsText() {
            MockProvider provider = new MockProvider();
            provider.enqueueResponse("Hello, World!");
            AgentLoop loop = new AgentLoop(buildConfig(provider, new ToolRegistry()), new AgentContext());

            List<AgentEvent> events = collectEvents(loop, List.of(
                    new UserMessage(List.of(new TextContent("Hi")), 1000.0)));

            AgentEnd end = (AgentEnd) events.get(events.size() - 1);
            assertFalse(end.messages().isEmpty());

            AssistantMessage assistant = end.messages().stream()
                    .filter(m -> m instanceof AssistantMessage)
                    .map(m -> (AssistantMessage) m)
                    .findFirst().orElseThrow();
            assertEquals("Hello, World!", assistant.text());
        }

        @Test
        void usageIsCaptured() {
            MockProvider provider = new MockProvider();
            provider.enqueueResponse("Hi");
            AgentLoop loop = new AgentLoop(buildConfig(provider, new ToolRegistry()), new AgentContext());

            List<AgentEvent> events = collectEvents(loop, List.of(
                    new UserMessage(List.of(new TextContent("Hi")), 1000.0)));

            AgentEnd end = (AgentEnd) events.get(events.size() - 1);
            AssistantMessage assistant = end.messages().stream()
                    .filter(m -> m instanceof AssistantMessage)
                    .map(m -> (AssistantMessage) m)
                    .findFirst().orElseThrow();
            assertEquals(10, assistant.usage().inputTokens());
            assertEquals(20, assistant.usage().outputTokens());
        }
    }

    @Nested
    class ToolExecution {

        @Test
        void toolCallTriggersExecution() {
            MockProvider provider = new MockProvider();
            provider.enqueueToolCallThenText("tc1", "echo",
                    Map.of("text", "hello"), "Done!");

            ToolRegistry registry = new ToolRegistry();
            registry.register(new EchoTool());

            AgentLoop loop = new AgentLoop(buildConfig(provider, registry), new AgentContext());

            List<AgentEvent> events = collectEvents(loop, List.of(
                    new UserMessage(List.of(new TextContent("Use echo")), 1000.0)));

            // Should have tool execution events
            assertTrue(events.stream().anyMatch(e -> e instanceof ToolExecutionStart));
            assertTrue(events.stream().anyMatch(e -> e instanceof ToolExecutionEnd));
        }

        @Test
        void toolResultAddedToContext() {
            MockProvider provider = new MockProvider();
            provider.enqueueToolCallThenText("tc1", "echo",
                    Map.of("text", "hello"), "Done!");

            ToolRegistry registry = new ToolRegistry();
            registry.register(new EchoTool());

            AgentContext ctx = new AgentContext();
            AgentLoop loop = new AgentLoop(buildConfig(provider, registry), ctx);

            collectEvents(loop, List.of(
                    new UserMessage(List.of(new TextContent("Use echo")), 1000.0)));

            // Context should contain: user message + assistant (with tool call) + tool result + assistant (text)
            assertTrue(ctx.messages().size() >= 4);
        }

        @Test
        void unknownToolReturnsError() {
            MockProvider provider = new MockProvider();
            provider.enqueueToolCallThenText("tc1", "nonexistent",
                    Map.of(), "Fallback response");

            AgentContext ctx = new AgentContext();
            AgentLoop loop = new AgentLoop(buildConfig(provider, new ToolRegistry()), ctx);

            List<AgentEvent> events = collectEvents(loop, List.of(
                    new UserMessage(List.of(new TextContent("Use tool")), 1000.0)));

            // Should have a tool execution end with error
            var toolEnd = events.stream()
                    .filter(e -> e instanceof ToolExecutionEnd)
                    .map(e -> (ToolExecutionEnd) e)
                    .findFirst();
            assertTrue(toolEnd.isPresent());
            assertTrue(toolEnd.get().isError());
        }

        @Test
        void parallelToolExecution() {
            MockProvider provider = new MockProvider();
            // Enqueue two tool calls in one response
            List<StreamEvent> events = List.of(
                    new StreamToolCallStart("tc1", "echo"),
                    new StreamToolCallEnd("tc1", Map.of("text", "first")),
                    new StreamToolCallStart("tc2", "add"),
                    new StreamToolCallEnd("tc2", Map.of("a", 3, "b", 4)),
                    new StreamMessageEnd("tool_use", 20, 40)
            );
            provider.scriptedResponses.add(events);
            provider.enqueueResponse("Results received!");

            ToolRegistry registry = new ToolRegistry();
            registry.register(new EchoTool());
            registry.register(new AddTool());

            AgentLoopConfig config = AgentLoopConfig.builder()
                    .model(new ModelInfo("mock", "mock-model", 4096, 2048))
                    .streamFn(provider::stream)
                    .messageAssembler(new MessageConverter()::convert)
                    .authResolver(name -> new ProviderAuth("test-key"))
                    .toolRegistry(registry)
                    .toolExecution(AgentLoopConfig.ToolExecutionMode.PARALLEL)
                    .maxRetries(0)
                    .build();

            AgentContext ctx = new AgentContext();
            AgentLoop loop = new AgentLoop(config, ctx);

            List<AgentEvent> collectedEvents = collectEvents(loop, List.of(
                    new UserMessage(List.of(new TextContent("Run both")), 1000.0)));

            // Both tools should have been executed
            long toolStarts = collectedEvents.stream()
                    .filter(e -> e instanceof ToolExecutionStart).count();
            assertEquals(2, toolStarts);

            long toolEnds = collectedEvents.stream()
                    .filter(e -> e instanceof ToolExecutionEnd).count();
            assertEquals(2, toolEnds);
        }
    }

    @Nested
    class AbortSignal {

        @Test
        void abortStopsTheLoop() {
            MockProvider provider = new MockProvider();
            provider.enqueueResponse("First");
            provider.enqueueResponse("Second");

            AgentLoopConfig config = AgentLoopConfig.builder()
                    .model(new ModelInfo("mock", "mock-model", 4096, 2048))
                    .streamFn(provider::stream)
                    .messageAssembler(new MessageConverter()::convert)
                    .authResolver(name -> new ProviderAuth("test-key"))
                    .maxTurns(10)
                    .maxRetries(0)
                    .build();

            AtomicBoolean signal = new AtomicBoolean(false);
            List<AgentEvent> events = new ArrayList<>();
            AgentLoop loop = new AgentLoop(config, new AgentContext());

            // Abort immediately before run
            signal.set(true);
            loop.run(List.of(new UserMessage(List.of(new TextContent("Hi")), 1000.0)),
                    signal, events::add);

            // Should have agent start/end but no turns
            assertTrue(events.stream().anyMatch(e -> e instanceof AgentStart));
            assertTrue(events.stream().anyMatch(e -> e instanceof AgentEnd));
            long turnCount = events.stream().filter(e -> e instanceof TurnStart).count();
            assertEquals(0, turnCount);
        }
    }

    @Nested
    class MaxTurnsLimit {

        @Test
        void respectsMaxTurns() {
            MockProvider provider = new MockProvider();
            // Queue enough responses for multiple turns
            for (int i = 0; i < 10; i++) {
                provider.enqueueResponse("Response " + i);
            }

            AgentLoopConfig config = AgentLoopConfig.builder()
                    .model(new ModelInfo("mock", "mock-model", 4096, 2048))
                    .streamFn(provider::stream)
                    .messageAssembler(new MessageConverter()::convert)
                    .authResolver(name -> new ProviderAuth("test-key"))
                    .maxTurns(3)
                    .maxRetries(0)
                    .build();

            AgentContext ctx = new AgentContext();
            AgentLoop loop = new AgentLoop(config, ctx);

            List<AgentEvent> events = collectEvents(loop, List.of(
                    new UserMessage(List.of(new TextContent("Hi")), 1000.0)));

            // Since responses are all text (no tool calls), loop should exit after 1 turn
            // (no tool results to continue on)
            long turnCount = events.stream().filter(e -> e instanceof TurnStart).count();
            assertTrue(turnCount <= 3);
        }
    }

    @Nested
    class AgentFacadeTest {

        @Test
        void agentPromptReturnsAssistantMessages() {
            MockProvider provider = new MockProvider();
            provider.enqueueResponse("I'm an agent!");

            ToolRegistry registry = new ToolRegistry();
            registry.register(new EchoTool());

            Agent agent = Agent.create(
                    provider,
                    new ModelInfo("mock", "mock-model", 4096, 2048),
                    AuthSource.staticAuth("test-key"),
                    registry,
                    "You are a helpful assistant."
            );

            List<Message> responses = agent.prompt("Hello", null);
            assertFalse(responses.isEmpty());
            assertTrue(responses.get(0) instanceof AssistantMessage);
            assertEquals("I'm an agent!", ((AssistantMessage) responses.get(0)).text());
        }

        @Test
        void agentContextAccumulatesMessages() {
            MockProvider provider = new MockProvider();
            provider.enqueueResponse("First");
            provider.enqueueResponse("Second");

            Agent agent = Agent.create(
                    provider,
                    new ModelInfo("mock", "mock-model", 4096, 2048),
                    AuthSource.staticAuth("test-key"),
                    new ToolRegistry(),
                    "System prompt"
            );

            agent.prompt("Hello", null);
            int sizeAfterFirst = agent.messages().size();

            agent.prompt("Follow-up", null);
            int sizeAfterSecond = agent.messages().size();

            assertTrue(sizeAfterSecond > sizeAfterFirst);
        }

        @Test
        void agentEventSubscriptionWorks() {
            MockProvider provider = new MockProvider();
            provider.enqueueResponse("Subscribed!");

            Agent agent = Agent.create(
                    provider,
                    new ModelInfo("mock", "mock-model", 4096, 2048),
                    AuthSource.staticAuth("test-key"),
                    new ToolRegistry(),
                    "System prompt"
            );

            List<AgentEvent> received = new ArrayList<>();
            agent.subscribe(received::add);

            agent.prompt("Hello", null);

            assertTrue(received.stream().anyMatch(e -> e instanceof AgentStart));
            assertTrue(received.stream().anyMatch(e -> e instanceof AgentEnd));
        }
    }

    @Nested
    class SessionStoreIntegration {

        @Test
        void memorySessionStoreSavesAndLoads() {
            MemorySessionStore store = new MemorySessionStore();
            String sessionId = "test-session-123";

            store.createSession(sessionId, new SessionHeader(sessionId, "2024-01-01", "/tmp"));

            SessionSnapshot snapshot = store.loadSession(sessionId);
            assertNotNull(snapshot);
            assertEquals(0, snapshot.entries().size());
        }
    }

    @Nested
    class ToolRegistryIntegration {

        @Test
        void registryHoldsAndRetrievesTools() {
            ToolRegistry registry = new ToolRegistry();
            EchoTool echo = new EchoTool();
            AddTool add = new AddTool();

            registry.register(echo);
            registry.register(add);

            assertEquals(2, registry.size());
            assertTrue(registry.contains("echo"));
            assertTrue(registry.contains("add"));
            assertFalse(registry.contains("unknown"));

            assertNotNull(registry.get("echo"));
            assertNull(registry.get("unknown"));
        }

        @Test
        void toProviderFormatProducesValidDefinitions() {
            ToolRegistry registry = new ToolRegistry();
            registry.register(new EchoTool());

            List<Map<String, Object>> formats = registry.toProviderFormat();
            assertEquals(1, formats.size());

            Map<String, Object> fmt = formats.get(0);
            assertEquals("function", fmt.get("type"));
            @SuppressWarnings("unchecked")
            Map<String, Object> fn = (Map<String, Object>) fmt.get("function");
            assertEquals("echo", fn.get("name"));
        }
    }

    @Nested
    class ExtensionRunnerIntegration {

        @Test
        void onBeforeToolCallHookIsApplied() {
            ExtensionRunner runner = new ExtensionRunner(List.of(
                    new Extension() {
                        @Override public String name() { return "test-hook"; }
                        @Override
                        public ToolCallHookResult onBeforeToolCall(ToolCallContext context) {
                            return new ToolCallHookResult.InjectMetadata(Map.of("custom", "value"));
                        }
                    }
            ));

            var tc = new ToolCallContent("tc1", "echo", Map.of());
            ToolCallHookResult result = runner.onBeforeToolCall(new ToolCallContext(tc, Map.of()));
            assertNotNull(result);
            assertInstanceOf(ToolCallHookResult.InjectMetadata.class, result);
        }

        @Test
        void eventForwardingWorks() {
            List<AgentEvent> received = new ArrayList<>();

            Extension ext = new Extension() {
                @Override public String name() { return "listener"; }
                @Override
                public void onEvent(AgentEvent evt) {
                    received.add(evt);
                }
            };

            ExtensionRunner runner = new ExtensionRunner(List.of(ext));
            AgentStart event = new AgentStart();
            runner.onEvent(event);

            assertEquals(1, received.size());
            assertTrue(received.get(0) instanceof AgentStart);
        }
    }
}
