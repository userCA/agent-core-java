package io.agentcore.skills;

/**
 * Represents a loaded skill (markdown file with frontmatter).
 *
 * <p>Mirrors Python {@code agent_core/skills/__init__.py} Skill dataclass.
 *
 * @param name                  skill name (validated against directory name)
 * @param description           human-readable description from frontmatter
 * @param filePath              absolute path to the SKILL.md or .md file
 * @param baseDir               parent directory of the skill file
 * @param disableModelInvocation if true, skill is hidden from LLM prompt
 */
public record Skill(
        String name,
        String description,
        String filePath,
        String baseDir,
        boolean disableModelInvocation
) {
    public Skill {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name required");
        if (description == null) description = "";
        if (filePath == null) filePath = "";
        if (baseDir == null) baseDir = "";
    }
}
