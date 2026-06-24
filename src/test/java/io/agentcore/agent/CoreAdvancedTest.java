package io.agentcore.agent;

import io.agentcore.model.Message.*;
import io.agentcore.extensions.HookTypes.*;
import io.agentcore.tools.*;
import io.agentcore.model.ThinkingLevel;

import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import io.agentcore.model.Message;
import io.agentcore.model.Content;
import io.agentcore.model.AgentEvent;
import io.agentcore.model.ToolResult;

class CoreAdvancedTest {

    @Nested
    class QueueAdvancedTests {

        @Test
        void defaultMode_isOneAtATime() {
            var q = new PendingMessageQueue();
            assertEquals(PendingMessageQueue.DrainMode.ONE_AT_A_TIME, q.mode());
        }

        @Test
        void setMode_changesBehavior() {
            var q = new PendingMessageQueue();
            q.enqueue(new UserMessage(List.of(), 1.0));
            q.enqueue(new UserMessage(List.of(), 2.0));
            assertEquals(1, q.drain().size());

            q.setMode(PendingMessageQueue.DrainMode.ALL);
            q.enqueue(new UserMessage(List.of(), 3.0));
            q.enqueue(new UserMessage(List.of(), 4.0));
            assertEquals(3, q.drain().size());
        }

        @Test
        void concurrentEnqueue_threadSafe() throws Exception {
            var q = new PendingMessageQueue(PendingMessageQueue.DrainMode.ALL);
            int count = 100;
            ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor();
            try {
                for (int i = 0; i < count; i++) {
                    exec.submit(() -> q.enqueue(new UserMessage(List.of(), 0)));
                }
            } finally {
                exec.shutdown();
                exec.awaitTermination(5, TimeUnit.SECONDS);
            }
            assertEquals(count, q.drain().size());
        }

        @Test
        void drain_empty_returnsImmutableList() {
            var q = new PendingMessageQueue();
            var result = q.drain();
            assertTrue(result.isEmpty());
            assertThrows(UnsupportedOperationException.class, () -> result.add(null));
        }
    }

    @Nested
    class ContextAdvancedTests {

        @Test
        void thinkingLevel_constants() {
            assertEquals("off", ThinkingLevel.OFF.value());
            assertEquals("minimal", ThinkingLevel.MINIMAL.value());
            assertEquals("low", ThinkingLevel.LOW.value());
            assertEquals("medium", ThinkingLevel.MEDIUM.value());
            assertEquals("high", ThinkingLevel.HIGH.value());
            assertEquals("xhigh", ThinkingLevel.XHIGH.value());
        }

        @Test
        void streamingLifecycle() {
            var ctx = new AgentContext();
            assertFalse(ctx.isStreaming());
            assertTrue(ctx.tryStartStreaming());
            assertTrue(ctx.isStreaming());
            assertFalse(ctx.tryStartStreaming()); // already streaming

            ctx.stopStreaming();
            assertFalse(ctx.isStreaming());
        }

        @Test
        void addAndReplaceMessages() {
            var ctx = new AgentContext();
            ctx.addMessage(new Message.UserMessage(List.of(), 1.0));
            ctx.addMessage(new Message.UserMessage(List.of(), 2.0));
            assertEquals(2, ctx.messages().size());

            ctx.replaceMessages(List.of(new Message.UserMessage(List.of(), 3.0)));
            assertEquals(1, ctx.messages().size());
        }

        @Test
        void resetState() {
            var ctx = new AgentContext("system prompt", List.of());
            ctx.addMessage(new Message.UserMessage(List.of(), 1.0));
            ctx.tryStartStreaming();
            ctx.setErrorMessage("error");

            ctx.resetState();

            assertTrue(ctx.messages().isEmpty());
            assertFalse(ctx.isStreaming());
            assertNull(ctx.errorMessage());
            assertEquals("system prompt", ctx.systemPrompt()); // preserved
        }

        @Test
        void errorMessage_setAndGet() {
            var ctx = new AgentContext();
            assertNull(ctx.errorMessage());
            ctx.setErrorMessage("something went wrong");
            assertEquals("something went wrong", ctx.errorMessage());
        }
    }

