package io.agentcore.resources;

/**
 * Diagnostic emitted during resource loading.
 *
 * @param type        severity: WARNING, ERROR, or COLLISION
 * @param message     human-readable description
 * @param sourcePath  file path that triggered the diagnostic (may be null)
 * @param winnerPath  for COLLISION: the path that was kept (may be null)
 * @param loserPath   for COLLISION: the path that was discarded (may be null)
 */
public record ResourceDiagnostic(
        Type type,
        String message,
        String sourcePath,
        String winnerPath,
        String loserPath
) {
    public enum Type { WARNING, ERROR, COLLISION }

    public static ResourceDiagnostic warning(String message, String sourcePath) {
        return new ResourceDiagnostic(Type.WARNING, message, sourcePath, null, null);
    }

    public static ResourceDiagnostic error(String message, String sourcePath) {
        return new ResourceDiagnostic(Type.ERROR, message, sourcePath, null, null);
    }

    public static ResourceDiagnostic collision(String message, String winnerPath, String loserPath) {
        return new ResourceDiagnostic(Type.COLLISION, message, null, winnerPath, loserPath);
    }
}
