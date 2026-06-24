package io.agentcore.prompt;

import java.util.List;

/**
 * Fully assembled system prompt with metadata.
 *
 * @param text                the complete prompt text
 * @param sections            ordered list of prompt sections
 * @param toolCount           number of active tools
 * @param skillCount          number of active skills
 * @param contextFileCount    number of context files included
 */
public record SystemPrompt(
        String text,
        List<SystemPromptSection> sections,
        int toolCount,
        int skillCount,
        int contextFileCount
) {
    public SystemPrompt {
        sections = sections != null ? List.copyOf(sections) : List.of();
    }
}
