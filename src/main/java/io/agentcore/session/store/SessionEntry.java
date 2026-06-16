package io.agentcore.session.store;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Map;

public sealed interface SessionEntry permits
        SessionEntry.MessageEntry, SessionEntry.CompactionEntry,
        SessionEntry.ModelChangeEntry, SessionEntry.ThinkingLevelChangeEntry,
        SessionEntry.CustomEntry {
    String type();
    String id();

    record MessageEntry(Map<String, Object> message, String parentId, String id) implements SessionEntry {
        @Override public String type() { return "message"; }
    }
    record CompactionEntry(String summary, String firstKeptEntryId, int tokensBefore,
                           Object details, boolean fromExtension, String id) implements SessionEntry {
        @Override public String type() { return "compaction"; }
    }
    record ModelChangeEntry(String provider, String modelId, String id) implements SessionEntry {
        @Override public String type() { return "model_change"; }
    }
    record ThinkingLevelChangeEntry(String level, String id) implements SessionEntry {
        @Override public String type() { return "thinking_level_change"; }
    }
    record CustomEntry(String customType, Object data, String id) implements SessionEntry {
        @Override public String type() { return "custom"; }
    }
}
