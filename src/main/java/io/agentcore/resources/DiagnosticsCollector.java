package io.agentcore.resources;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Accumulates {@link ResourceDiagnostic} items during resource loading.
 *
 * <p>Mirrors Python {@code agent_core/resources/diagnostics.py ResourceDiagnostics}.
 * Thread-safe via synchronized list.
 */
public final class DiagnosticsCollector {

    private final List<ResourceDiagnostic> items = Collections.synchronizedList(new ArrayList<>());

    public void warning(String message, String sourcePath) {
        items.add(ResourceDiagnostic.warning(message, sourcePath));
    }

    public void error(String message, String sourcePath) {
        items.add(ResourceDiagnostic.error(message, sourcePath));
    }

    public void collision(String message, String winnerPath, String loserPath) {
        items.add(ResourceDiagnostic.collision(message, winnerPath, loserPath));
    }

    public void add(ResourceDiagnostic diagnostic) {
        items.add(diagnostic);
    }

    public List<ResourceDiagnostic> getItems() {
        return List.copyOf(items);
    }

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public void clear() {
        items.clear();
    }
}
