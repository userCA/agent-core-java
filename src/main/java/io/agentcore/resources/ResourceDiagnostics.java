package io.agentcore.resources;

import io.agentcore.resources.types.ResourceDiagnostic;
import java.util.ArrayList;
import java.util.List;

public class ResourceDiagnostics {
    private final List<ResourceDiagnostic> items = new ArrayList<>();

    public void warning(String message, String sourcePath) {
        items.add(new ResourceDiagnostic("warning", message, sourcePath, null, null));
    }
    public void error(String message, String sourcePath) {
        items.add(new ResourceDiagnostic("error", message, sourcePath, null, null));
    }
    public void collision(String message, String winnerPath, String loserPath) {
        items.add(new ResourceDiagnostic("collision", message, null, winnerPath, loserPath));
    }
    public List<ResourceDiagnostic> items() { return items; }
    public boolean hasErrors() { return items.stream().anyMatch(d -> "error".equals(d.type())); }
}
