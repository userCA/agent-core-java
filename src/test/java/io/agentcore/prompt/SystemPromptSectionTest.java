package io.agentcore.prompt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SystemPromptSectionTest {

    @Test
    void createsSectionWithSource() {
        SystemPromptSection s = new SystemPromptSection("base", "Hello", "file.md");
        assertEquals("base", s.name());
        assertEquals("Hello", s.content());
        assertEquals("file.md", s.source());
    }

    @Test
    void createsSectionWithoutSource() {
        SystemPromptSection s = new SystemPromptSection("tools", "List");
        assertEquals("tools", s.name());
        assertEquals("List", s.content());
        assertNull(s.source());
    }

    @Test
    void rejectsBlankName() {
        assertThrows(IllegalArgumentException.class, () ->
                new SystemPromptSection("", "content"));
    }

    @Test
    void rejectsNullName() {
        assertThrows(IllegalArgumentException.class, () ->
                new SystemPromptSection(null, "content"));
    }
}
