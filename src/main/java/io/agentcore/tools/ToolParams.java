package io.agentcore.tools;

import java.util.Map;

/**
 * Type-safe wrapper for tool execution parameters.
 *
 * <p>Eliminates boilerplate null-checks and type casts in every Tool implementation.
 * Wraps the raw {@code Map<String, Object>} from the LLM and provides
 * typed accessors with sensible defaults.
 *
 * <p>Usage in Tool implementations:
 * <pre>{@code
 * ToolParams p = new ToolParams(params);
 * String path = p.requireString("path");       // throws on null/blank
 * int offset = p.getInt("offset", 0);          // default 0
 * String size = p.getString("size", "1024x1024");
 * }</pre>
 */
public record ToolParams(Map<String, Object> raw) {

    public ToolParams {
        if (raw == null) raw = Map.of();
    }

    /**
     * Require a non-null, non-blank string parameter.
     *
     * @throws IllegalArgumentException if the parameter is missing or blank
     */
    public String requireString(String key) {
        Object v = raw.get(key);
        if (v == null || (v instanceof String s && s.isBlank())) {
            throw new IllegalArgumentException("'" + key + "' parameter is required");
        }
        return String.valueOf(v);
    }

    /**
     * Get a string parameter, or null if absent.
     */
    public String getString(String key) {
        Object v = raw.get(key);
        return v != null ? String.valueOf(v) : null;
    }

    /**
     * Get a string parameter with a default value.
     */
    public String getString(String key, String defaultValue) {
        Object v = raw.get(key);
        if (v == null) return defaultValue;
        String s = String.valueOf(v);
        return s.isEmpty() ? defaultValue : s;
    }

    /**
     * Get an integer parameter with a default value.
     */
    public int getInt(String key, int defaultValue) {
        Object v = raw.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) { }
        }
        return defaultValue;
    }

    /**
     * Get a double parameter with a default value.
     */
    public double getDouble(String key, double defaultValue) {
        Object v = raw.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException ignored) { }
        }
        return defaultValue;
    }

    /**
     * Get a boolean parameter with a default value.
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        Object v = raw.get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s);
        return defaultValue;
    }

    /**
     * Check if a parameter exists and is non-null.
     */
    public boolean has(String key) {
        return raw.containsKey(key) && raw.get(key) != null;
    }

    /**
     * Get a parameter as an unchecked map, or null if absent/wrong type.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getMap(String key) {
        Object v = raw.get(key);
        return v instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }
}
