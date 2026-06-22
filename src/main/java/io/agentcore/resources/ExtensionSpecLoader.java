package io.agentcore.resources;

import io.agentcore.resources.ResourceTypes.ExtensionSpec;
import io.agentcore.resources.ResourceTypes.SourceInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ExtensionSpec discovery from project and user extension directories.
 *
 * <p>Mirrors Python {@code agent_core/resources/extensions.py}.
 * Discovers extension entry points without dynamically loading them.
 */
public final class ExtensionSpecLoader {

    private static final Logger log = LoggerFactory.getLogger(ExtensionSpecLoader.class);

    private ExtensionSpecLoader() {}

    /**
     * Discover extension specs from a list of search directories.
     *
     * <p>Looks for files named {@code extension.py} or containing "extension" in the stem.
     * In Java context, looks for {@code extension.json} or {@code Extension.java} files.
     */
    public static List<ExtensionSpec> discoverSpecs(
            List<String> searchPaths,
            DiagnosticsCollector diagnostics) {

        List<ExtensionSpec> specs = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (String searchPath : searchPaths) {
            Path dir = Path.of(searchPath);
            if (!Files.isDirectory(dir)) continue;

            try (Stream<Path> stream = Files.walk(dir)) {
                for (Path entry : stream.toList()) {
                    if (!Files.isRegularFile(entry)) continue;

                    String name = entry.getFileName().toString();
                    String stem = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;

                    // Match extension.json or *Extension.java or extension.* files
                    boolean isExtensionFile =
                            name.equals("extension.json")
                                    || name.equals("extension.yaml")
                                    || name.equals("extension.yml")
                                    || (name.endsWith(".java") && stem.toLowerCase().contains("extension"))
                                    || (name.endsWith(".json") && stem.toLowerCase().contains("extension"));

                    if (!isExtensionFile) continue;

                    String resolved = entry.toAbsolutePath().normalize().toString();
                    if (seen.contains(resolved)) continue;
                    seen.add(resolved);

                    String modulePath = Files.isDirectory(entry.getParent().resolve("__init__.py"))
                            ? entry.getParent().toString()
                            : resolved;

                    specs.add(new ExtensionSpec(
                            stem,
                            modulePath,
                            new SourceInfo("project", "project", resolved, dir.toString())));
                }
            } catch (IOException e) {
                if (diagnostics != null) diagnostics.warning("Failed to scan: " + e.getMessage(), searchPath);
                log.debug("Failed to scan extension dir: {}", dir, e);
            }
        }
        return specs;
    }
}
