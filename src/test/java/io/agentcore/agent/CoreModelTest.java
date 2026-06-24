package io.agentcore.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentcore.llm.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import io.agentcore.model.Message;
import io.agentcore.model.Content;
import io.agentcore.model.AgentEvent;
import io.agentcore.model.ToolResult;
import io.agentcore.agent.Agent;
import io.agentcore.agent.AgentContext;
import io.agentcore.agent.AgentLoop;
import io.agentcore.agent.AgentLoopConfig;
import io.agentcore.agent.StreamAccumulator;
import io.agentcore.agent.ToolRunner;
import io.agentcore.agent.PendingMessageQueue;
import io.agentcore.agent.HumanInputGate;
import io.agentcore.tools.Tool;
import io.agentcore.tools.ToolContext;
import io.agentcore.tools.ToolDefinition;
import io.agentcore.tools.ToolRegistry;
import io.agentcore.extensions.Extension;
import io.agentcore.extensions.ExtensionRunner;
import io.agentcore.extensions.HookTypes;

/**
 * Comprehensive tests for all Phase 1 core data model types.
 */
class CoreModelTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ── Content ────────────────────────────────────────────────

    @Nested
    class ContentTest {

        @Test
        void textContent() {
            var tc = new Content.TextContent("hello");
            assertEquals("hello", tc.text());
        }

        @Test
        void textContentNullDefaultsToEmpty() {
            var tc = new Content.TextContent(null);
            assertEquals("", tc.text());
        }

        @Test
        void imageContent() {
            var ic = new Content.ImageContent("base64data", "image/jpeg");
            assertEquals("base64data", ic.data());
            assertEquals("image/jpeg", ic.mimeType());
        }

        @Test
        void imageContentDefaultMime() {
            var ic = new Content.ImageContent("data", null);
            assertEquals("image/png", ic.mimeType());
        }

        @Test
        void toolCallContent() {
            var tc = new Content.ToolCallContent("call_1", "bash", Map.of("cmd", "ls"));
            assertEquals("call_1", tc.id());
            assertEquals("bash", tc.name());
            assertEquals(Map.of("cmd", "ls"), tc.arguments());
        }

        @Test
        void toolCallContentNullArgs() {
            var tc = new Content.ToolCallContent("id", "name", null);
            assertEquals(Map.of(), tc.arguments());
        }

        @Test
        void toolCallContentArgumentsAreImmutable() {
            var tc = new Content.ToolCallContent("id", "name", Map.of("k", "v"));
            assertThrows(UnsupportedOperationException.class, () -> tc.arguments().put("x", "y"));
        }

        @Test
        void sealedInterfacePatternMatch() {
            Content c = new Content.TextContent("test");
            String result = switch (c) {
                case Content.TextContent tc -> "text:" + tc.text();
                case Content.ImageContent ic -> "image:" + ic.data();
                case Content.ToolCallContent tcc -> "tool:" + tcc.name();
            };
            assertEquals("text:test", result);
        }
    }

    // ── Message ────────────────────────────────────────────────

    @Nested
    class MessageTest {

        @Test
        void userMessage() {
            var msg = new Message.UserMessage(
                List.of(new Content.TextContent("hi")), 1000.0);
            assertEquals("user", msg.getClass().getSimpleName().equals("UserMessage") ? "user" : "other");
            assertEquals(1, msg.content().size());
            assertEquals(1000.0, msg.timestamp());
        }

        @Test
        void assistantMessageBuilder() {
            var msg = Message.AssistantMessage.builder()
                .addContent(new Content.TextContent("Hello!"))
                .addContent(new Content.ToolCallContent("tc1", "bash", Map.of()))
                .usage(new Message.Usage(100, 50, 0, 0))
                .stopReason(Message.StopReason.TOOL_USE)
                .provider("openai")
                .model("gpt-4o")
                .build();

            assertTrue(msg.hasToolCalls());
            assertEquals(1, msg.toolCalls().size());
            assertEquals("tc1", msg.toolCalls().get(0).id());
            assertEquals("Hello!", msg.text());
            assertEquals(150, msg.usage().totalTokens());
        }

        @Test
        void assistantMessageNoToolCalls() {
            var msg = Message.AssistantMessage.builder()
                .addContent(new Content.TextContent("Just text"))
                .build();
            assertFalse(msg.hasToolCalls());
            assertEquals(0, msg.toolCalls().size());
        }

        @Test
        void toolResultMessage() {
            var msg = new Message.ToolResultMessage(
                "call_1", "bash",
                List.of(new Content.TextContent("output")),
                false, 2000.0);
            assertEquals("call_1", msg.toolCallId());
            assertEquals("bash", msg.toolName());
            assertFalse(msg.isError());
        }

        @Test
        void customMessage() {
            var msg = Message.CustomMessage.compactionSummary("Summary of conversation...");
            assertEquals("compaction_summary", msg.customType());
            assertEquals("Summary of conversation...", msg.content().asText());
        }

        @Test
        void stopReasonFromValue() {
            assertEquals(Message.StopReason.STOP, Message.StopReason.fromValue("stop"));
            assertEquals(Message.StopReason.STOP, Message.StopReason.fromValue("end_turn"));
            assertEquals(Message.StopReason.TOOL_USE, Message.StopReason.fromValue("tool_use"));
            assertEquals(Message.StopReason.TOOL_USE, Message.StopReason.fromValue("tool_calls"));
            assertEquals(Message.StopReason.LENGTH, Message.StopReason.fromValue("length"));
            assertEquals(Message.StopReason.STOP, Message.StopReason.fromValue("unknown"));
            assertEquals(Message.StopReason.STOP, Message.StopReason.fromValue(null));
        }

        @Test
        void usageTotalTokens() {
            var u = new Message.Usage(100, 50, 20, 10);
            assertEquals(150, u.totalTokens()); // input + output only
            assertEquals(180, u.totalTokensWithCache()); // includes cache
        }

        @Test
        void usageDefault() {
            var u = new Message.Usage();
            assertEquals(0, u.totalTokens());
        }
    }

    // ── AgentEvent ─────────────────────────────────────────────

    @Nested
    class AgentEventTest {

        @Test
        void agentStart() {
            AgentEvent evt = new AgentEvent.AgentStart();
            assertInstanceOf(AgentEvent.AgentStart.class, evt);
        }

        @Test
        void agentEnd() {
            var msgs = List.<Message>of(new Message.UserMessage(List.of(), 0));
            var evt = new AgentEvent.AgentEnd(msgs);
            assertEquals(1, evt.messages().size());
        }

        @Test
        void turnEnd() {
            var msg = Message.AssistantMessage.builder().build();
            var evt = new AgentEvent.TurnEnd(msg, List.of());
            assertNotNull(evt.toolResults());
            assertTrue(evt.toolResults().isEmpty());
        }

        @Test
        void messageUpdate() {
            var msg = Message.AssistantMessage.builder().build();
            var delta = new AgentEvent.MessageDelta.TextDelta("hello");
            var evt = new AgentEvent.MessageUpdate(msg, delta);
            assertInstanceOf(AgentEvent.MessageDelta.TextDelta.class, evt.delta());
        }

        @Test
        void toolExecutionStart() {
            var evt = new AgentEvent.ToolExecutionStart("tc1", "bash", Map.of("cmd", "ls"));
            assertEquals("tc1", evt.toolCallId());
            assertEquals("bash", evt.toolName());
        }

        @Test
        void sealedEventPatternMatch() {
            AgentEvent evt = new AgentEvent.TurnStart();
            String result = switch (evt) {
                case AgentEvent.AgentStart e -> "start";
                case AgentEvent.AgentEnd e -> "end";
                case AgentEvent.TurnStart e -> "turn_start";
                case AgentEvent.TurnEnd e -> "turn_end";
                case AgentEvent.MessageStart e -> "msg_start";
                case AgentEvent.MessageUpdate e -> "msg_update";
                case AgentEvent.MessageEnd e -> "msg_end";
                case AgentEvent.ToolExecutionStart e -> "tool_start";
                case AgentEvent.ToolExecutionUpdate e -> "tool_update";
                case AgentEvent.ToolExecutionEnd e -> "tool_end";
                case AgentEvent.HumanInputRequired e -> "hitl";
            };
            assertEquals("turn_start", result);
        }
    }

    // ── StreamEvent ────────────────────────────────────────────

    @Nested
    class StreamEventTest {

        @Test
        void textDelta() {
            StreamEvent evt = new StreamEvent.StreamTextDelta("hello");
            assertInstanceOf(StreamEvent.StreamTextDelta.class, evt);
        }

        @Test
        void toolCallEndNullArgs() {
            var evt = new StreamEvent.StreamToolCallEnd("id", null);
            assertEquals(Map.of(), evt.arguments());
        }

        @Test
        void errorEvent() {
            var evt = new StreamEvent.StreamError("rate limit", true, false);
            assertTrue(evt.retryable());
            assertFalse(evt.overflow());
        }
    }

    // ── ModelInfo ──────────────────────────────────────────────

    @Nested
    class ModelInfoTest {

        @Test
        void simpleModel() {
            var m = new ModelInfo("openai", "gpt-4o", 128000, 4096);
            assertEquals("openai", m.provider());
            assertEquals("gpt-4o", m.id());
            assertFalse(m.supportsReasoning());
        }

        @Test
        void reasoningModel() {
            var m = new ModelInfo("anthropic", "claude-sonnet-4-6", 200000, 8192, true, true);
            assertTrue(m.supportsReasoning());
            assertTrue(m.supportsXhighThinking());
        }

        @Test
        void nullProviderThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> new ModelInfo(null, "id", 1, 1));
        }
    }

    // ── ProviderAuth + AuthSource ──────────────────────────────

    @Nested
    class AuthTest {

        @Test
        void providerAuthSimple() {
            var auth = new ProviderAuth("sk-test");
            assertEquals("sk-test", auth.apiKey());
            assertNull(auth.extraHeaders());
        }

        @Test
        void providerAuthBlankThrows() {
            assertThrows(IllegalArgumentException.class, () -> new ProviderAuth(""));
            assertThrows(IllegalArgumentException.class, () -> new ProviderAuth("  "));
        }

        @Test
        void authSourceStatic() {
            var source = AuthSource.staticAuth("my-key");
            var auth = source.resolve("openai");
            assertEquals("my-key", auth.apiKey());
        }

        @Test
        void authSourceDynamic() {
            var source = AuthSource.dynamic(provider ->
                new ProviderAuth("key-for-" + provider));
            assertEquals("key-for-anthropic", source.resolve("anthropic").apiKey());
        }

        @Test
        void authSourceEnvMissingThrows() {
            var source = AuthSource.env("NONEXISTENT_VAR_12345");
            assertThrows(AuthSource.MissingCredentialsError.class, () -> source.resolve("test"));
        }
    }

    // ── PendingMessageQueue ────────────────────────────────────

    @Nested
    class PendingMessageQueueTest {

        @Test
        void emptyDrain() {
            var q = new PendingMessageQueue();
            assertTrue(q.drain().isEmpty());
            assertFalse(q.hasItems());
        }

        @Test
        void oneAtATimeMode() {
            var q = new PendingMessageQueue(PendingMessageQueue.DrainMode.ONE_AT_A_TIME);
            q.enqueue(new Message.UserMessage(List.of(), 1.0));
            q.enqueue(new Message.UserMessage(List.of(), 2.0));
            q.enqueue(new Message.UserMessage(List.of(), 3.0));

            assertEquals(1, q.drain().size());
            assertEquals(1, q.drain().size());
            assertEquals(1, q.drain().size());
            assertTrue(q.drain().isEmpty());
        }

        @Test
        void allMode() {
            var q = new PendingMessageQueue(PendingMessageQueue.DrainMode.ALL);
            q.enqueue(new Message.UserMessage(List.of(), 1.0));
            q.enqueue(new Message.UserMessage(List.of(), 2.0));

            var drained = q.drain();
            assertEquals(2, drained.size());
            assertTrue(q.drain().isEmpty());
        }

        @Test
        void clear() {
            var q = new PendingMessageQueue();
            q.enqueue(new Message.UserMessage(List.of(), 1.0));
            q.clear();
            assertFalse(q.hasItems());
        }
    }

    // ── HumanInputGate ─────────────────────────────────────────

    @Nested
    class HumanInputGateTest {

        @Test
        void requireAndProvide() throws Exception {
            var gate = new HumanInputGate();
            var future = gate.requireInput("tc1");
            assertTrue(gate.isWaiting("tc1"));

            assertTrue(gate.provideInput("tc1", Map.of("value", "yes")));
            var result = future.get(1, TimeUnit.SECONDS);
            assertEquals("yes", result.get("value"));
            assertFalse(gate.isWaiting("tc1"));
        }

        @Test
        void provideNonexistentReturnsFalse() {
            var gate = new HumanInputGate();
            assertFalse(gate.provideInput("nonexistent", Map.of()));
        }

        @Test
        void cancelAll() {
            var gate = new HumanInputGate();
            var f1 = gate.requireInput("tc1");
            var f2 = gate.requireInput("tc2");

            gate.cancelAll();

            assertTrue(f1.isCancelled());
            assertTrue(f2.isCancelled());
            assertFalse(gate.isWaiting("tc1"));
        }

        @Test
        void requiresHumanInputException() {
            var ex = new HumanInputGate.RequiresHumanInput(
                "Enter your name", Map.of("type", "string"));
            assertEquals("Enter your name", ex.prompt());
            assertEquals("string", ex.inputSchema().get("type"));
        }
    }

    // ── AgentContext ───────────────────────────────────────────

    @Nested
    class AgentContextTest {

        @Test
        void defaultContext() {
            var ctx = new AgentContext();
            assertEquals("", ctx.systemPrompt());
            assertTrue(ctx.messages().isEmpty());
            assertFalse(ctx.isStreaming());
            assertNull(ctx.errorMessage());
        }

        @Test
        void mutableSystemPrompt() {
            var ctx = new AgentContext("initial", List.of());
            ctx.setSystemPrompt("updated");
            assertEquals("updated", ctx.systemPrompt());
        }

        @Test
        void streamingState() {
            var ctx = new AgentContext();
            assertTrue(ctx.tryStartStreaming());
            assertTrue(ctx.isStreaming());
            assertFalse(ctx.tryStartStreaming());
            ctx.stopStreaming();
            assertFalse(ctx.isStreaming());
        }

        @Test
        void errorMessage() {
            var ctx = new AgentContext();
            assertNull(ctx.errorMessage());
            ctx.setErrorMessage("err");
            assertEquals("err", ctx.errorMessage());
        }
    }

    // ── AgentLoopConfig ────────────────────────────────────────

    @Nested
    class AgentLoopConfigTest {

        @Test
        void builderRequiresModel() {
            assertThrows(IllegalStateException.class, () ->
                AgentLoopConfig.builder().build());
        }

        @Test
        void builderRequiresStreamFn() {
            var model = new ModelInfo("test", "test-model", 1000, 100);
            assertThrows(IllegalStateException.class, () ->
                AgentLoopConfig.builder().model(model).build());
        }

        @Test
        void builderFullConfig() {
            var model = new ModelInfo("test", "test-model", 1000, 100);
            var config = AgentLoopConfig.builder()
                .model(model)
                .streamFn((m, msgs, tools, sp, tl, temp, mt, sig, auth) -> List.<StreamEvent>of().iterator())
                .convertToLlm(msgs -> List.of())
                .authResolver(name -> new ProviderAuth("test-key"))
                .thinkingLevel("high")
                .maxRetries(5)
                .toolTimeout(60.0)
                .build();

            assertEquals(model, config.model());
            assertEquals("high", config.thinkingLevel());
            // Sub-config getters (preferred)
            assertEquals(5, config.retryConfig().maxRetries());
            assertEquals(60.0, config.toolConfig().timeout());
            // Deprecated convenience getters still work
            assertEquals(5, config.maxRetries());
            assertEquals(60.0, config.toolTimeout());
        }

        @Test
        void toBuilder_roundTrip_preservesAllFields() {
            var model1 = new ModelInfo("test", "model-1", 1000, 100);
            var model2 = new ModelInfo("test", "model-2", 2000, 200);

            AgentLoopConfig.StreamFunction streamFn =
                    (m, msgs, tools, sp, tl, temp, mt, sig, auth) -> List.<StreamEvent>of().iterator();
            AgentLoopConfig.ConvertToLlm convertFn = msgs -> List.of();
            AgentLoopConfig.AuthResolver authFn = name -> new ProviderAuth("key");

            var original = AgentLoopConfig.builder()
                    .model(model1)
                    .streamFn(streamFn)
                    .convertToLlm(convertFn)
                    .authResolver(authFn)
                    .thinkingLevel("medium")
                    .temperature(0.7)
                    .maxTokens(512)
                    .maxTurns(10)
                    .maxRetries(2)
                    .retryBaseDelay(0.5)
                    .retryMaxDelay(30.0)
                    .toolTimeout(90.0)
                    .toolResultMaxChars(8000)
                    .toolExecution(AgentLoopConfig.ToolExecutionMode.SEQUENTIAL)
                    .build();

            // Round-trip through toBuilder
            var rebuilt = original.toBuilder().build();

            // Verify all scalar/config fields survive the round-trip
            assertEquals(original.model(), rebuilt.model());
            assertEquals(original.thinkingLevel(), rebuilt.thinkingLevel());
            assertEquals(original.temperature(), rebuilt.temperature());
            assertEquals(original.maxTokens(), rebuilt.maxTokens());
            assertEquals(original.maxTurns(), rebuilt.maxTurns());
            assertEquals(original.retryConfig(), rebuilt.retryConfig());
            assertEquals(original.toolConfig(), rebuilt.toolConfig());

            // Functional references preserved (same instance)
            assertSame(original.streamFn(), rebuilt.streamFn());
            assertSame(original.convertToLlm(), rebuilt.convertToLlm());
            assertSame(original.authResolver(), rebuilt.authResolver());

            // Verify mutation via toBuilder works
            var mutated = original.toBuilder().model(model2).temperature(0.9).build();
            assertEquals(model2, mutated.model());
            assertEquals(0.9, mutated.temperature());
            // Unchanged fields preserved
            assertEquals(original.thinkingLevel(), mutated.thinkingLevel());
            assertEquals(original.retryConfig(), mutated.retryConfig());
        }
    }
}
