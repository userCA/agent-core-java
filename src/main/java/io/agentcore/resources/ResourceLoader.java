package io.agentcore.resources;

import io.agentcore.resources.ContextFileLoader.ContextFile;
import io.agentcore.resources.ResourceTypes.*;
import io.agentcore.skill.Skill;
import io.agentcore.skill.SkillLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unified resource loader — discovers and loads skills, prompts, themes,
 * context files, and extension specs from project and user directories.
 *
 * <p>Mirrors Python {@code agent_core/resources/loader.py ResourceLoader}.
 *
 * @deprecated Not integrated into the main agent runtime. Most resource types
 *             (themes, personas, extension specs) are loaded but never consumed.
 *             Prefer {@link ContextFileLoader} directly for context file loading.
 */
@Deprecated
public final class ResourceLoader {

    private static final Logger log = LoggerFactory.getLogger(ResourceLoader.class);
    private static final int DEFAULT_WALK_DEPTH = 5;

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
        Set<Path> uniquePaths = new LinkedHashSet<>();

        for (String p : extras) {
            Path resolved = Path.of(p.replaceFirst("^~", System.getProperty("user.home")));
            if (Files.exists(resolved)) uniquePaths.add(resolved.toAbsolutePath().normalize());
        }
        Path localDir = cwd.resolve(".pi").resolve(resourceType);
        if (Files.isDirectory(localDir)) uniquePaths.add(localDir.toAbsolutePath().normalize());

        Path homeDir = Path.of(System.getProperty("user.home"), ".pi", "agent", resourceType);
        if (Files.isDirectory(homeDir)) uniquePaths.add(homeDir.toAbsolutePath().normalize());

