package io.agentcore.compaction;

import com.fasterxml.jackson.databind.node.TextNode;

import io.agentcore.core.Content;
import io.agentcore.core.Message;
import io.agentcore.core.Message.*;

import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Compactor, LLMSummaryCompactor, and CompactionResult.
 */
class CompactionTest {

    // ── CompactionResult ─────────────────────────────────────

    @Nested
    @DisplayName("CompactionResult")
    class ResultTests {

        @Test
        void emptyResult() {
            var r = CompactionResult.empty();
            assertEquals("", r.summary());
            assertEquals("", r.firstKeptEntryId());
            assertEquals(0, r.tokensBefore());
            assertEquals(0, r.tokensAfter());
            assertEquals(0, r.keptCount());
        }

        @Test
        void nullSummaryDefaultsToEmpty() {
            var r = new CompactionResult(null, null, 10, 5, 2);
            assertEquals("", r.summary());
            assertEquals("", r.firstKeptEntryId());
        }

        @Test
        void normalResult() {
            var r = new CompactionResult("summary text", "msg-1", 100, 30, 4);
            assertEquals("summary text", r.summary());
            assertEquals("msg-1", r.firstKeptEntryId());
            assertEquals(100, r.tokensBefore());
            assertEquals(30, r.tokensAfter());
            assertEquals(4, r.keptCount());
        }
    }

    // ── Token estimation ─────────────────────────────────────

    @Nested
    @DisplayName("Token Estimation")
    class TokenTests {

        @Test
        void estimateUserMessageTokens() {
            var msg = new UserMessage(
                    List.of(new Content.TextContent("Hello world, this is a test message")),
                    System.currentTimeMillis() / 1000.0);
            int tokens = Compactor.estimateTokens(msg);
            // ~38 chars / 4 + 20 = ~29
            assertTrue(tokens > 0);
            assertTrue(tokens >= 20); // minimum overhead
        }

        @Test
        void estimateAssistantMessageTokens() {
            var msg = new AssistantMessage(
                    List.of(new Content.TextContent("I am the assistant response")),
                    new Usage(10, 20, 0, 0),
                    StopReason.STOP, null, false, false,
                    "anthropic", "claude-3", System.currentTimeMillis() / 1000.0);
            int tokens = Compactor.estimateTokens(msg);
            assertTrue(tokens > 0);
        }

        @Test
        void estimateToolResultTokens() {
            var msg = new ToolResultMessage(
                    "tc-1", "bash",
                    List.of(new Content.TextContent("command output here")),
                    false, System.currentTimeMillis() / 1000.0);
            int tokens = Compactor.estimateTokens(msg);
            assertTrue(tokens > 0);
        }

        @Test
        void estimateCustomMessageTokens() {
            var msg = new CustomMessage("system", new TextNode("custom content data"), null, null,
                    System.currentTimeMillis() / 1000.0);
            int tokens = Compactor.estimateTokens(msg);
            assertTrue(tokens > 0);
        }

        @Test
        void emptyMessageMinimumOverhead() {
            var msg = new UserMessage(List.of(), System.currentTimeMillis() / 1000.0);
            int tokens = Compactor.estimateTokens(msg);
            assertEquals(20, tokens); // 0 chars / 4 + 20 = 20, max(1, 20) = 20
        }

        @Test
        void totalTokensSumsAll() {
            List<Message> messages = List.of(
                    new UserMessage(List.of(new Content.TextContent("Hello")), 0),
                    new AssistantMessage(
                            List.of(new Content.TextContent("Hi there")),
                            new Usage(), StopReason.STOP, null, false, false,
                            "test", "test", 0)
            );
            int total = Compactor.totalTokens(messages);
            assertTrue(total > 0);
            assertEquals(
                    Compactor.estimateTokens(messages.get(0)) + Compactor.estimateTokens(messages.get(1)),
                    total);
        }
    }

    // ── LLMSummaryCompactor ──────────────────────────────────

