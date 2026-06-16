package io.agentcore.core.context;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ToolExecutionMode {
    PARALLEL("parallel"),
    SEQUENTIAL("sequential");

    private final String value;

    ToolExecutionMode(String value) { this.value = value; }

    @JsonValue
    public String getValue() { return value; }
}
