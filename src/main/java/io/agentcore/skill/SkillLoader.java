package io.agentcore.skill;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Skill discovery, loading, validation, and formatting for system prompts.
 *
 * <p>Mirrors Python {@code agent_core/skills/__init__.py}.
 */
public final class SkillLoader {

    private static final Logger log = LoggerFactory.getLogger(SkillLoader.class);

    public static final int MAX_NAME_LENGTH = 64;
    public static final int MAX_DESCRIPTION_LENGTH = 1024;

    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9-]+$");
    private static final Pattern SENT_SPLIT = Pattern.compile("(?<=[。！？.!?\\n])\\s*");

    private SkillLoader() {}

    // ── Result ───────────────────────────────────────────────

    /**
     * Result of loading skills from one or more sources.
     */
    public record LoadSkillsResult(List<Skill> skills, List<Map<String, Object>> diagnostics) {
        public LoadSkillsResult {
            if (skills == null) skills = List.of();
            if (diagnostics == null) diagnostics = List.of();
        }
    }

    // ── Validation ───────────────────────────────────────────

    /**
     * Validate a skill name against the parent directory name.
     */
    public static List<String> validateName(String name, String parentDirName) {
        List<String> errors = new ArrayList<>();
        if (!name.equals(parentDirName)) {
            errors.add("name \"" + name + "\" does not match parent directory \"" + parentDirName + "\"");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            errors.add("name exceeds " + MAX_NAME_LENGTH + " characters (" + name.length() + ")");
        }
        if (!NAME_PATTERN.matcher(name).matches()) {
            errors.add("name contains invalid characters (must be lowercase a-z, 0-9, hyphens only)");
        }
        if (name.startsWith("-") || name.endsWith("-")) {
            errors.add("name must not start or end with a hyphen");
        }
        if (name.contains("--")) {
            errors.add("name must not contain consecutive hyphens");
        }
        return errors;
    }

    /**
     * Validate a skill description.
     */
    public static List<String> validateDescription(String description) {
        List<String> errors = new ArrayList<>();
        if (description == null || description.isBlank()) {
            errors.add("description is required");
        } else if (description.length() > MAX_DESCRIPTION_LENGTH) {
            errors.add("description exceeds " + MAX_DESCRIPTION_LENGTH + " characters (" + description.length() + ")");
        }
        return errors;
    }

    // ── Frontmatter parsing ──────────────────────────────────

    /**
     * Parse YAML-like frontmatter from markdown content.
     *
     * @return map with "frontmatter" (Map) and "body" (String)
     */
    public static Map<String, Object> parseFrontmatter(String content) {
        Map<String, Object> result = new HashMap<>();
        if (content == null || !content.startsWith("---")) {
            result.put("frontmatter", Map.of());
            result.put("body", content != null ? content : "");
            return result;
        }

        String[] parts = content.split("---", 3);
        if (parts.length < 3) {
            result.put("frontmatter", Map.of());
            result.put("body", content);
            return result;
        }

        String raw = parts[1].strip();
        String body = parts[2].strip();
        Map<String, Object> frontmatter = new LinkedHashMap<>();

        for (String line : raw.split("\\n")) {
            int colonIdx = line.indexOf(':');
            if (colonIdx > 0) {
                String key = line.substring(0, colonIdx).strip();
                String value = line.substring(colonIdx + 1).strip();
                if ("true".equalsIgnoreCase(value)) {
                    frontmatter.put(key, true);
                } else if ("false".equalsIgnoreCase(value)) {
                    frontmatter.put(key, false);
                } else {
                    frontmatter.put(key, value);
                }
            }
        }

        result.put("frontmatter", frontmatter);
        result.put("body", body);
        return result;
    }

    // ── Loading ──────────────────────────────────────────────

    /**
     * Load a single skill from a markdown file.
     */
    public static LoadResult loadSkillFromFile(Path filePath) {
        List<Map<String, Object>> diagnostics = new ArrayList<>();

        String rawContent;
        try {
            rawContent = Files.readString(filePath);
        } catch (IOException e) {
            diagnostics.add(diag("warning", e.getMessage(), filePath.toString()));
            return new LoadResult(null, diagnostics);
        }

        Map<String, Object> parsed = parseFrontmatter(rawContent);
        @SuppressWarnings("unchecked")
        Map<String, Object> frontmatter = (Map<String, Object>) parsed.get("frontmatter");

        String skillDir = filePath.getParent() != null ? filePath.getParent().toString() : "";
        String parentDirName = filePath.getParent() != null ? filePath.getParent().getFileName().toString() : "";

        // Validate description
        String description = frontmatter.get("description") != null
                ? frontmatter.get("description").toString() : null;
        for (String error : validateDescription(description)) {
            diagnostics.add(diag("warning", error, filePath.toString()));
        }

        // Resolve name
        String name = frontmatter.get("name") != null
                ? frontmatter.get("name").toString() : parentDirName;
        for (String error : validateName(name, parentDirName)) {
            diagnostics.add(diag("warning", error, filePath.toString()));
        }

        // Require description
        if (description == null || description.isBlank()) {
            return new LoadResult(null, diagnostics);
        }

        boolean disableModelInvocation = Boolean.TRUE.equals(
                frontmatter.get("disable-model-invocation"));

        String body = parsed.get("body") != null ? parsed.get("body").toString() : "";

        Skill skill = new Skill(
                name, description,
                body,
                filePath.toAbsolutePath().toString(),
                skillDir,
                disableModelInvocation);

        return new LoadResult(skill, diagnostics);
    }

    /**
     * Load skills from a directory, recursing into subdirectories.
     */
    public static LoadSkillsResult loadSkillsFromDir(Path dir) {
        List<Skill> skills = new ArrayList<>();
        List<Map<String, Object>> diagnostics = new ArrayList<>();

        if (!Files.exists(dir)) {
            return new LoadSkillsResult(skills, diagnostics);
        }

        // If directory contains SKILL.md, treat as a skill root
        Path skillMd = dir.resolve("SKILL.md");
        if (Files.isRegularFile(skillMd)) {
            LoadResult result = loadSkillFromFile(skillMd);
            diagnostics.addAll(result.diagnostics());
            if (result.skill() != null) {
                skills.add(result.skill());
            }
            return new LoadSkillsResult(skills, diagnostics);
        }

        // Otherwise, load .md children and recurse into subdirectories
        try (Stream<Path> entries = Files.list(dir)) {
            List<Path> sorted = entries.sorted().toList();
            for (Path entry : sorted) {
                String name = entry.getFileName().toString();
                if (name.startsWith(".") || name.equals("node_modules")) continue;

                if (Files.isDirectory(entry)) {
                    LoadSkillsResult sub = loadSkillsFromDir(entry);
                    skills.addAll(sub.skills());
                    diagnostics.addAll(sub.diagnostics());
                } else if (name.endsWith(".md")) {
                    LoadResult result = loadSkillFromFile(entry);
                    diagnostics.addAll(result.diagnostics());
                    if (result.skill() != null) {
                        skills.add(result.skill());
                    }
                }
            }
        } catch (IOException e) {
            diagnostics.add(diag("warning", "Failed to list directory: " + e.getMessage(), dir.toString()));
        }

        return new LoadSkillsResult(skills, diagnostics);
    }

    /**
     * Load skills from multiple sources with collision detection.
     *
     * @param cwd              current working directory
     * @param agentDir         agent config directory (default: ~/.agent-core/agent)
     * @param skillPaths       additional skill paths (files or directories)
     * @param includeDefaults  whether to include default skill directories
     */
    public static LoadSkillsResult loadSkills(
            Path cwd, Path agentDir, List<Path> skillPaths, boolean includeDefaults) {

        Map<String, Skill> skillMap = new LinkedHashMap<>();
        List<Map<String, Object>> allDiagnostics = new ArrayList<>();

        // Helper to merge skills with collision detection
        if (includeDefaults) {
            Path userSkillsDir = agentDir.resolve("skills");
            Path projectSkillsDir = cwd.resolve(".agent-core").resolve("skills");

            mergeSkills(loadSkillsFromDir(userSkillsDir), skillMap, allDiagnostics);
            mergeSkills(loadSkillsFromDir(projectSkillsDir), skillMap, allDiagnostics);
        }

        if (skillPaths != null) {
            for (Path rawPath : skillPaths) {
                Path resolved = rawPath.isAbsolute() ? rawPath : cwd.resolve(rawPath);
                if (!Files.exists(resolved)) {
                    allDiagnostics.add(diag("warning", "skill path does not exist", resolved.toString()));
                    continue;
                }
                if (Files.isDirectory(resolved)) {
                    mergeSkills(loadSkillsFromDir(resolved), skillMap, allDiagnostics);
                } else if (resolved.toString().endsWith(".md")) {
                    LoadResult result = loadSkillFromFile(resolved);
                    allDiagnostics.addAll(result.diagnostics());
                    if (result.skill() != null) {
                        mergeSkills(new LoadSkillsResult(List.of(result.skill()), List.of()),
                                skillMap, allDiagnostics);
                    }
                } else {
                    allDiagnostics.add(diag("warning", "skill path is not a markdown file", resolved.toString()));
                }
            }
        }

        return new LoadSkillsResult(new ArrayList<>(skillMap.values()), allDiagnostics);
    }

    /**
     * Convenience: load skills with defaults from current working directory.
     */
    public static LoadSkillsResult loadSkills() {
        Path cwd = Path.of(System.getProperty("user.dir"));
        Path agentDir = Path.of(System.getProperty("user.home"), ".agent-core", "agent");
        return loadSkills(cwd, agentDir, List.of(), true);
    }

    // ── Formatting ───────────────────────────────────────────

    /**
     * Format skills into a system prompt snippet.
     */
    public static String formatSkillsForPrompt(List<Skill> skills) {
        List<Skill> visible = skills.stream()
                .filter(s -> !s.disableModelInvocation())
                .toList();
        if (visible.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("\n\nThe following skills provide specialized instructions for specific tasks.\n");
        sb.append("Use the read tool to load a skill's file when the task matches its description.\n");
        sb.append("When a skill file references a relative path, resolve it against the skill directory ");
        sb.append("(parent of SKILL.md / dirname of the path) and use that absolute path in tool commands.\n");
        sb.append("\n<available_skills>\n");

        for (Skill skill : visible) {
            sb.append("  <skill>\n");
            sb.append("    <name>").append(escapeXml(skill.name())).append("</name>\n");
            sb.append("    <description>").append(escapeXml(skill.description())).append("</description>\n");
            sb.append("    <location>").append(escapeXml(skill.filePath())).append("</location>\n");
            sb.append("  </skill>\n");
        }
        sb.append("</available_skills>");

        return sb.toString();
    }

    // ── Helpers ──────────────────────────────────────────────

    private static void mergeSkills(LoadSkillsResult result, Map<String, Skill> skillMap,
                                   List<Map<String, Object>> allDiagnostics) {
        allDiagnostics.addAll(result.diagnostics());
        for (Skill skill : result.skills()) {
            Skill existing = skillMap.get(skill.name());
            if (existing != null) {
                allDiagnostics.add(Map.of(
                        "type", "collision",
                        "message", "name \"" + skill.name() + "\" collision",
                        "path", skill.filePath(),
                        "collision", Map.of(
                                "resource_type", "skill",
                                "name", skill.name(),
                                "winner_path", existing.filePath(),
                                "loser_path", skill.filePath()
                        )
                ));
            } else {
                skillMap.put(skill.name(), skill);
            }
        }
    }

    private static Map<String, Object> diag(String type, String message, String path) {
        return Map.of("type", type, "message", message, "path", path);
    }

    /**
     * XML-escape a string for safe embedding in prompt tags.
     */
    static String escapeXml(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * Intermediate result holder for single-file loading.
     */
    public record LoadResult(Skill skill, List<Map<String, Object>> diagnostics) {
        public LoadResult {
            if (diagnostics == null) diagnostics = List.of();
        }
    }
}
