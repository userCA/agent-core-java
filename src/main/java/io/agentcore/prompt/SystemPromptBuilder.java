package io.agentcore.prompt;

import io.agentcore.resources.ContextFileLoader.ContextFile;
import io.agentcore.skill.Skill;
import io.agentcore.skill.SkillLoader;
import io.agentcore.tools.ToolDefinition;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;

/**
 * Dynamic system prompt builder — assembles a system prompt from tools,
 * skills, context files, and metadata.
 *
 * <p>Mirrors Python {@code agent_core/prompts/builder.py} SystemPromptBuilder.
 */
public final class SystemPromptBuilder {

    private static final String DEFAULT_BASE_PROMPT =
            "You are a helpful assistant with access to tools. "
            + "When you need to perform actions on the user's system, use the available tools. "
            + "Always prefer using tools over guessing when file or system information is needed.";

    /**
     * Guideline rule: given a set of active tool names, optionally produce a guideline string.
     */
    @FunctionalInterface
    public interface GuidelineRule extends Function<Set<String>, Optional<String>> {}

    private static final int MAX_SNIPPET_LENGTH = 80;
    private static final int TRUNCATION_SUFFIX_LENGTH = 77; // MAX_SNIPPET_LENGTH - 3 for "..."

    private static final List<GuidelineRule> DEFAULT_RULES = List.of(
            tools -> tools.containsAll(Set.of("grep", "find", "ls"))
                    ? Optional.of("Prefer grep/find/ls over bash when searching files.")
                    : Optional.empty(),
            tools -> tools.contains("edit")
                    ? Optional.of("Always use 'edit' for small changes instead of rewriting entire files.")
                    : Optional.empty(),
            tools -> tools.contains("read")
                    ? Optional.of("When using 'read', the output is automatically truncated if too large.")
                    : Optional.empty(),
            tools -> (tools.contains("write") || tools.contains("edit"))
                    ? Optional.of("Before creating files, check if they already exist with 'ls'.")
                    : Optional.empty()
    );

    private final String basePrompt;

    public SystemPromptBuilder() {
        this(null);
    }

    public SystemPromptBuilder(String basePrompt) {
        this.basePrompt = basePrompt;
    }

