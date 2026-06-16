package io.agentcore.resources;

import io.agentcore.resources.types.*;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Unified resource loader — discovers skills, prompts, themes, context files, and extensions.
 */
public class ResourceLoader {
    /** Maximum directory depth when searching for skill files. */
    private static final int MAX_SKILL_WALK_DEPTH = 10;

    private final String cwd;
    private final List<String> extraSkillPaths;
    private final List<String> extraPromptPaths;

    public ResourceLoader(String cwd) {
        this(cwd, List.of(), List.of());
    }

    public ResourceLoader(String cwd, List<String> extraSkillPaths, List<String> extraPromptPaths) {
        this.cwd = cwd;
        this.extraSkillPaths = extraSkillPaths;
        this.extraPromptPaths = extraPromptPaths;
    }

    public LoadResult<Skill> loadSkills() {
        ResourceDiagnostics diag = new ResourceDiagnostics();
        Map<String, Skill> skills = new LinkedHashMap<>();
        for (String searchPath : skillSearchPaths()) {
            Path dir = Path.of(searchPath);
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> stream = Files.walk(dir, MAX_SKILL_WALK_DEPTH)) {
                stream.filter(p -> p.getFileName().toString().equals("SKILL.md")).forEach(p -> {
                    Skill skill = parseSkill(p, diag);
                    if (skill != null && !skills.containsKey(skill.name())) {
                        skills.put(skill.name(), skill);
                    }
                });
            } catch (IOException ignored) {}
        }
        return new LoadResult<>(new ArrayList<>(skills.values()), diag);
    }

    public List<ContextFile> loadContextFiles() {
        List<ContextFile> result = new ArrayList<>();
        String[] filenames = {"AGENTS.md", "CLAUDE.md"};
        Path current = Path.of(cwd).toAbsolutePath();
        int depth = 0;
        while (current != null && depth < 20) {
            for (String fn : filenames) {
                Path f = current.resolve(fn);
                if (Files.exists(f)) {
                    try {
                        result.add(new ContextFile(f.toString(), Files.readString(f),
                                depth == 0 ? "cwd" : "ancestor"));
                    } catch (IOException ignored) {}
                }
            }
            if (Files.exists(current.resolve(".git"))) break;
            current = current.getParent();
            depth++;
        }
        return result;
    }

    private List<String> skillSearchPaths() {
        List<String> paths = new ArrayList<>(extraSkillPaths);
        paths.add(cwd + "/.pi/skills");
        String home = System.getProperty("user.home");
        if (home != null) paths.add(home + "/.pi/agent/skills");
        String envPath = System.getenv("AGENT_CORE_SKILLS_PATH");
        if (envPath != null) paths.addAll(List.of(envPath.split(":")));
        return paths;
    }

    @SuppressWarnings("unchecked")
    private Skill parseSkill(Path file, ResourceDiagnostics diag) {
        try {
            String content = Files.readString(file);
            String[] parts = content.split("---", 3);
            if (parts.length < 2) return null;
            String frontmatter = parts[1].trim();
            Yaml yaml = new Yaml();
            Map<String, Object> meta = yaml.load(frontmatter);
            if (meta == null) return null;
            String name = meta.get("name") != null ? meta.get("name").toString() : null;
            String description = meta.get("description") != null ? meta.get("description").toString() : null;
            boolean disableModelInvocation = Boolean.TRUE.equals(meta.get("disable_model_invocation"));
            if (name == null || name.isEmpty() || description == null || description.isEmpty()) return null;
            if (name.length() > 64 || description.length() > 1024) return null;
            SourceInfo source = new SourceInfo("project", "project", file.toString(), file.getParent().toString());
            return new Skill(name, description, content, source, disableModelInvocation);
        } catch (IOException e) {
            diag.error("Failed to read skill: " + e.getMessage(), file.toString());
            return null;
        } catch (Exception e) {
            diag.error("Failed to parse skill frontmatter: " + e.getMessage(), file.toString());
            return null;
        }
    }

    public record LoadResult<T>(List<T> items, ResourceDiagnostics diagnostics) {}
}
