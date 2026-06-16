package io.agentcore.prompts;

import java.util.List;

public record SystemPrompt(
        String text,
        List<SystemPromptSection> sections,
        int toolCount,
        int skillCount,
        int contextFileCount
) {
    public record SystemPromptSection(String name, String content, String source) {}
}
