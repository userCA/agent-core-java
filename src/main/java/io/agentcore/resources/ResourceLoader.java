package io.agentcore.resources;

import io.agentcore.resources.ContextFileLoader.ContextFile;
import io.agentcore.resources.ResourceTypes.*;
import io.agentcore.skill.Skill;
import io.agentcore.skill.SkillLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unified resource loader — discovers and loads skills, prompts, themes,
 * context files, and extension specs from project and user directories.
 *
 * <p>Mirrors Python {@code agent_core/resources/loader.py ResourceLoader}.
 *
 * <p>Search order (per resource type):
 * <ol>
 *   <li>Explicit extra paths (constructor arg)</li>
 *   <li>{@code <cwd>/.pi/<resource_type>}</li>
 *   <li>{@code ~/.pi/agent/<resource_type>}</li>
 *   <li>{@code AGENT_CORE_<TYPE>_PATH} environment variable</li>
 * </ol>
 */
public final class ResourceLoader {

    private static final Logger log = LoggerFactory.getLogger(ResourceLoader.class);

    private final Path cwd;
    private final List<String> extraSkillPaths;
    private final List<String> extraPromptPaths;
    private final List<String> extraThemePaths;

    public ResourceLoader() {
        this(Path.of(System.getProperty("user.dir")), List.of(), List.of(), List.of());
    }

    public ResourceLoader(Path cwd,
                          List<String> extraSkillPaths,
                          List<String> extraPromptPaths,
                          List<String> extraThemePaths) {
        this.cwd = cwd;
        this.extraSkillPaths = extraSkillPaths != null ? extraSkillPaths : List.of();
        this.extraPromptPaths = extraPromptPaths != null ? extraPromptPaths : List.of();
        this.extraThemePaths = extraThemePaths != null ? extraThemePaths : List.of();
    }

    // ── search paths ──────────────────────────────────────────────────

    List<Path> searchPaths(String resourceType, List<String> extras) {
        List<Path> paths = new ArrayList<>();
        for (String p : extras) {
            Path resolved = Path.of(p.replaceFirst("^~", System.getProperty("user.home")));
            if (Files.exists(resolved)) paths.add(resolved.toAbsolutePath());
        }
        Path localDir = cwd.resolve(".pi").resolve(resourceType);
        if (Files.isDirectory(localDir)) paths.add(localDir);

        Path homeDir = Path.of(System.getProperty("user.home"), ".pi", "agent", resourceType);
        if (Files.isDirectory(homeDir)) paths.add(homeDir);

        String envVar = System.getenv("AGENT_CORE_" + resourceType.toUpperCase() + "_PATH");
        if (envVar != null) {
            for (String p : envVar.split(":")) {
                Path resolved = Path.of(p.replaceFirst("^~", System.getProperty("user.home")));
                if (Files.exists(resolved)) paths.add(resolved.toAbsolutePath());
            }
        }
        return paths;
    }

    // ── skills ────────────────────────────────────────────────────────

    public record SkillLoadResult(List<Skill> skills, List<ResourceDiagnostic> diagnostics) {}

    public SkillLoadResult loadSkills() {
        DiagnosticsCollector diag = new DiagnosticsCollector();
        Map<String, Skill> skills = new LinkedHashMap<>();
        Set<String> seenPaths = new HashSet<>();

        for (Path dir : searchPaths("skills", extraSkillPaths)) {
            try (Stream<Path> stream = Files.walk(dir)) {
                for (Path file : stream.toList()) {
                    if (!file.getFileName().toString().equals("SKILL.md")) continue;
                    String resolved = file.toAbsolutePath().normalize().toString();
                    if (seenPaths.contains(resolved)) continue;
                    seenPaths.add(resolved);

                    SkillLoader.LoadResult result = SkillLoader.loadSkillFromFile(file);
                    Skill skill = result.skill();
                    if (skill == null) continue;

                    Skill existing = skills.get(skill.name());
                    if (existing != null) {
                        diag.collision(
                                "Skill name \"" + skill.name() + "\" collision",
                                existing.filePath(), skill.filePath());
                    } else {
                        skills.put(skill.name(), skill);
                    }
                }
            } catch (IOException e) {
                diag.warning("Failed to scan skills: " + e.getMessage(), dir.toString());
            }
        }
        return new SkillLoadResult(List.copyOf(skills.values()), diag.getItems());
    }