        String envVar = System.getenv("AGENT_CORE_" + resourceType.toUpperCase() + "_PATH");
        if (envVar != null) {
            for (String p : envVar.split(":")) {
                Path resolved = Path.of(p.replaceFirst("^~", System.getProperty("user.home")));
                if (Files.exists(resolved)) uniquePaths.add(resolved.toAbsolutePath().normalize());
            }
        }
        return List.copyOf(uniquePaths);
    }

    // ── generic walk-and-load ────────────────────────────────────────

    /**
     * Generic resource walker: scans search directories, filters files,
     * loads items, deduplicates by name, and reports collisions.
     */
    private <T> List<T> walkAndLoad(
            String resourceType,
            List<String> extras,
            Predicate<String> fileNameFilter,
            BiFunction<Path, DiagnosticsCollector, T> fileLoader,
            Function<T, String> nameExtractor,
            Function<T, String> originExtractor,
            String collisionLabel,
            DiagnosticsCollector diag) {

        Map<String, T> items = new LinkedHashMap<>();
        Set<String> seenPaths = new HashSet<>();

        for (Path dir : searchPaths(resourceType, extras)) {
            try (Stream<Path> stream = Files.walk(dir, DEFAULT_WALK_DEPTH)) {
                for (Path file : stream.toList()) {
                    if (!Files.isRegularFile(file)) continue;
                    if (!fileNameFilter.test(file.getFileName().toString())) continue;

                    String resolved = file.toAbsolutePath().normalize().toString();
                    if (!seenPaths.add(resolved)) continue;

                    T item = fileLoader.apply(file, diag);
                    if (item == null) continue;

                    String name = nameExtractor.apply(item);
                    T existing = items.get(name);
                    if (existing != null) {
                        String existingOrigin = originExtractor.apply(existing);
                        String newOrigin = originExtractor.apply(item);
                        diag.collision(
                                collisionLabel + " \"" + name + "\" collision",
                                existingOrigin != null ? existingOrigin : "",
                                newOrigin != null ? newOrigin : "");
                    } else {
                        items.put(name, item);
                    }
                }
            } catch (IOException e) {
                diag.warning("Failed to scan " + resourceType + ": " + e.getMessage(), dir.toString());
            }
        }
        return List.copyOf(items.values());
    }

    // ── skills ────────────────────────────────────────────────────────

    public record SkillLoadResult(List<Skill> skills, List<ResourceDiagnostic> diagnostics) {}

    public SkillLoadResult loadSkills() {
        DiagnosticsCollector diag = new DiagnosticsCollector();
        List<Skill> skills = walkAndLoad(
                "skills", extraSkillPaths,
                name -> name.equals("SKILL.md"),
                (file, d) -> {
                    SkillLoader.LoadResult result = SkillLoader.loadSkillFromFile(file);
                    return result.skill();
                },
                Skill::name,
                Skill::filePath,
                "Skill name", diag);
        return new SkillLoadResult(skills, diag.getItems());
    }

    // ── prompts ───────────────────────────────────────────────────────

    public record PromptLoadResult(List<PromptTemplate> prompts, List<ResourceDiagnostic> diagnostics) {}

    public PromptLoadResult loadPromptTemplates() {
        DiagnosticsCollector diag = new DiagnosticsCollector();
        List<PromptTemplate> prompts = walkAndLoad(
                "prompts", extraPromptPaths,
                name -> name.endsWith(".md"),
                (file, d) -> PromptLoader.loadPromptFromFile(file.toAbsolutePath().toString(), d),
                PromptTemplate::name,
                pt -> pt.source() != null ? pt.source().origin() : "",
                "Prompt template", diag);
        return new PromptLoadResult(prompts, diag.getItems());
    }

    // ── themes ────────────────────────────────────────────────────────

    public record ThemeLoadResult(List<Theme> themes, List<ResourceDiagnostic> diagnostics) {}

    public ThemeLoadResult loadThemes() {
        DiagnosticsCollector diag = new DiagnosticsCollector();
        List<Theme> themes = walkAndLoad(
                "themes", extraThemePaths,
                name -> name.endsWith(".json"),
                (file, d) -> ThemeLoader.loadThemeFromFile(file.toAbsolutePath().toString(), d),
                Theme::name,
                t -> t.source() != null ? t.source().origin() : "",
                "Theme", diag);
        return new ThemeLoadResult(themes, diag.getItems());
    }

    // ── context files ─────────────────────────────────────────────────

    public List<ContextFile> loadContextFiles() {
        return new ContextFileLoader().load(cwd);
    }

    // ── personas ──────────────────────────────────────────────────────

    public record PersonaLoadResult(List<Persona> personas, List<ResourceDiagnostic> diagnostics) {}

    public PersonaLoadResult loadPersonas() {
        DiagnosticsCollector diag = new DiagnosticsCollector();
        List<Persona> personas = PersonaLoader.loadPersonas(cwd, diag);
        return new PersonaLoadResult(personas, diag.getItems());
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

    // ── aggregate ─────────────────────────────────────────────────────

    /**
     * Aggregate result from loading all resource types.
     */
    public record AllResources(
            List<Skill> skills,
            List<PromptTemplate> prompts,
            List<Theme> themes,
            List<Persona> personas,
            List<ContextFile> contextFiles,
            List<ExtensionSpec> extensionSpecs,
            List<ResourceDiagnostic> diagnostics) {}

    /**
     * Load all resource types in one call, aggregating diagnostics.
     */
    public AllResources loadAll() {
        var skillResult = loadSkills();
        var promptResult = loadPromptTemplates();
        var themeResult = loadThemes();
        var personaResult = loadPersonas();
        var contextFiles = loadContextFiles();
        var extResult = loadExtensionSpecs();

        List<ResourceDiagnostic> allDiag = new ArrayList<>();
        allDiag.addAll(skillResult.diagnostics());
        allDiag.addAll(promptResult.diagnostics());
        allDiag.addAll(themeResult.diagnostics());
        allDiag.addAll(personaResult.diagnostics());
        allDiag.addAll(extResult.diagnostics());

        return new AllResources(
                skillResult.skills(),
                promptResult.prompts(),
                themeResult.themes(),
                personaResult.personas(),
                contextFiles,
                extResult.specs(),
                List.copyOf(allDiag));
    }

    /** Returns the configured working directory. */
    public Path getCwd() {
        return cwd;
    }
}
