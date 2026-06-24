package io.agentcore.resources;

import io.agentcore.resources.ResourceTypes.PromptTemplate;
import io.agentcore.resources.ResourceTypes.SourceInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Prompt template discovery and loading from markdown files with YAML frontmatter.
 *
 * <p>Mirrors Python {@code agent_core/resources/prompts.py}.
 * Uses a simple YAML subset parser (no external YAML library needed).
 *
 * @deprecated Not integrated into the main agent runtime.
 */
@Deprecated
public final class PromptLoader {

    private static final Logger log = LoggerFactory.getLogger(PromptLoader.class);
    private static final ConcurrentHashMap<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();
    private static final Pattern ITEM_PATTERN = Pattern.compile("^\\s+-\\s+(.+)$", Pattern.MULTILINE);

    private PromptLoader() {}

    /**
     * Load a prompt template from a markdown file with frontmatter.
     *
     * <p>Expected format:
     * <pre>
     * ---
     * name: system-prompt
     * description: Main agent system prompt
     * parameters:
     *   - user_name
     *   - project_name
     * ---
     * Hello {{user_name}}, welcome to {{project_name}}.
     * </pre>
     */
    public static PromptTemplate loadPromptFromFile(String filePath, DiagnosticsCollector diagnostics) {
        try {
            String rawContent = Files.readString(Path.of(filePath));

            if (!rawContent.startsWith("---")) {
                if (diagnostics != null) diagnostics.warning("Missing YAML frontmatter", filePath);
                return null;
            }

            // Split at --- boundaries
            int secondDash = rawContent.indexOf("---", 3);
            if (secondDash < 0) {
                if (diagnostics != null) diagnostics.warning("Invalid frontmatter format", filePath);
                return null;
            }

            String frontmatterRaw = rawContent.substring(3, secondDash).strip();
            String template = rawContent.substring(secondDash + 3).strip();

            // Parse simple YAML frontmatter
            String name = extractYamlValue(frontmatterRaw, "name");
            if (name == null || name.isBlank()) {
                if (diagnostics != null) diagnostics.warning("Missing 'name' in frontmatter", filePath);
                return null;
            }

            String description = extractYamlValue(frontmatterRaw, "description");
            if (description == null) description = "";

            List<String> parameters = extractYamlList(frontmatterRaw, "parameters");

            return new PromptTemplate(
                    name, description, template, parameters,
                    new SourceInfo("project", "project", filePath,
                            Path.of(filePath).getParent() != null
                                    ? Path.of(filePath).getParent().toString() : ""));

        } catch (IOException e) {
            if (diagnostics != null) diagnostics.warning(e.getMessage(), filePath);
            log.debug("Failed to load prompt: {}", filePath, e);
            return null;
        }
    }

    /**
     * Extract a scalar value from simple YAML: {@code key: value}.
     */
    static String extractYamlValue(String yaml, String key) {
        Pattern p = PATTERN_CACHE.computeIfAbsent("value:" + key,
                k -> Pattern.compile("(?m)^" + Pattern.quote(key) + "\\s*:\\s*(.+)$"));
        Matcher m = p.matcher(yaml);
        if (m.find()) {
            String val = m.group(1).strip();
            // Strip surrounding quotes
            if (val.length() >= 2
                    && ((val.startsWith("\"") && val.endsWith("\""))
                        || (val.startsWith("'") && val.endsWith("'")))) {
                val = val.substring(1, val.length() - 1);
            }
            return val;
        }
        return null;
    }

    /**
     * Extract a YAML list (both inline [a, b] and block - item format).
     */
    static List<String> extractYamlList(String yaml, String key) {
        List<String> result = new ArrayList<>();

        // Try inline list: parameters: [a, b, c]
        Pattern inline = PATTERN_CACHE.computeIfAbsent("inline:" + key,
                k -> Pattern.compile("(?m)^" + Pattern.quote(key) + "\\s*:\\s*\\[(.+)]"));
        Matcher m = inline.matcher(yaml);
        if (m.find()) {
            for (String item : m.group(1).split(",")) {
                String v = item.strip();
                if (!v.isEmpty()) result.add(v.replaceAll("^[\"']|[\"']$", ""));
            }
            return result;
        }

        // Try block list: parameters:\n  - item1\n  - item2
        Pattern block = PATTERN_CACHE.computeIfAbsent("block:" + key,
                k -> Pattern.compile("(?m)^" + Pattern.quote(key) + "\\s*:\\s*\\n((?:\\s+-\\s+.+\\n?)+)"));
        m = block.matcher(yaml);
        if (m.find()) {
            Matcher im = ITEM_PATTERN.matcher(m.group(1));
            while (im.find()) {
                String v = im.group(1).strip().replaceAll("^[\"']|[\"']$", "");
                if (!v.isEmpty()) result.add(v);
            }
        }
        return result;
    }
}
