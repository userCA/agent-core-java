package io.agentcore.agent;

import io.agentcore.extensions.HookTypes.*;
import io.agentcore.model.Content;
import io.agentcore.model.Content.TextContent;
import io.agentcore.model.Content.ToolCallContent;
import io.agentcore.model.Message.AssistantMessage;
import io.agentcore.model.Message.ToolResultMessage;
import io.agentcore.model.ToolResult;
import io.agentcore.tools.Tool;
import io.agentcore.tools.ToolContext;
import io.agentcore.tools.ToolDefinition;
import io.agentcore.tools.ToolRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ToolRunnerTest {

    private ToolRegistry registry;
    private ToolRunner runner;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        var toolConfig = new AgentLoopConfig.ToolConfig(120.0, 4000,
                AgentLoopConfig.ToolExecutionMode.SEQUENTIAL, 10);
        runner = new ToolRunner(registry, toolConfig, null, null);
    }

    @AfterEach
    void tearDown() {
        runner.close();
    }

    // ── Helper: create a simple mock tool ──

    private static Tool simpleTool(String name, ToolResult result) {
        return new Tool() {
            @Override public ToolDefinition definition() {
                return new ToolDefinition(name, "test tool", Map.of());
            }
            @Override public ToolResult execute(String toolCallId, Map<String, Object> params, ToolContext ctx) {
                return result;
            }
        };
    }

    private static Tool errorTool(String name) {
        return new Tool() {
            @Override public ToolDefinition definition() {
                return new ToolDefinition(name, "error tool", Map.of());
            }
            @Override public ToolResult execute(String toolCallId, Map<String, Object> params, ToolContext ctx) throws Exception {
                throw new RuntimeException("boom");
            }
        };
    }

    private static AssistantMessage assistantWithTools(ToolCallContent... calls) {
        var builder = AssistantMessage.builder()
                .stopReason(io.agentcore.model.Message.StopReason.TOOL_USE);
        for (var c : calls) builder.addContent(c);
        return builder.build();
    }

    // ── Sequential execution ──

    @Nested
    class Sequential {

        @Test
        void emptyToolCalls_returnsEmptyBatch() {
            var msg = assistantWithTools();
            var result = runner.executeSequential(msg, new AtomicBoolean(false), null);
            assertTrue(result.messages().isEmpty());
            assertFalse(result.shouldTerminate());
        }

        @Test
        void normalExecution_returnsResults() {
            registry.register(simpleTool("echo", new ToolResult("hello")));
            var msg = assistantWithTools(new ToolCallContent("c1", "echo", Map.of()));

            var result = runner.executeSequential(msg, new AtomicBoolean(false), null);

            assertEquals(1, result.messages().size());
            assertEquals("hello", Content.extractText(result.messages().get(0).content()));
            assertFalse(result.messages().get(0).isError());
            assertFalse(result.shouldTerminate());
        }

        @Test
        void toolThrows_returnsErrorResult() {
            registry.register(errorTool("failing"));
            var msg = assistantWithTools(new ToolCallContent("c1", "failing", Map.of()));

            var result = runner.executeSequential(msg, new AtomicBoolean(false), null);

            assertEquals(1, result.messages().size());
            assertTrue(result.messages().get(0).isError());
            assertTrue(Content.extractText(result.messages().get(0).content()).contains("boom"));
        }

        @Test
        void toolNotFound_returnsErrorResult() {
            var msg = assistantWithTools(new ToolCallContent("c1", "nonexistent", Map.of()));

            var result = runner.executeSequential(msg, new AtomicBoolean(false), null);

            assertEquals(1, result.messages().size());
            assertTrue(result.messages().get(0).isError());
            assertTrue(Content.extractText(result.messages().get(0).content()).contains("not found"));
        }

        @Test
        void abortSignal_stopsExecution() {
            registry.register(simpleTool("a", new ToolResult("a")));
            registry.register(simpleTool("b", new ToolResult("b")));
            var msg = assistantWithTools(
                    new ToolCallContent("c1", "a", Map.of()),
                    new ToolCallContent("c2", "b", Map.of()));

            AtomicBoolean signal = new AtomicBoolean(true);
            var result = runner.executeSequential(msg, signal, null);

            assertTrue(result.messages().isEmpty());
        }

        @Test
        void multipleTools_allExecute() {
            registry.register(simpleTool("a", new ToolResult("ra")));
            registry.register(simpleTool("b", new ToolResult("rb")));
            var msg = assistantWithTools(
                    new ToolCallContent("c1", "a", Map.of()),
                    new ToolCallContent("c2", "b", Map.of()));

            var result = runner.executeSequential(msg, new AtomicBoolean(false), null);

            assertEquals(2, result.messages().size());
            assertEquals("ra", Content.extractText(result.messages().get(0).content()));
            assertEquals("rb", Content.extractText(result.messages().get(1).content()));
        }
    }

    // ── Parallel execution ──

    @Nested
    class Parallel {

        @Test
        void parallelExecution_allToolsRun() {
            registry.register(simpleTool("a", new ToolResult("ra")));
            registry.register(simpleTool("b", new ToolResult("rb")));
            var msg = assistantWithTools(
                    new ToolCallContent("c1", "a", Map.of()),
                    new ToolCallContent("c2", "b", Map.of()));

            var result = runner.executeParallel(msg, new AtomicBoolean(false), null);

            assertEquals(2, result.messages().size());
            assertFalse(result.shouldTerminate());
        }

        @Test
        void parallelExecution_emptyCalls() {
            var msg = assistantWithTools();
            var result = runner.executeParallel(msg, new AtomicBoolean(false), null);
            assertTrue(result.messages().isEmpty());
        }
    }

    // ── Hook integration ──

    @Nested
    class Hooks {

        @Test
        void beforeHook_block_preventsExecution() {
            registry.register(simpleTool("echo", new ToolResult("should not run")));
            runner.updateHooks(
                    ctx -> new ToolCallHookResult.Block("blocked by test"),
                    null
            );

            var msg = assistantWithTools(new ToolCallContent("c1", "echo", Map.of()));
            var result = runner.executeSequential(msg, new AtomicBoolean(false), null);

            assertEquals(1, result.messages().size());
            assertTrue(result.messages().get(0).isError());
            assertTrue(Content.extractText(result.messages().get(0).content()).contains("blocked by test"));
        }

        @Test
        void beforeHook_proceed_mutatesArgs() {
            List<Map<String, Object>> capturedArgs = new ArrayList<>();
            Tool capturing = new Tool() {
                @Override public ToolDefinition definition() {
                    return new ToolDefinition("cap", "capture", Map.of());
                }
                @Override public ToolResult execute(String toolCallId, Map<String, Object> params, ToolContext ctx) {
                    capturedArgs.add(params);
                    return new ToolResult("ok");
                }
            };
            registry.register(capturing);

            runner.updateHooks(
                    ctx -> new ToolCallHookResult.Proceed(Map.of("injected", "value")),
                    null
            );

            var msg = assistantWithTools(new ToolCallContent("c1", "cap", Map.of("orig", "val")));
            runner.executeSequential(msg, new AtomicBoolean(false), null);

            assertEquals(1, capturedArgs.size());
            assertEquals("value", capturedArgs.get(0).get("injected"));
        }

        @Test
        void afterHook_modifyResult_overridesContent() {
            registry.register(simpleTool("echo", new ToolResult("original")));

            runner.updateHooks(null,
                    ctx -> new AfterToolCallHookResult.ModifyResult(
                            List.of(new TextContent("modified")))
            );

            var msg = assistantWithTools(new ToolCallContent("c1", "echo", Map.of()));
            var result = runner.executeSequential(msg, new AtomicBoolean(false), null);

            assertEquals("modified", Content.extractText(result.messages().get(0).content()));
        }

        @Test
        void afterHook_modifyResult_overridesTerminate() {
            Tool termTool = new Tool() {
                @Override public ToolDefinition definition() {
                    return new ToolDefinition("term", "term", Map.of());
                }
                @Override public ToolResult execute(String id, Map<String, Object> p, ToolContext ctx) {
                    ctx.requestTerminate();
                    return new ToolResult("done");
                }
            };
            registry.register(termTool);

            runner.updateHooks(null,
                    ctx -> new AfterToolCallHookResult.ModifyResult(null, null, null, false)
            );

            var msg = assistantWithTools(new ToolCallContent("c1", "term", Map.of()));
            var result = runner.executeSequential(msg, new AtomicBoolean(false), null);

            // After-hook overrode terminate to false
            assertFalse(result.shouldTerminate());
        }
    }

    // ── Truncation ──

    @Nested
    class Truncation {

        @Test
        void shortContent_notTruncated() {
            registry.register(simpleTool("echo", new ToolResult("short")));
            var msg = assistantWithTools(new ToolCallContent("c1", "echo", Map.of()));
            var result = runner.executeSequential(msg, new AtomicBoolean(false), null);
            assertEquals("short", Content.extractText(result.messages().get(0).content()));
        }

        @Test
        void longContent_truncated() {
            String longText = "x".repeat(5000);
            registry.register(simpleTool("echo", new ToolResult(longText)));
            var msg = assistantWithTools(new ToolCallContent("c1", "echo", Map.of()));
            var result = runner.executeSequential(msg, new AtomicBoolean(false), null);
            String text = Content.extractText(result.messages().get(0).content());
            assertTrue(text.length() < 5000);
            assertTrue(text.contains("[truncated"));
        }
    }

    // ── Event emission ──

    @Nested
    class Events {

        @Test
        void events_emittedForEachTool() {
            registry.register(simpleTool("a", new ToolResult("ra")));
            var events = new ArrayList<io.agentcore.model.AgentEvent>();

            var msg = assistantWithTools(new ToolCallContent("c1", "a", Map.of()));
            runner.executeSequential(msg, new AtomicBoolean(false), events::add);

            assertEquals(2, events.size());
            assertInstanceOf(io.agentcore.model.AgentEvent.ToolExecutionStart.class, events.get(0));
            assertInstanceOf(io.agentcore.model.AgentEvent.ToolExecutionEnd.class, events.get(1));
        }
    }
}
