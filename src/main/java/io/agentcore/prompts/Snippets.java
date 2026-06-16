package io.agentcore.prompts;

import io.agentcore.tools.base.ToolDefinition;

public final class Snippets {
    private Snippets() {}

    public static String extractSnippet(ToolDefinition definition) {
        if (definition.promptSnippet() != null && !definition.promptSnippet().isEmpty()) {
            return definition.promptSnippet();
        }
        String desc = definition.description();
        if (desc != null && !desc.isEmpty()) {
            int dot = desc.indexOf('.');
            String first = dot > 0 ? desc.substring(0, dot + 1) : desc;
            return first.length() > 80 ? first.substring(0, 77) + "..." : first;
        }
        return definition.name();
    }
}
