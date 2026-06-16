package io.agentcore.tools.base;

/**
 * Metadata about a registered tool, used for listing.
 */
public record ToolInfo(
        String name,
        String description,
        java.util.Map<String, Object> parameters,
        Object source
) {
    public ToolInfo(String name, String description, java.util.Map<String, Object> parameters) {
        this(name, description, parameters, null);
    }
}
