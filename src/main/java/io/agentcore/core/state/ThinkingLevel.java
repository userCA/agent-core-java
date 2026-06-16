package io.agentcore.core.state;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ThinkingLevel {
    OFF("off"),
    MINIMAL("minimal"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    XHIGH("xhigh");

    private final String value;

    ThinkingLevel(String value) { this.value = value; }

    @JsonValue
    public String getValue() { return value; }

    public static ThinkingLevel fromValue(String v) {
        for (var level : values()) {
            if (level.value.equals(v)) return level;
        }
        return OFF;
    }
}
