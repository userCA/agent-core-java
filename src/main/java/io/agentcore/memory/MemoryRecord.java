package io.agentcore.memory;

import java.time.Instant;
import java.util.Map;

/**
 * A single memory record persisted by a {@link MemoryStore}.
 *
 * @param text      the remembered text content
 * @param sessionId the session this record belongs to
 * @param timestamp when the record was created
 * @param metadata  optional key-value metadata
 */
public record MemoryRecord(
        String text,
        String sessionId,
        Instant timestamp,
        Map<String, Object> metadata
) {
    public MemoryRecord(String text, String sessionId) {
        this(text, sessionId, Instant.now(), Map.of());
    }

    public MemoryRecord(String text, String sessionId, Map<String, Object> metadata) {
        this(text, sessionId, Instant.now(), metadata);
    }
}
