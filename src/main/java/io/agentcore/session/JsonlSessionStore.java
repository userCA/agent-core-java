package io.agentcore.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * JSONL file-based session store.
 *
 * <p>Mirrors Python {@code agent_core/session/jsonl_store.py} JsonlStore.
 * Each session is stored as a .jsonl file with one JSON object per line.
 */
public class JsonlSessionStore implements SessionStore {

    private static final Logger log = LoggerFactory.getLogger(JsonlSessionStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

    private final Path directory;

    public JsonlSessionStore(String directory) throws IOException {
        this.directory = Path.of(directory);
        Files.createDirectories(this.directory);
    }

    public JsonlSessionStore(Path directory) throws IOException {
        this.directory = directory;
        Files.createDirectories(this.directory);
    }

    private Path sessionPath(String sessionId) {
        if (sessionId.contains("/") || sessionId.contains("\\") || sessionId.contains("..")) {
            throw new IllegalArgumentException("Invalid session ID: " + sessionId);
        }
        return directory.resolve(sessionId + ".jsonl");
    }

    @Override
    public void createSession(String sessionId, SessionHeader header) {
        try {
            Map<String, Object> headerMap = new LinkedHashMap<>();
            headerMap.put("type", "session");
            headerMap.put("version", header.version());
            headerMap.put("id", header.id());
            headerMap.put("timestamp", header.timestamp());
            headerMap.put("cwd", header.cwd());

            String line = MAPPER.writeValueAsString(headerMap) + "\n";
            Files.writeString(sessionPath(sessionId), line,
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create session " + sessionId, e);
        }
    }

    @Override
    public void appendEntry(String sessionId, SessionEntry entry) {
        try {
            Map<String, Object> entryMap = entryToMap(entry);
            String line = MAPPER.writeValueAsString(entryMap) + "\n";
            Files.writeString(sessionPath(sessionId), line,
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new RuntimeException("Failed to append entry to session " + sessionId, e);
        }
    }

    @Override
    public SessionSnapshot loadSession(String sessionId) {
        Path path = sessionPath(sessionId);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Session " + sessionId + " not found");
        }

        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            if (lines.isEmpty()) {
                throw new RuntimeException("Session file is empty");
            }

            // Parse header
            @SuppressWarnings("unchecked")
            Map<String, Object> headerData = MAPPER.readValue(lines.get(0), Map.class);
            SessionHeader header = new SessionHeader(
                    (String) headerData.get("id"),
                    (String) headerData.get("timestamp"),
                    (String) headerData.getOrDefault("cwd", ""),
                    (String) headerData.getOrDefault("version", "1")
            );

            // Parse entries
            List<SessionEntry> entries = new ArrayList<>();
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i).strip();
                if (line.isEmpty()) continue;
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = MAPPER.readValue(line, Map.class);
                    entries.add(deserializeEntry(data));
                } catch (Exception e) {
                    log.debug("Skip malformed session entry in {}", sessionId, e);
                }
            }

