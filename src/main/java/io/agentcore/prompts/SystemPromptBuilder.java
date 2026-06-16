package io.agentcore.prompts;

import io.agentcore.resources.types.ContextFile;
import io.agentcore.resources.types.Skill;
import io.agentcore.tools.base.ToolDefinition;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dynamic system prompt builder — assembles from base prompt, tools, skills,
 * guidelines, context files, and meta information.
 */
public class SystemPromptBuilder {
    private final String basePrompt;

    public SystemPromptBuilder() {
        this("You are a helpful AI assistant.");
    }

    public SystemPromptBuilder(String basePrompt) {
        this.basePrompt = basePrompt;
    }

    public SystemPrompt build(String cwd, List<ToolDefinition> activeTools,
                               List<Skill> skills, List<ContextFile> contextFiles,
                               LocalDateTime date) {
        List<SystemPrompt.SystemPromptSection> sections = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        // 1. Base prompt
        sections.add(new SystemPrompt.SystemPromptSection("base", basePrompt, null));
        sb.append(basePrompt).append('\n');

        // 2. Tools
        if (activeTools != null && !activeTools.isEmpty()) {
            var sorted = activeTools.stream()
                    .sorted(Comparator.comparing(ToolDefinition::name))
                    .toList();
            StringBuilder toolsSb = new StringBuilder("## Tools\n");
            for (var tool : sorted) {
                toolsSb.append("- ").append(Snippets.extractSnippet(tool)).append('\n');
            }
            String toolsSection = toolsSb.toString();
            sections.add(new SystemPrompt.SystemPromptSection("tools", toolsSection, null));
            sb.append('\n').append(toolsSection);
        }

        // 3. Guidelines
        if (activeTools != null && !activeTools.isEmpty()) {
            List<String> guidelines = Guidelines.generateGuidelines(activeTools);
            if (!guidelines.isEmpty()) {
                String guidSection = "## Guidelines\n" + String.join("\n", guidelines);
                sections.add(new SystemPrompt.SystemPromptSection("guidelines", guidSection, null));
                sb.append('\n').append(guidSection);
            }
        }

        // 4. Context files
        if (contextFiles != null && !contextFiles.isEmpty()) {
            StringBuilder ctxSb = new StringBuilder("## Project Context\n");
            for (var cf : contextFiles) {
                ctxSb.append("### ").append(cf.path()).append("\n").append(cf.content()).append('\n');
            }
            String ctxSection = ctxSb.toString();
            sections.add(new SystemPrompt.SystemPromptSection("context_files", ctxSection, null));
            sb.append('\n').append(ctxSection);
        }

        // 5. Skills
        if (skills != null && !skills.isEmpty()) {
            var invocable = skills.stream().filter(s -> !s.disableModelInvocation()).toList();
            if (!invocable.isEmpty()) {
                StringBuilder skillSb = new StringBuilder("## Skills\n<available_skills>\n");
                for (var skill : invocable) {
                    skillSb.append("<skill>\n<name>").append(skill.name()).append("</name>\n");
                    skillSb.append("<description>").append(skill.description()).append("</description>\n");
                    skillSb.append("</skill>\n");
                }
                skillSb.append("</available_skills>");
                String skillSection = skillSb.toString();
                sections.add(new SystemPrompt.SystemPromptSection("skills", skillSection, null));
                sb.append('\n').append(skillSection);
            }
        }

        // 6. Meta
        StringBuilder metaSb = new StringBuilder("## Meta\n");
        if (date != null) metaSb.append("Current date: ").append(date.toLocalDate()).append('\n');
        if (cwd != null) metaSb.append("Working directory: ").append(cwd).append('\n');
        String metaSection = metaSb.toString();
        sections.add(new SystemPrompt.SystemPromptSection("meta", metaSection, null));
        sb.append('\n').append(metaSection);

        return new SystemPrompt(
                sb.toString(), sections,
                activeTools != null ? activeTools.size() : 0,
                skills != null ? skills.size() : 0,
                contextFiles != null ? contextFiles.size() : 0
        );
    }
}
