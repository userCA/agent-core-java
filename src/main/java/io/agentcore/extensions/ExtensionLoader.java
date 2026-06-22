package io.agentcore.extensions;

import io.agentcore.resources.ResourceTypes.ExtensionSpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Dynamic extension loader — discovers and instantiates {@link Extension} implementations.
 *
 * <p>Mirrors Python {@code agent_core/extensions/loader.py} ExtensionLoader.
 * Uses Java's {@link ServiceLoader} (SPI) mechanism for pluggable extension discovery.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   // Auto-discover all extensions via SPI
 *   List<Extension> extensions = ExtensionLoader.loadFromServiceLoader();
 *
 *   // Load from ExtensionSpec list (from resource discovery)
 *   List<Extension> specs = ExtensionLoader.loadFromSpecs(specList);
 *
 *   // Combined
 *   List<Extension> all = ExtensionLoader.loadAll(specList);
 * }</pre>
 *
 * <h3>Registering a custom extension via SPI</h3>
 * <ol>
 *   <li>Implement the {@link Extension} interface</li>
 *   <li>Create a file {@code META-INF/services/io.agentcore.extensions.Extension}
 *       containing the fully qualified class name</li>
 * </ol>
 */
public final class ExtensionLoader {

    private static final Logger log = LoggerFactory.getLogger(ExtensionLoader.class);

    private ExtensionLoader() {}

    /**
     * Discover extensions via Java SPI ServiceLoader.
     *
     * <p>Looks for {@code META-INF/services/io.agentcore.extensions.Extension}
     * on the classpath.
     *
     * @return list of discovered Extension instances
     */
    public static List<Extension> loadFromServiceLoader() {
        return loadFromServiceLoader(Extension.class.getClassLoader());
    }

    /**
     * Discover extensions via Java SPI with a custom classloader.
     */
    public static List<Extension> loadFromServiceLoader(ClassLoader classLoader) {
        List<Extension> extensions = new ArrayList<>();
        ServiceLoader<Extension> loader = ServiceLoader.load(Extension.class, classLoader);
        for (Extension ext : loader) {
            try {
                if (ext.name() != null && !ext.name().isBlank()) {
                    extensions.add(ext);
                    log.debug("Loaded SPI extension: {}", ext.name());
                }
            } catch (Exception e) {
                log.warn("Failed to initialize SPI extension: {}", e.getMessage());
            }
        }
        return extensions;
    }

    /**
     * Load extensions from a list of {@link ExtensionSpec} records.
     *
     * <p>For each spec, attempts to:
     * <ol>
     *   <li>Load the class specified by {@code modulePath} via reflection</li>
     *   <li>Check if it implements {@link Extension}</li>
     *   <li>Instantiate it via the no-arg constructor</li>
     * </ol>
     *
     * @param specs the extension specs to load
     * @return list of successfully loaded extensions
     */
    public static List<Extension> loadFromSpecs(List<ExtensionSpec> specs) {
        List<Extension> extensions = new ArrayList<>();
        for (ExtensionSpec spec : specs) {
            try {
                Extension ext = loadSpec(spec);
                if (ext != null) {
                    extensions.add(ext);
                }
            } catch (Exception e) {
                log.warn("Failed to load extension {}: {}", spec.name(), e.getMessage());
            }
        }
        return extensions;
    }

    /**
     * Combined loader: merges SPI-discovered extensions with spec-based extensions.
     * De-duplicates by name (SPI extensions take priority).
     */
    public static List<Extension> loadAll(List<ExtensionSpec> specs) {
        Map<String, Extension> byName = new LinkedHashMap<>();

        // SPI first
        for (Extension ext : loadFromServiceLoader()) {
            byName.putIfAbsent(ext.name(), ext);
        }

        // Then specs
        for (Extension ext : loadFromSpecs(specs)) {
            byName.putIfAbsent(ext.name(), ext);
        }

        return new ArrayList<>(byName.values());
    }

    // ── Internals ──

    @SuppressWarnings("unchecked")
    private static Extension loadSpec(ExtensionSpec spec) {
        String className = spec.modulePath();
        if (className == null || className.isBlank()) {
            log.debug("Extension spec {} has no modulePath, skipping", spec.name());
            return null;
        }

        try {
            Class<?> cls = Class.forName(className);
            if (Extension.class.isAssignableFrom(cls)) {
                return (Extension) cls.getDeclaredConstructor().newInstance();
            }
            log.warn("Class {} does not implement Extension", className);
            return null;
        } catch (ClassNotFoundException e) {
            log.debug("Extension class not found: {}", className);
            return null;
        } catch (Exception e) {
            log.warn("Failed to instantiate extension {}: {}", className, e.getMessage());
            return null;
        }
    }
}
