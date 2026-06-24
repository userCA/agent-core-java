package io.agentcore.prompt;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SystemPromptTest {

    @Test
    void createsPromptWithDefaults() {
        SystemPromptSection s1 = new SystemPromptSection("base", "hello");
        SystemPrompt p = new SystemPrompt("hello", List.of(s1), 0, 0, 0);

        assertEquals("hello", p.text());
        assertEquals(1, p.sections().size());
        assertEquals(0, p.toolCount());
        assertEquals(0, p.skillCount());
        assertEquals(0, p.contextFileCount());
    }

    @Test
    void sectionsAreCopied() {
        SystemPromptSection s1 = new SystemPromptSection("base", "hello");
        SystemPrompt p = new SystemPrompt("hello", List.of(s1), 1, 2, 3);

        assertEquals(1, p.toolCount());
        assertEquals(2, p.skillCount());
        assertEquals(3, p.contextFileCount());
    }

    @Test
    void nullSectionsBecomeEmptyList() {
        SystemPrompt p = new SystemPrompt("text", null, 0, 0, 0);
        assertTrue(p.sections().isEmpty());
    }
}
