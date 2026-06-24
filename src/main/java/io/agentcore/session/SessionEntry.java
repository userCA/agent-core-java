package io.agentcore.session;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Map;

/**
 * Session entry types stored in the session log.
 *
 * <p>Mirrors Python {@code agent_core/session/store.py} SessionEntry union.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = SessionEntry.MessageEntry.class, name = "message"),
    @JsonSubTypes.Type(value = SessionEntry.CompactionEntry.class, name = "compaction"),
    @JsonSubTypes.Type(value = SessionEntry.ModelChangeEntry.class, name = "model_change"),
    @JsonSubTypes.Type(value = SessionEntry.ThinkingLevelChangeEntry.class, name = "thinking_level_change"),
    @JsonSubTypes.Type(value = SessionEntry.CustomEntry.class, name = "custom"),
})
public sealed interface SessionEntry {

    String id();

    record MessageEntry(
        String id,
        Map<String, Object> message,
        String parentId
    ) implements SessionEntry {}

    record CompactionEntry(
        String id,
        String summary,
        String firstKeptEntryId,
        int tokensBefore,
        Object details,
        boolean fromExtension
    ) implements SessionEntry {
        public CompactionEntry(String id, String summary, String firstKeptEntryId, int tokensBefore) {
            this(id, summary, firstKeptEntryId, tokensBefore, null, false);
        }
    }

    record ModelChangeEntry(
        String id,
        String provider,
        String modelId
    ) implements SessionEntry {}

    record ThinkingLevelChangeEntry(
        String id,
        String level
    ) implements SessionEntry {}

    record CustomEntry(
        String id,
        String customType,
        Object data
    ) implements SessionEntry {}
}
