package io.agentcore.resources;

import io.agentcore.resources.ResourceTypes.Persona;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentcore.llm.ProviderUtils;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persona (agent role) loading from .pi/personas/*.json.
 *
 * <p>Mirrors Python {@code agent_core/resources/personas.py}.
 */
public final class PersonaLoader {

    private static final Logger log = LoggerFactory.getLogger(PersonaLoader.class);
    private static final ObjectMapper MAPPER = ProviderUtils.mapper();

    private PersonaLoader() {}

    /**
     * Load persona definitions from .pi/personas/*.json and ~/.pi/agent/personas/*.json.
     */
    public static List<Persona> loadPersonas(Path cwd) {
        return loadPersonas(cwd, null);
    }

    /**
     * Load persona definitions with diagnostics support.
     *
     * @param cwd         the project working directory
     * @param diagnostics optional collector for warnings/errors (may be null)
     */
    public static List<Persona> loadPersonas(Path cwd, DiagnosticsCollector diagnostics) {
        List<Path> searchDirs = new ArrayList<>();

        Path localDir = cwd.resolve(".pi").resolve("personas");
        if (Files.isDirectory(localDir)) searchDirs.add(localDir);

        Path homeDir = Path.of(System.getProperty("user.home"), ".pi", "agent", "personas");
        if (Files.isDirectory(homeDir)) searchDirs.add(homeDir);

        List<Persona> personas = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        for (Path dir : searchDirs) {
            try (Stream<Path> files = Files.list(dir)) {
                for (Path file : files.sorted().toList()) {
                    if (!file.getFileName().toString().endsWith(".json")) continue;

                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> data = MAPPER.readValue(
                                Files.readString(file), Map.class);

                        String id = (String) data.get("id");
                        if (id == null || id.isBlank()) {
                            if (diagnostics != null) {
                                diagnostics.warning("Missing 'id' in persona file", file.toString());
                            }
                            continue;
                        }
                        if (seenIds.contains(id)) {
                            if (diagnostics != null) {
                                diagnostics.collision(
                                        "Persona id \"" + id + "\" collision",
                                        personas.stream()
                                                .filter(p -> p.id().equals(id))
                                                .findFirst().map(Persona::id).orElse(""),
                                        file.toString());
                            }
                            continue;
                        }
                        seenIds.add(id);

                        @SuppressWarnings("unchecked")
                        List<String> tools = data.containsKey("enabled_tools")
                                ? (List<String>) data.get("enabled_tools") : null;
                        @SuppressWarnings("unchecked")
                        List<String> kbs = data.containsKey("knowledge_bases")
                                ? (List<String>) data.get("knowledge_bases") : null;

                        personas.add(new Persona(
                                id,
                                (String) data.getOrDefault("name", id),
                                (String) data.getOrDefault("description", ""),
                                (String) data.getOrDefault("system_prompt", ""),
                                tools, kbs));
                    } catch (Exception e) {
                        if (diagnostics != null) diagnostics.warning(e.getMessage(), file.toString());
                        log.debug("Failed to load persona: {}", file, e);
                    }
                }
            } catch (IOException e) {
                if (diagnostics != null) diagnostics.warning("Failed to list personas: " + e.getMessage(), dir.toString());
                log.debug("Failed to list personas dir: {}", dir, e);
            }
        }
        return personas;
    }

    /**
     * Load a single persona by ID.
     */
    public static Persona getPersona(String personaId, Path cwd) {
        for (Persona p : loadPersonas(cwd)) {
            if (p.id().equals(personaId)) return p;
        }
        return null;
    }

    /**
     * Save (create or update) a persona JSON file.
     */
    public static void savePersona(Persona persona, Path cwd) throws IOException {
        Path dir = cwd.resolve(".pi").resolve("personas");
        Files.createDirectories(dir);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", persona.id());
        data.put("name", persona.name());
        data.put("description", persona.description());
        data.put("system_prompt", persona.systemPrompt());
        if (persona.enabledTools() != null && !persona.enabledTools().isEmpty()) data.put("enabled_tools", persona.enabledTools());
        if (persona.knowledgeBases() != null && !persona.knowledgeBases().isEmpty()) data.put("knowledge_bases", persona.knowledgeBases());

        Path file = dir.resolve(persona.id() + ".json");
        Files.writeString(file,
                MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(data) + "\n");
    }

    /**
     * Delete a persona JSON file. Returns true if found.
     */
    public static boolean deletePersona(String personaId, Path cwd) throws IOException {
        Path file = cwd.resolve(".pi").resolve("personas").resolve(personaId + ".json");
        return Files.deleteIfExists(file);
    }
}
