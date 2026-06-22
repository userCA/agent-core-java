package io.agentcore.resources;

import java.util.List;
import java.util.Map;

/**
 * Resource type records used across the resource loading system.
 *
 * <p>Mirrors Python {@code agent_core/resources/types.py}.
 */
public final class ResourceTypes {

    private ResourceTypes() {}

    /**
     * Source metadata for a loaded resource.
     */
    public record SourceInfo(
            String source,    // "project", "user", "package", "explicit"
            String scope,     // "global", "project", "session"
            String origin,    // file path or URI
            String baseDir    // parent directory
    ) {
        public SourceInfo {
            if (source == null) source = "project";
            if (scope == null) scope = "project";
            if (origin == null) origin = "";
            if (baseDir == null) baseDir = "";
        }
    }

    /**
     * A prompt template loaded from disk.
     */
    public record PromptTemplate(
            String name,
            String description,
            String template,
            List<String> parameters,
            SourceInfo source
    ) {
        public PromptTemplate {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("name required");
            if (description == null) description = "";
            if (template == null) template = "";
            if (parameters == null) parameters = List.of();
        }
    }

    /**
     * A theme definition loaded from JSON.
     */
    public record Theme(
            String name,
            Map<String, Object> definition,
            SourceInfo source
    ) {
        public Theme {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("name required");
            if (definition == null) definition = Map.of();
        }
    }

    /**
     * An extension specification discovered from config.
     */
    public record ExtensionSpec(
            String name,
            String modulePath,
            SourceInfo source
    ) {
        public ExtensionSpec {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("name required");
            if (modulePath == null) modulePath = "";
        }
    }

    /**
     * A persona (agent role) definition.
     */
    public record Persona(
            String id,
            String name,
            String description,
            String systemPrompt,
            List<String> enabledTools,
            List<String> knowledgeBases
    ) {
        public Persona {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id required");
            if (name == null) name = id;
            if (description == null) description = "";
            if (systemPrompt == null) systemPrompt = "";
        }
    }
}
