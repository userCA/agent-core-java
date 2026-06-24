package io.agentcore.tools;

import io.agentcore.agent.*;
import io.agentcore.model.Content.TextContent;
import io.agentcore.model.Content.ToolCallContent;
import io.agentcore.model.Message.*;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import io.agentcore.model.AgentEvent;
import io.agentcore.model.ToolResult;
import io.agentcore.model.Message;

/**
 * Tests for Phase 3: Tool system, Session stores, Compactor.
 */
class Phase3Test {

    // ── Tool types ───────────────────────────────────────────

    @Nested
    class ToolDefinitionTest {

        @Test
        void createMinimal() {
            var def = new ToolDefinition("search", "Search the web", Map.of());
            assertEquals("search", def.name());
            assertEquals("Search the web", def.description());
            assertNotNull(def.parameters());
        }

        @Test
        void toProviderFormat() {
            var def = new ToolDefinition("search", "Search the web",
                    Map.of("type", "object", "properties", Map.of("q", Map.of("type", "string"))));
            var format = def.toProviderFormat();
            assertEquals("function", format.get("type"));
            @SuppressWarnings("unchecked")
            Map<String, Object> fn = (Map<String, Object>) format.get("function");
            assertEquals("search", fn.get("name"));
        }

        @Test
        void requireName() {
            assertThrows(IllegalArgumentException.class, () -> new ToolDefinition("", "desc", Map.of()));
        }
    }

    @Nested
    class ToolResultTest {

        @Test
        void simpleTextResult() {
            var result = new ToolResult("Found it");
            assertEquals("Found it", result.text());
            assertEquals(1, result.content().size());
        }

        @Test
        void emptyResult() {
            var result = new ToolResult(List.of());
            assertTrue(result.content().isEmpty());
            assertEquals("", result.text());
        }
    }

    @Nested
    class ToolRegistryTest {

        @Test
        void registerAndRetrieve() {
            var registry = new ToolRegistry();
            Tool tool = createTool("test_tool", "A test tool");
            registry.register(tool);

            assertNotNull(registry.get("test_tool"));
            assertNull(registry.get("nonexistent"));
            assertTrue(registry.contains("test_tool"));
            assertEquals(1, registry.size());
        }

        @Test
        void listTools() {
            var registry = new ToolRegistry();
            registry.register(createTool("tool_a", "Tool A"));
            registry.register(createTool("tool_b", "Tool B"));

            var infos = registry.list();
            assertEquals(2, infos.size());

            var defs = registry.toDefinitions();
            assertEquals(2, defs.size());

            var providerFormat = registry.toProviderFormat();
            assertEquals(2, providerFormat.size());
        }

        private Tool createTool(String name, String desc) {
            return new Tool() {
                private final ToolDefinition def = new ToolDefinition(name, desc, Map.of());
                @Override public ToolDefinition definition() { return def; }
                @Override public ToolResult execute(String toolCallId, Map<String, Object> params, ToolContext ctx) {
                    return new ToolResult("OK");
                }
            };
        }
    }

    // ── ToolRunner ───────────────────────────────────────────

    @Nested
    class ToolRunnerTest {

        @Test
        void executeSequential() {
            var registry = new ToolRegistry();
            registry.register(new Tool() {
                private final ToolDefinition def = new ToolDefinition("echo", "Echo input", Map.of());
                @Override public ToolDefinition definition() { return def; }
                @Override public ToolResult execute(String toolCallId, Map<String, Object> params, ToolContext ctx) {
                    return new ToolResult("Echo: " + params.getOrDefault("text", ""));
                }
            });

            var runner = new ToolRunner(registry, null, null, null);
            var assistant = AssistantMessage.builder()
                    .addContent(new ToolCallContent("tc1", "echo", Map.of("text", "hello")))
                    .build();

            List<AgentEvent> events = new ArrayList<>();
            var results = runner.executeSequential(assistant, null, events::add).messages();

            assertEquals(1, results.size());
            assertEquals("tc1", results.get(0).toolCallId());
            assertFalse(results.get(0).isError());

            // Should have start + end events
            assertEquals(2, events.size());
            assertInstanceOf(AgentEvent.ToolExecutionStart.class, events.get(0));
            assertInstanceOf(AgentEvent.ToolExecutionEnd.class, events.get(1));
        }

        @Test
        void toolNotFound() {
            var registry = new ToolRegistry();
            var runner = new ToolRunner(registry, null, null, null);

            var assistant = AssistantMessage.builder()
                    .addContent(new ToolCallContent("tc1", "nonexistent", Map.of()))
                    .build();

            var results = runner.executeSequential(assistant, null, null).messages();
            assertEquals(1, results.size());
            assertTrue(results.get(0).isError());
        }

        @Test
        void executeParallel() {
            var registry = new ToolRegistry();
            for (String name : List.of("tool_a", "tool_b", "tool_c")) {
                registry.register(new Tool() {
                    private final ToolDefinition def = new ToolDefinition(name, name, Map.of());
                    @Override public ToolDefinition definition() { return def; }
                    @Override public ToolResult execute(String toolCallId, Map<String, Object> params, ToolContext ctx) {
                        return new ToolResult("Result from " + name);
                    }
                });
            }

            var runner = new ToolRunner(registry, null, null, null);
            var assistant = AssistantMessage.builder()
                    .addContent(new ToolCallContent("tc1", "tool_a", Map.of()))
                    .addContent(new ToolCallContent("tc2", "tool_b", Map.of()))
                    .addContent(new ToolCallContent("tc3", "tool_c", Map.of()))
                    .build();

            var results = runner.executeParallel(assistant, null, null).messages();
            assertEquals(3, results.size());
            results.forEach(r -> assertFalse(r.isError()));
        }
    }