    /**
     * Build the system prompt from the given components.
     *
     * @param cwd          current working directory (nullable)
     * @param activeTools  tools available to the agent (nullable)
     * @param skills       skills available (nullable)
     * @param contextFiles context files loaded from disk (nullable)
     * @param date         current date (null = today)
     * @return fully assembled SystemPrompt
     */
    public SystemPrompt build(String cwd,
                              List<ToolDefinition> activeTools,
                              List<Skill> skills,
                              List<ContextFile> contextFiles,
                              LocalDate date) {
        List<SystemPromptSection> sections = new ArrayList<>();
        List<ToolDefinition> tools = activeTools != null ? activeTools : List.of();
        List<Skill> skillList = skills != null ? skills : List.of();
        List<ContextFile> ctxFiles = contextFiles != null ? contextFiles : List.of();

        // 1. Base prompt
        String base = basePrompt != null ? basePrompt : DEFAULT_BASE_PROMPT;
        sections.add(new SystemPromptSection("base", base));

        // 2. Tools
        StringBuilder toolSection = new StringBuilder();
        if (!tools.isEmpty()) {
            toolSection.append("\n## Tools\n\nYou have access to the following tools:\n\n");
            List<ToolDefinition> sorted = new ArrayList<>(tools);
            sorted.sort(Comparator.comparing(ToolDefinition::name));
            for (ToolDefinition tool : sorted) {
                toolSection.append("- ").append(tool.name()).append(": ")
                           .append(extractSnippet(tool)).append("\n");
            }
        }
        sections.add(new SystemPromptSection("tools", toolSection.toString()));

        // 3. Guidelines
        List<String> guidelines = generateGuidelines(tools, DEFAULT_RULES);
        if (!guidelines.isEmpty()) {
            StringBuilder gs = new StringBuilder("\n## Guidelines\n\n");
            for (String g : guidelines) {
                gs.append(g).append("\n");
            }
            sections.add(new SystemPromptSection("guidelines", gs.toString()));
        }

        // 4. Tool guidelines
        List<String> toolGuidelines = new ArrayList<>();
        toolGuidelines.add("Distinguish intermediate results from final outputs:");
        toolGuidelines.add("- Final outputs (images, files, content user needs) -> include verbatim in response");
        toolGuidelines.add("- Intermediate steps (file reads, status checks, commands) -> use results to continue answering");
        for (ToolDefinition tool : tools) {
            if (tool.promptGuidelines() != null) {
                for (String g : tool.promptGuidelines()) {
                    toolGuidelines.add("- " + g);
                }
            }
        }
        StringBuilder tgSection = new StringBuilder("\n## Tool Guidelines\n\n");
        for (String tg : toolGuidelines) {
            tgSection.append(tg).append("\n");
        }
        sections.add(new SystemPromptSection("tool_guidelines", tgSection.toString()));

        // 5. Context files
        if (!ctxFiles.isEmpty()) {
            StringBuilder cf = new StringBuilder("\n## Project Context\n\n");
            for (ContextFile file : ctxFiles) {
                cf.append("### ").append(file.path()).append("\n\n");
                cf.append(file.content()).append("\n\n");
            }
            sections.add(new SystemPromptSection("context_files", cf.toString()));
        }

        // 6. Skills — delegate to SkillLoader for consistent XML format
        if (!skillList.isEmpty()) {
            String skillSection = SkillLoader.formatSkillsForPrompt(skillList);
            if (!skillSection.isEmpty()) {
                sections.add(new SystemPromptSection("skills", skillSection));
            }
        }

        // 7. Meta
        LocalDate now = date != null ? date : LocalDate.now(ZoneOffset.UTC);
        StringBuilder meta = new StringBuilder("\n## Meta\n\n");
        meta.append("Current date: ").append(now.format(DateTimeFormatter.ISO_LOCAL_DATE)).append("\n");
        if (cwd != null) {
            meta.append("Current working directory: ").append(cwd).append("\n");
        }
        sections.add(new SystemPromptSection("meta", meta.toString()));

        // Assemble
        StringBuilder fullText = new StringBuilder();
        for (SystemPromptSection s : sections) {
            fullText.append(s.content());
        }

        return new SystemPrompt(fullText.toString(), sections,
                tools.size(), skillList.size(), ctxFiles.size());
    }

    /** Convenience overload with no date override. */
    public SystemPrompt build(String cwd,
                              List<ToolDefinition> activeTools,
                              List<Skill> skills,
                              List<ContextFile> contextFiles) {
        return build(cwd, activeTools, skills, contextFiles, null);
    }

    // ── Helpers ────────────────────────────────────────────────

    /**
     * Extract a one-line description for a tool.
     * Priority: promptSnippet → first sentence of description → tool name.
     */
    static String extractSnippet(ToolDefinition def) {
        if (def.promptSnippet() != null && !def.promptSnippet().isBlank()) {
            return def.promptSnippet();
        }
        if (def.description() != null && !def.description().isBlank()) {
            String sentence = def.description().split("\\.")[0].trim();
            if (sentence.length() > MAX_SNIPPET_LENGTH) {
                sentence = sentence.substring(0, TRUNCATION_SUFFIX_LENGTH) + "...";
            }
            return sentence;
        }
        return def.name();
    }

    /**
     * Generate dynamic guidelines based on active tool names.
     */
    static List<String> generateGuidelines(List<ToolDefinition> tools, List<GuidelineRule> rules) {
        Set<String> toolNames = new HashSet<>();
        for (ToolDefinition t : tools) {
            toolNames.add(t.name());
        }
        List<String> result = new ArrayList<>();
        for (GuidelineRule rule : rules) {
            rule.apply(toolNames).ifPresent(result::add);
        }
        return result;
    }
}
