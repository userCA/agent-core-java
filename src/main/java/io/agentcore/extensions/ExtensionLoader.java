package io.agentcore.extensions;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Loads Extension instances from SPI (META-INF/services) and merges with
 * manually registered extensions.
 *
 * <p>SPI extensions are loaded once per call via {@link ServiceLoader}.
 * Manual extensions are appended after SPI ones. Duplicates (by identity)
 * are removed, keeping the first occurrence.
 *
 * <p>Usage:
 * <pre>{@code
 * // Load SPI extensions only
 * List<Extension> exts = ExtensionLoader.load();
 *
 * // Load SPI + manual extensions
 * List<Extension> exts = ExtensionLoader.load(myCustomExtension);
 * }</pre>
 */
public final class ExtensionLoader {

    private ExtensionLoader() {}

    /**
     * Load SPI extensions merged with optional manual ones.
     *
     * @param manual additional manually-registered extensions (may be empty)
     * @return merged, deduplicated list of extensions
     */
    public static List<Extension> load(Extension... manual) {
        return load(List.of(manual));
    }

    /**
     * Load SPI extensions merged with a list of manual ones.
     *
     * @param manual additional manually-registered extensions (nullable)
     * @return merged, deduplicated list of extensions
     */
    public static List<Extension> load(List<Extension> manual) {
        LinkedHashSet<Extension> merged = new LinkedHashSet<>();
        ServiceLoader.load(Extension.class).forEach(merged::add);
        if (manual != null) merged.addAll(manual);
        return List.copyOf(merged);
    }
}