    @Nested
    class ToolRunnerAdvancedTests {

        @Test
        void emptyToolCalls_returnsEmpty() {
            var registry = new ToolRegistry();
            var runner = new ToolRunner(registry, null, null, null);
            var assistant = AssistantMessage.builder().build();

            assertTrue(runner.executeSequential(assistant, null, null).messages().isEmpty());
            assertTrue(runner.executeParallel(assistant, null, null).messages().isEmpty());
        }

        @Test
        void beforeHook_blocksExecution() {
            var registry = new ToolRegistry();
            registry.register(new Tool() {
                private final ToolDefinition def = new ToolDefinition("echo", "Echo", Map.of());
                @Override public ToolDefinition definition() { return def; }
                @Override public ToolResult execute(String id, Map<String, Object> params, ToolContext ctx) {
                    return new ToolResult("should not reach");
                }
            });

            var runner = new ToolRunner(registry, null,
                    ctx -> new ToolCallHookResult.Block("Blocked!"), null);

            var assistant = AssistantMessage.builder()
                    .addContent(new Content.ToolCallContent("tc1", "echo", Map.of()))
                    .build();

            var results = runner.executeSequential(assistant, null, null).messages();
            assertEquals(1, results.size());
            assertTrue(results.get(0).isError());
        }

        @Test
        void afterHook_calledWithResult() {
            var registry = new ToolRegistry();
            registry.register(new Tool() {
                private final ToolDefinition def = new ToolDefinition("echo", "Echo", Map.of());
                @Override public ToolDefinition definition() { return def; }
                @Override public ToolResult execute(String id, Map<String, Object> params, ToolContext ctx) {
                    return new ToolResult("hello");
                }
            });

            AtomicBoolean hookCalled = new AtomicBoolean(false);
            var runner = new ToolRunner(registry, null, null,
                    ctx -> { hookCalled.set(true); return null; });

            var assistant = AssistantMessage.builder()
                    .addContent(new Content.ToolCallContent("tc1", "echo", Map.of()))
                    .build();

            runner.executeSequential(assistant, null, null);
            assertTrue(hookCalled.get());
        }

        @Test
        void hookFailure_nonFatal() {
            var registry = new ToolRegistry();
            registry.register(new Tool() {
                private final ToolDefinition def = new ToolDefinition("echo", "Echo", Map.of());
                @Override public ToolDefinition definition() { return def; }
                @Override public ToolResult execute(String id, Map<String, Object> params, ToolContext ctx) {
                    return new ToolResult("ok");
                }
            });

            var runner = new ToolRunner(registry, null,
                    ctx -> { throw new RuntimeException("hook boom"); },
                    ctx -> { throw new RuntimeException("hook boom"); });

            var assistant = AssistantMessage.builder()
                    .addContent(new Content.ToolCallContent("tc1", "echo", Map.of()))
                    .build();

            var results = runner.executeSequential(assistant, null, null).messages();
            assertEquals(1, results.size());
            assertFalse(results.get(0).isError());
        }

        @Test
        void timeout_returnsError() {
            var registry = new ToolRegistry();
            registry.register(new Tool() {
                private final ToolDefinition def = new ToolDefinition("sleeper", "Sleeps", Map.of());
                @Override public ToolDefinition definition() { return def; }
                @Override public ToolResult execute(String id, Map<String, Object> params, ToolContext ctx) throws Exception {
                    Thread.sleep(10_000); // 10 seconds
                    return new ToolResult("should not reach");
                }
            });

            var runner = new ToolRunner(registry,
                    new AgentLoopConfig.ToolConfig(0.5, 4000, AgentLoopConfig.ToolExecutionMode.PARALLEL),
                    null, null);
            var assistant = AssistantMessage.builder()
                    .addContent(new Content.ToolCallContent("tc1", "sleeper", Map.of()))
                    .build();

            var results = runner.executeSequential(assistant, null, null).messages();
            assertEquals(1, results.size());
            assertTrue(results.get(0).isError());
            assertTrue(results.get(0).content().stream()
                    .anyMatch(c -> c instanceof Content.TextContent tc
                            && tc.text().contains("timed out")));
        }
    }
}