    // ── prompts ───────────────────────────────────────────────────────

    public record PromptLoadResult(List<PromptTemplate> prompts, List<ResourceDiagnostic> diagnostics) {}

    public PromptLoadResult loadPromptTemplates() {
        DiagnosticsCollector diag = new DiagnosticsCollector();
        Map<String, PromptTemplate> prompts = new LinkedHashMap<>();

        for (Path dir : searchPaths("prompts", extraPromptPaths)) {
            try (Stream<Path> stream = Files.walk(dir)) {
                for (Path file : stream.toList()) {
                    if (!file.getFileName().toString().endsWith(".md")) continue;

                    PromptTemplate pt = PromptLoader.loadPromptFromFile(
                            file.toAbsolutePath().toString(), diag);
                    if (pt == null) continue;

                    PromptTemplate existing = prompts.get(pt.name());
                    if (existing != null) {
                        diag.collision(
                                "Prompt template \"" + pt.name() + "\" collision",
                                existing.source() != null ? existing.source().origin() : "",
                                pt.source() != null ? pt.source().origin() : "");
                    } else {
                        prompts.put(pt.name(), pt);
                    }
                }
            } catch (IOException e) {
                diag.warning("Failed to scan prompts: " + e.getMessage(), dir.toString());
            }
        }
        return new PromptLoadResult(List.copyOf(prompts.values()), diag.getItems());
    }

    // ── themes ────────────────────────────────────────────────────────

    public record ThemeLoadResult(List<Theme> themes, List<ResourceDiagnostic> diagnostics) {}

    public ThemeLoadResult loadThemes() {
        DiagnosticsCollector diag = new DiagnosticsCollector();
        Map<String, Theme> themes = new LinkedHashMap<>();

        for (Path dir : searchPaths("themes", extraThemePaths)) {
            try (Stream<Path> stream = Files.walk(dir)) {
                for (Path file : stream.toList()) {
                    if (!file.getFileName().toString().endsWith(".json")) continue;

                    Theme theme = ThemeLoader.loadThemeFromFile(
                            file.toAbsolutePath().toString(), diag);
                    if (theme == null) continue;

                    Theme existing = themes.get(theme.name());
                    if (existing != null) {
                        diag.collision(
                                "Theme \"" + theme.name() + "\" collision",
                                existing.source() != null ? existing.source().origin() : "",
                                theme.source() != null ? theme.source().origin() : "");
                    } else {
                        themes.put(theme.name(), theme);
                    }
                }
            } catch (IOException e) {
                diag.warning("Failed to scan themes: " + e.getMessage(), dir.toString());
            }
        }
        return new ThemeLoadResult(List.copyOf(themes.values()), diag.getItems());
    }

    // ── context files ─────────────────────────────────────────────────

    public List<ContextFile> loadContextFiles() {
        return new ContextFileLoader().load(cwd);
    }

    // ── extension specs ───────────────────────────────────────────────

    public record ExtensionLoadResult(List<ExtensionSpec> specs, List<ResourceDiagnostic> diagnostics) {}

    public ExtensionLoadResult loadExtensionSpecs() {
        DiagnosticsCollector diag = new DiagnosticsCollector();
        List<String> paths = searchPaths("extensions", List.of()).stream()
                .map(Path::toString).toList();
        List<ExtensionSpec> specs = ExtensionSpecLoader.discoverSpecs(paths, diag);
        return new ExtensionLoadResult(specs, diag.getItems());
    }

    /** Returns the configured working directory. */
    public Path getCwd() {
        return cwd;
    }
}