    @Nested
    @DisplayName("LLMSummaryCompactor")
    class CompactorTests {

        private List<Message> buildMessages(int count) {
            List<Message> messages = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String text = "Message " + i + " with some content to estimate tokens";
                if (i % 2 == 0) {
                    messages.add(new UserMessage(
                            List.of(new Content.TextContent(text)),
                            System.currentTimeMillis() / 1000.0));
                } else {
                    messages.add(new AssistantMessage(
                            List.of(new Content.TextContent(text)),
                            new Usage(), StopReason.STOP, null, false, false,
                            "test", "test", System.currentTimeMillis() / 1000.0));
                }
            }
            return messages;
        }

        @Test
        void shouldCompactBelowThreshold() {
            var compactor = new LLMSummaryCompactor(null, 0.8, 4);
            var messages = buildMessages(2); // ~60 tokens
            assertFalse(compactor.shouldCompact(messages, 100000));
        }

        @Test
        void shouldCompactAboveThreshold() {
            var compactor = new LLMSummaryCompactor(null, 0.8, 4);
            var messages = buildMessages(20); // ~640 tokens
            assertTrue(compactor.shouldCompact(messages, 700)); // 640 >= 700*0.8=560
        }

        @Test
        void compactEmptyMessages() {
            var compactor = new LLMSummaryCompactor();
            var result = compactor.compact(List.of(), "threshold", null, null);
            assertEquals(0, result.tokensBefore());
            assertEquals(0, result.tokensAfter());
            assertEquals(0, result.keptCount());
        }

        @Test
        void compactNullMessages() {
            var compactor = new LLMSummaryCompactor();
            var result = compactor.compact(null, "threshold", null, null);
            assertEquals(CompactionResult.empty().tokensBefore(), result.tokensBefore());
        }

        @Test
        void compactKeepsRecentMessages() {
            var compactor = new LLMSummaryCompactor(null, 0.8, 4);
            var messages = buildMessages(10);
            var result = compactor.compact(messages, "threshold", null, null);

            assertEquals(4, result.keptCount());
            assertTrue(result.tokensBefore() > result.tokensAfter()
                    || result.tokensBefore() == result.tokensAfter()); // summary may add tokens
        }

        @Test
        void compactWithSummarizeFn() {
            var compactor = new LLMSummaryCompactor(
                    msgs -> "Summarized " + msgs.size() + " messages",
                    0.8, 3);
            var messages = buildMessages(8);
            var result = compactor.compact(messages, "threshold", null, null);

            assertEquals("Summarized 5 messages", result.summary());
            assertEquals(3, result.keptCount());
        }

        @Test
        void compactWithAbortSignal() {
            var compactor = new LLMSummaryCompactor(
                    msgs -> "should not be called",
                    0.8, 3);
            var messages = buildMessages(8);
            var signal = new AtomicBoolean(true); // aborted
            var result = compactor.compact(messages, "threshold", null, signal);

            assertEquals("[aborted]", result.summary());
            assertEquals(3, result.keptCount());
        }

        @Test
        void compactNoSummarizeFn() {
            var compactor = new LLMSummaryCompactor(null, 0.8, 4);
            var messages = buildMessages(10);
            var result = compactor.compact(messages, "threshold", null, null);

            assertEquals("", result.summary());
            assertEquals(4, result.keptCount());
        }

        @Test
        void compactAllKeptNoCompact() {
            var compactor = new LLMSummaryCompactor(null, 0.8, 100);
            var messages = buildMessages(5); // fewer than keepRecent
            var result = compactor.compact(messages, "threshold", null, null);

            assertEquals("", result.summary());
            assertEquals(5, result.keptCount());
        }

        @Test
        void defaultConstructor() {
            var compactor = new LLMSummaryCompactor();
            var messages = buildMessages(2);
            assertFalse(compactor.shouldCompact(messages, 100000));
        }
    }
}
