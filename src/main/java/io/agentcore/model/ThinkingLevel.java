package io.agentcore.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Thinking levels for model reasoning.
 *
 * <p>Represents the reasoning effort / depth for extended thinking models.
 * Used by provider implementations to configure model behavior.
 */
public enum ThinkingLevel {
    OFF("off"),
    MINIMAL("minimal"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    XHIGH("xhigh");

    private static final Map<String, ThinkingLevel> LOOKUP;
    static {
        Map<String, ThinkingLevel> m = new HashMap<>();
        for (ThinkingLevel level : values()) {
            m.put(level.value.toLowerCase(), level);
        }
        LOOKUP = Map.copyOf(m);
    }

    private final String value;

    ThinkingLevel(String value) {
        this.value = value;
    }

    /** Returns the string value used by providers. */
    public String value() {
        return value;
    }

    /**
     * Parse a string to ThinkingLevel (case-insensitive).
     * @return the matching level, or {@link #OFF} if not recognized
     */
    public static ThinkingLevel fromValue(String s) {
        if (s == null) return OFF;
        ThinkingLevel level = LOOKUP.get(s.toLowerCase());
        return level != null ? level : OFF;
    }

    @Override
    public String toString() {
        return value;
    }
}
