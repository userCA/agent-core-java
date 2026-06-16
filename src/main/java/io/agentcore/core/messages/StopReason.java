package io.agentcore.core.messages;

import com.fasterxml.jackson.annotation.JsonValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum StopReason {
    STOP("stop"),
    TOOL_USE("tool_use"),
    LENGTH("length"),
    CONTENT_FILTER("content_filter"),
    ERROR("error"),
    ABORTED("aborted");

    private static final Logger log = LoggerFactory.getLogger(StopReason.class);

    private final String value;

    StopReason(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static StopReason fromValue(String v) {
        for (var r : values()) {
            if (r.value.equals(v)) return r;
        }
        log.warn("Unknown StopReason value '{}', defaulting to STOP", v);
        return STOP;
    }
}
