package io.agentcore.prompts;

import java.util.Collections;
import java.util.List;

/**
 * A named section of the system prompt.
 *
 * @param name    section identifier (e.g. "base", "tools", "skills")
 * @param content rendered text content
 * @param source  optional source path or identifier
 */
public record SystemPromptSection(String name, String content, String source) {

    public SystemPromptSection {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }

    /** Create a section with no source. */
    public SystemPromptSection(String name, String content) {
        this(name, content, null);
    }
}
