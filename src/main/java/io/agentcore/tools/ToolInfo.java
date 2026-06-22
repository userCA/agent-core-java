package io.agentcore.tools;

import java.util.Map;

/**
 * Lightweight tool info record for listing.
 *
 * <p>Mirrors Python {@code agent_core/tools/base.py} ToolInfo.
 */
public record ToolInfo(
        String name,
        String description,
        Map<String, Object> parameters,
        Object source
) {
    public ToolInfo {
        if (name == null) throw new IllegalArgumentException("name required");
        if (description == null) description = "";
        if (parameters == null) parameters = Map.of();
    }
}