            return new SessionSnapshot(header, entries);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load session " + sessionId, e);
        }
    }

    @Override
    public List<SessionMeta> listSessions(String owner, int limit) {
        List<SessionMeta> result = new ArrayList<>();
        try (Stream<Path> files = Files.list(directory)) {
            List<Path> jsonlFiles = files
                    .filter(p -> p.toString().endsWith(".jsonl"))
                    .sorted(Comparator.reverseOrder())
                    .toList();

            for (Path file : jsonlFiles) {
                if (result.size() >= limit) break;
                try {
                    String sessionId = file.getFileName().toString().replace(".jsonl", "");
                    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                    if (lines.isEmpty()) continue;

                    @SuppressWarnings("unchecked")
                    Map<String, Object> headerData = MAPPER.readValue(lines.get(0), Map.class);
                    String timestamp = (String) headerData.getOrDefault("timestamp", "");

                    // Extract title from first user message
                    String title = extractTitle(lines);

                    result.add(new SessionMeta(sessionId, timestamp, lines.size() - 1, title));
                } catch (Exception e) {
                    log.debug("Skip malformed session file {}", file.getFileName(), e);
                }
            }
        } catch (IOException e) {
            // Return empty list
        }
        return result;
    }

    @Override
    public void close() {
        // No-op for file store
    }

    // ── Helpers ──────────────────────────────────────────────

    private String extractTitle(List<String> lines) {
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty()) continue;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = MAPPER.readValue(line, Map.class);
                if ("message".equals(data.get("type"))) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> msg = (Map<String, Object>) data.get("message");
                    if (msg != null && "user".equals(msg.get("role"))) {
                        Object content = msg.get("content");
                        String text = extractText(content);
                        if (!text.isEmpty()) {
                            return text.length() > 30 ? text.substring(0, 30) + "..." : text;
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Skip malformed entry while extracting title", e);
                continue;
            }
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private static String extractText(Object content) {
        if (content instanceof String s) return s;
        if (content instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object c : list) {
                if (c instanceof Map<?, ?> map && "text".equals(map.get("type"))) {
                    sb.append(((Map<String, Object>) map).getOrDefault("text", ""));
                } else if (c instanceof String s) {
                    sb.append(s);
                }
            }
            return sb.toString();
        }
        return "";
    }

    private static Map<String, Object> entryToMap(SessionEntry entry) {
        Map<String, Object> map = new LinkedHashMap<>();
        switch (entry) {
            case SessionEntry.MessageEntry me -> {
                map.put("type", "message");
                map.put("id", me.id());
                map.put("message", me.message());
                if (me.parentId() != null) map.put("parent_id", me.parentId());
            }
            case SessionEntry.CompactionEntry ce -> {
                map.put("type", "compaction");
                map.put("id", ce.id());
                map.put("summary", ce.summary());
                map.put("first_kept_entry_id", ce.firstKeptEntryId());
                map.put("tokens_before", ce.tokensBefore());
                if (ce.details() != null) map.put("details", ce.details());
                map.put("from_extension", ce.fromExtension());
            }
            case SessionEntry.ModelChangeEntry mce -> {
                map.put("type", "model_change");
                map.put("id", mce.id());
                map.put("provider", mce.provider());
                map.put("model_id", mce.modelId());
            }
            case SessionEntry.ThinkingLevelChangeEntry tlce -> {
                map.put("type", "thinking_level_change");
                map.put("id", tlce.id());
                map.put("level", tlce.level());
            }
            case SessionEntry.CustomEntry ce -> {
                map.put("type", "custom");
                map.put("id", ce.id());
                map.put("custom_type", ce.customType());
                map.put("data", ce.data());
            }
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private static SessionEntry deserializeEntry(Map<String, Object> data) {
        String type = (String) data.get("type");
        String id = (String) data.getOrDefault("id", "");
        return switch (type) {
            case "message" -> new SessionEntry.MessageEntry(
                    id, data.get("message"), (String) data.get("parent_id"));
            case "compaction" -> new SessionEntry.CompactionEntry(
                    id,
                    (String) data.getOrDefault("summary", ""),
                    (String) data.getOrDefault("first_kept_entry_id", ""),
                    data.get("tokens_before") instanceof Number n ? n.intValue() : 0,
                    data.get("details"),
                    Boolean.TRUE.equals(data.get("from_extension")));
            case "model_change" -> new SessionEntry.ModelChangeEntry(
                    id,
                    (String) data.getOrDefault("provider", ""),
                    (String) data.getOrDefault("model_id", ""));
            case "thinking_level_change" -> new SessionEntry.ThinkingLevelChangeEntry(
                    id,
                    (String) data.getOrDefault("level", ""));
            case "custom" -> new SessionEntry.CustomEntry(
                    id,
                    (String) data.getOrDefault("custom_type", ""),
                    data.get("data"));
            default -> new SessionEntry.CustomEntry(id, "unknown", data);
        };
    }
}
