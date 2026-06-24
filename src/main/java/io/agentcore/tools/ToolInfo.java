package io.agentcore.tools;

import java.util.Map;

/**
 * Lightweight tool info record for listing.
 *
 * <p>Mirrors Python {@code agent_core/tools/base.py} ToolInfo.
 *
 * @param source typed provenance of the tool (builtin, MCP, extension, external)
 */
public record ToolInfo(
        String name,
        String description,
        Map<String, Object> parameters,
        ToolSource source
) {
    public ToolInfo {
        if (name == null) throw new IllegalArgumentException("name required");
        if (description == null) description = "";
        if (parameters == null) parameters = Map.of();
        if (source == null) source = ToolSource.Builtin.INSTANCE;
    }
}
