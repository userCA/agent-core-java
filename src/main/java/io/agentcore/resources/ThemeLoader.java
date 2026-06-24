package io.agentcore.resources;

import io.agentcore.resources.ResourceTypes.SourceInfo;
import io.agentcore.resources.ResourceTypes.Theme;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentcore.llm.ProviderUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Theme discovery and loading from JSON files.
 *
 * <p>Mirrors Python {@code agent_core/resources/themes.py}.
 */
public final class ThemeLoader {

    private static final Logger log = LoggerFactory.getLogger(ThemeLoader.class);
    private static final ObjectMapper MAPPER = ProviderUtils.mapper();

    private ThemeLoader() {}

    /**
     * Load a theme from a JSON file.
     *
     * @param filePath    path to the JSON file
     * @param diagnostics collects warnings on parse failures
     * @return loaded Theme, or null on failure
     */
    @SuppressWarnings("unchecked")
    public static Theme loadThemeFromFile(String filePath, DiagnosticsCollector diagnostics) {
        try {
            String raw = Files.readString(Path.of(filePath));
            Map<String, Object> data = MAPPER.readValue(raw, Map.class);

            Object nameObj = data.get("name");
            if (nameObj == null || nameObj.toString().isBlank()) {
                if (diagnostics != null) diagnostics.warning("Missing 'name' field", filePath);
                return null;
            }

            return new Theme(
                    nameObj.toString(),
                    data,
                    new SourceInfo(
                            "project", "project", filePath,
                            Path.of(filePath).getParent() != null
                                    ? Path.of(filePath).getParent().toString() : ""));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            if (diagnostics != null) diagnostics.warning("Invalid JSON: " + e.getMessage(), filePath);
            return null;
        } catch (Exception e) {
            if (diagnostics != null) diagnostics.warning(e.getMessage(), filePath);
            log.debug("Failed to load theme: {}", filePath, e);
            return null;
        }
    }
}
