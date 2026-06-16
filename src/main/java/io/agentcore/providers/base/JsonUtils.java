package io.agentcore.providers.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Shared JSON utility methods for provider implementations.
 */
public final class JsonUtils {
    private static final Logger log = LoggerFactory.getLogger(JsonUtils.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonUtils() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseJson(String json) {
        try {
            return MAPPER.readValue(json, Map.class);
        } catch (Exception e) {
            log.debug("JSON parse failed: {}", e.getMessage());
            return Map.of();
        }
    }

    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("JSON serialization failed: {}", e.getMessage());
            return "{}";
        }
    }

    public static int toInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        return 0;
    }
}