    // ── Session stores ───────────────────────────────────────

    @Nested
    class MemorySessionStoreTest {

        @Test
        void createAndLoad() {
            var store = new io.agentcore.session.MemorySessionStore();
            var header = new io.agentcore.session.SessionHeader("sess-1", "2024-01-01T00:00:00Z");
            store.createSession("sess-1", header);

            store.appendEntry("sess-1", new io.agentcore.session.SessionEntry.MessageEntry(
                    "msg-1", Map.of("role", "user", "content", "Hello"), null));

            var snapshot = store.loadSession("sess-1");
            assertEquals("sess-1", snapshot.header().id());
            assertEquals(1, snapshot.entries().size());
        }

        @Test
        void loadNonexistent() {
            var store = new io.agentcore.session.MemorySessionStore();
            assertThrows(IllegalArgumentException.class, () -> store.loadSession("nonexistent"));
        }

        @Test
        void listSessions() {
            var store = new io.agentcore.session.MemorySessionStore();
            store.createSession("s1", new io.agentcore.session.SessionHeader("s1", "2024-01-01"));
            store.createSession("s2", new io.agentcore.session.SessionHeader("s2", "2024-01-02"));

            var list = store.listSessions(null, 50);
            assertEquals(2, list.size());
        }
    }

    @Nested
    class JsonlSessionStoreTest {

        @Test
        void createAppendAndLoad() throws Exception {
            var tmpDir = java.nio.file.Files.createTempDirectory("jsonl-test");
            var store = new io.agentcore.session.JsonlSessionStore(tmpDir);

            var header = new io.agentcore.session.SessionHeader("test-session", "2024-01-01T00:00:00Z", "/tmp");
            store.createSession("test-session", header);

            store.appendEntry("test-session", new io.agentcore.session.SessionEntry.MessageEntry(
                    "msg-1", Map.of("role", "user", "content", "Hello world"), null));

            store.appendEntry("test-session", new io.agentcore.session.SessionEntry.CompactionEntry(
                    "comp-1", "Summary text", "msg-1", 100));

            var snapshot = store.loadSession("test-session");
            assertEquals("test-session", snapshot.header().id());
            assertEquals(2, snapshot.entries().size());
            assertInstanceOf(io.agentcore.session.SessionEntry.MessageEntry.class, snapshot.entries().get(0));
            assertInstanceOf(io.agentcore.session.SessionEntry.CompactionEntry.class, snapshot.entries().get(1));
        }

        @Test
        void listSessions() throws Exception {
            var tmpDir = java.nio.file.Files.createTempDirectory("jsonl-list-test");
            var store = new io.agentcore.session.JsonlSessionStore(tmpDir);

            store.createSession("s1", new io.agentcore.session.SessionHeader("s1", "2024-01-01"));
            store.appendEntry("s1", new io.agentcore.session.SessionEntry.MessageEntry(
                    "m1", Map.of("role", "user", "content", "First message"), null));

            store.createSession("s2", new io.agentcore.session.SessionHeader("s2", "2024-01-02"));

            var list = store.listSessions(null, 50);
            assertEquals(2, list.size());
        }
    }

    // ── Compactor ────────────────────────────────────────────

    @Nested
    class CompactorTest {

        @Test
        void tokenEstimation() {
            var msg = new UserMessage(List.of(new TextContent("Hello, this is a test message")),
                    System.currentTimeMillis() / 1000.0);
            int tokens = io.agentcore.session.compaction.Compactor.estimateTokens(msg);
            assertTrue(tokens > 0);
        }

        @Test
        void shouldCompactThreshold() {
            var compactor = new io.agentcore.session.compaction.LLMSummaryCompactor(null, 0.8, 2);

            // Create messages that exceed 80% of a 100-token window
            List<Message> messages = new java.util.ArrayList<>();
            for (int i = 0; i < 20; i++) {
                messages.add(new UserMessage(
                        List.of(new TextContent("x".repeat(100))),
                        System.currentTimeMillis() / 1000.0));
            }

            assertTrue(compactor.shouldCompact(messages, 100));
            assertFalse(compactor.shouldCompact(messages.subList(0, 2), 100000));
        }

        @Test
        void compactKeepsRecent() {
            var compactor = new io.agentcore.session.compaction.LLMSummaryCompactor(
                    msgs -> "Summary of " + msgs.size() + " messages",
                    0.8, 2);

            List<Message> messages = new java.util.ArrayList<>();
            for (int i = 0; i < 6; i++) {
                messages.add(new UserMessage(
                        List.of(new TextContent("Message " + i)),
                        System.currentTimeMillis() / 1000.0));
            }

            var result = compactor.compact(messages, "threshold", null, null);
            assertEquals(2, result.keptCount());
            assertTrue(result.summary().contains("4 messages"));
            assertTrue(result.tokensBefore() > 0);
        }

        @Test
        void compactEmpty() {
            var compactor = new io.agentcore.session.compaction.LLMSummaryCompactor();
            var result = compactor.compact(List.of(), "manual", null, null);
            assertEquals("", result.summary());
            assertEquals(0, result.keptCount());
        }
    }
}
