package io.agentcore.prompts;

import io.agentcore.tools.base.ToolDefinition;
import java.util.*;
import java.util.function.Function;

public final class Guidelines {
    private Guidelines() {}

    public static List<String> generateGuidelines(List<ToolDefinition> tools) {
        return generateGuidelines(tools, defaultRules());
    }

    public static List<String> generateGuidelines(List<ToolDefinition> tools,
                                                    List<Function<Set<String>, String>> rules) {
        Set<String> toolNames = new HashSet<>();
        tools.forEach(t -> toolNames.add(t.name()));

        List<String> result = new ArrayList<>();
        for (var rule : rules) {
            String guideline = rule.apply(toolNames);
            if (guideline != null) result.add(guideline);
        }
        return result;
    }

    private static List<Function<Set<String>, String>> defaultRules() {
        return List.of(
                names -> (names.containsAll(Set.of("grep", "find", "ls")))
                        ? "Prefer grep/find/ls over bash when searching files." : null,
                names -> names.contains("edit")
                        ? "Always use 'edit' for small changes instead of rewriting entire files." : null,
                names -> names.contains("read")
                        ? "When using 'read', the output is automatically truncated if too large." : null,
                names -> (names.contains("write") && names.contains("edit"))
                        ? "Before creating files, check if they already exist with 'ls'." : null
        );
    }
}
