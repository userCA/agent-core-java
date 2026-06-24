package io.agentcore.llm;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class LlmMessageUtilsTest {

    @Test
    void latestUserIndexFindsLastUser() {
        List<Map<String, Object>> msgs = List.of(
                Map.of("role", "system", "content", "You are helpful"),
                Map.of("role", "user", "content", "Hello"),
                Map.of("role", "assistant", "content", "Hi"),
                Map.of("role", "user", "content", "How are you?"),
                Map.of("role", "assistant", "content", "Fine")
        );
        assertEquals(3, LlmMessageUtils.latestUserIndex(msgs));
    }

    @Test
    void latestUserIndexNoUserReturnsSize() {
        List<Map<String, Object>> msgs = List.of(
                Map.of("role", "system", "content", "Hi"),
                Map.of("role", "assistant", "content", "Hello")
        );
        assertEquals(2, LlmMessageUtils.latestUserIndex(msgs));
    }

    @Test
    void latestUserTextWithStringContent() {
        List<Map<String, Object>> msgs = List.of(
                Map.of("role", "user", "content", "Hello world")
        );
        assertEquals("Hello world", LlmMessageUtils.latestUserText(msgs));
    }

    @Test
    void latestUserTextWithListContent() {
        List<Map<String, Object>> content = List.of(
                Map.of("type", "text", "text", "Part 1"),
                Map.of("type", "text", "text", "Part 2")
        );
        List<Map<String, Object>> msgs = List.of(
                Map.of("role", "user", "content", content)
        );
        assertEquals("Part 1\nPart 2", LlmMessageUtils.latestUserText(msgs));
    }

    @Test
    void latestUserTextNoUserReturnsNull() {
        List<Map<String, Object>> msgs = List.of(
                Map.of("role", "assistant", "content", "Hi")
        );
        assertNull(LlmMessageUtils.latestUserText(msgs));
    }

    @Test
    void injectSystemMessageAtLatestUser() {
        List<Map<String, Object>> msgs = new ArrayList<>(List.of(
                Map.of("role", "system", "content", "Original"),
                Map.of("role", "user", "content", "Hello"),
                Map.of("role", "assistant", "content", "Hi"),
                Map.of("role", "user", "content", "Question")
        ));

        List<Map<String, Object>> result = LlmMessageUtils.injectSystemMessageAtLatestUser(msgs, "Memory info");

        assertEquals(5, result.size());
        // Inserted BEFORE index 3 (latest user), so at index 3
        assertEquals("system", result.get(3).get("role"));
        assertEquals("Memory info", result.get(3).get("content"));
        assertEquals("user", result.get(4).get("role"));
        assertEquals("Question", result.get(4).get("content"));
    }

    @Test
    void injectSystemMessageWhenNoUserMessage() {
        List<Map<String, Object>> msgs = new ArrayList<>(List.of(
                Map.of("role", "system", "content", "System")
        ));

        List<Map<String, Object>> result = LlmMessageUtils.injectSystemMessageAtLatestUser(msgs, "Extra");

        assertEquals(2, result.size());
        // No user message, so injected at position = size (appended at end)
        assertEquals("System", result.get(0).get("content"));
        assertEquals("Extra", result.get(1).get("content"));
    }
}
