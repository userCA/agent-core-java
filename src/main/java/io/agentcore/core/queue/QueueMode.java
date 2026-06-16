package io.agentcore.core.queue;

import com.fasterxml.jackson.annotation.JsonValue;

public enum QueueMode {
    ALL("all"),
    ONE_AT_A_TIME("one-at-a-time");

    private final String value;

    QueueMode(String value) { this.value = value; }

    @JsonValue
    public String getValue() { return value; }
}
