package io.agentcore.session.jsonl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentcore.session.store.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * File-based session store using JSONL format.
 * One .jsonl file per session. Line 1 = header, subsequent lines = entries.
 */
public class JsonlStore implements SessionStore {
    private static final Logger log = LoggerFactory.getLogger(JsonlStore.class);

    private final Path directory;
    private final ObjectMapper mapper = new ObjectMapper();
    // Per-session locks for concurrent write protection
    private final ConcurrentHashMap<String, ReentrantLock> sessionLocks = new ConcurrentHashMap<>();

    public JsonlStore(String directory) {
        this.directory = Path.of(directory);
        try { Files.createDirectories(this.directory); } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    private static final java.util.regex.Pattern SESSION_ID_PATTERN =
            java.util.regex.Pattern.compile("[a-zA-Z0-9_-]+");

    private void validateSessionId(String sessionId) {
        if (sessionId == null || sessionId.isEmpty() || !SESSION_ID_PATTERN.matcher(sessionId).matches()) {
            throw new IllegalArgumentException(
                    "Invalid sessionId: must contain only [a-zA-Z0-9_-], got: " + sessionId);
        }
    }

    private Path sessionFile(String sessionId) {
        validateSessionId(sessionId);
        return directory.resolve(sessionId + ".jsonl");
    }

    @Override
    public CompletableFuture<Void> createSession(String sessionId, SessionHeader header) {
        return CompletableFuture.runAsync(() -> {
            ReentrantLock lock = sessionLocks.computeIfAbsent(sessionId, k -> new ReentrantLock());
            lock.lock();
            try {
                Path file = sessionFile(sessionId);
                Files.writeString(file, mapper.writeValueAsString(header) + "\n");
            } catch (IOException e) { throw new UncheckedIOException(e); }
            finally {
                lock.unlock();
                cleanLockIfUnused(sessionId, lock);
            }
        });
    }

    @Override
    public CompletableFuture<Void> appendEntry(String sessionId, SessionEntry entry) {
        return CompletableFuture.runAsync(() -> {
            ReentrantLock lock = sessionLocks.computeIfAbsent(sessionId, k -> new ReentrantLock());
            lock.lock();
            try {
                Path file = sessionFile(sessionId);
                Map<String, Object> map = entryToMap(entry);
                try (var writer = Files.newBufferedWriter(file, StandardOpenOption.APPEND)) {
                    writer.write(mapper.writeValueAsString(map));
                    writer.write('\n');
                }
            } catch (IOException e) { throw new UncheckedIOException(e); }
            finally {
                lock.unlock();
                cleanLockIfUnused(sessionId, lock);
            }
        });
    }

    /**
     * Remove the lock entry if no other thread is contending for it.
     * This prevents unbounded growth of the sessionLocks map.
     */
    private void cleanLockIfUnused(String sessionId, ReentrantLock lock) {
        if (!lock.hasQueuedThreads()) {
            sessionLocks.remove(sessionId, lock);
        }
    }

    @Override
    public CompletableFuture<SessionSnapshot> loadSession(String sessionId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path file = sessionFile(sessionId);
                if (!Files.exists(file)) throw new NoSuchElementException("Session not found: " + sessionId);

                // Stream lines instead of loading entire file to avoid OOM on large sessions
                SessionHeader[] headerHolder = new SessionHeader[1];
                List<SessionEntry> entries = new ArrayList<>();
                try (Stream<String> lines = Files.lines(file)) {
                    int[] lineIndex = {0};
                    lines.forEach(line -> {
                        if (lineIndex[0] == 0) {
                            try {
                                headerHolder[0] = mapper.readValue(line, SessionHeader.class);
                            } catch (Exception e) {
                                throw new UncheckedIOException(new IOException("Failed to parse session header", e));
                            }
                        } else if (!line.isBlank()) {
                            entries.add(deserializeEntry(line));
                        }
                        lineIndex[0]++;
                    });
                }
                if (headerHolder[0] == null) throw new NoSuchElementException("Empty session file: " + sessionId);
                return new SessionSnapshot(headerHolder[0], entries);
            } catch (IOException e) { throw new UncheckedIOException(e); }
        });
    }

    @Override
    public CompletableFuture<List<SessionMeta>> listSessions(String owner, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<SessionMeta> result = new ArrayList<>();
                try (var stream = Files.list(directory)) {
                    stream.filter(p -> p.toString().endsWith(".jsonl"))
                          .sorted(Comparator.reverseOrder())
                          .limit(limit)
                          .forEach(p -> {
                              try {
                                  List<String> lines = Files.readAllLines(p);
                                  if (!lines.isEmpty()) {
                                      SessionHeader h = mapper.readValue(lines.get(0), SessionHeader.class);
                                      result.add(new SessionMeta(h.id(), h.timestamp(), lines.size() - 1, ""));
                                  }
                              } catch (IOException e) {
                                  log.warn("Failed to read session file {}: {}", p, e.getMessage());
                              }
                          });
                }
                return result;
            } catch (IOException e) { return List.of(); }
        });
    }

    @Override
    public CompletableFuture<Void> close() {
        return CompletableFuture.completedFuture(null);
    }

    @SuppressWarnings("unchecked")
    private SessionEntry deserializeEntry(String json) {
        try {
            Map<String, Object> map = mapper.readValue(json, Map.class);
            String type = (String) map.get("type");
            String id = (String) map.getOrDefault("id", UUID.randomUUID().toString());
            return switch (type) {
                case "message" -> new SessionEntry.MessageEntry(
                        (Map<String, Object>) map.get("message"),
                        (String) map.get("parent_id"), id);
                case "compaction" -> new SessionEntry.CompactionEntry(
                        (String) map.get("summary"),
                        (String) map.get("first_kept_entry_id"),
                        ((Number) map.getOrDefault("tokens_before", 0)).intValue(),
                        map.get("details"),
                        Boolean.TRUE.equals(map.get("from_extension")), id);
                case "model_change" -> new SessionEntry.ModelChangeEntry(
                        (String) map.get("provider"),
                        (String) map.get("model_id"), id);
                case "thinking_level_change" -> new SessionEntry.ThinkingLevelChangeEntry(
                        (String) map.get("level"), id);
                default -> new SessionEntry.CustomEntry(
                        (String) map.get("custom_type"),
                        map.get("data"), id);
            };
        } catch (Exception e) {
            return new SessionEntry.CustomEntry("error", json, UUID.randomUUID().toString());
        }
    }

    private Map<String, Object> entryToMap(SessionEntry entry) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", entry.type());
        map.put("id", entry.id());
        if (entry instanceof SessionEntry.MessageEntry me) {
            map.put("message", me.message());
            map.put("parent_id", me.parentId());
        } else if (entry instanceof SessionEntry.CompactionEntry ce) {
            map.put("summary", ce.summary());
            map.put("first_kept_entry_id", ce.firstKeptEntryId());
            map.put("tokens_before", ce.tokensBefore());
            map.put("details", ce.details());
            map.put("from_extension", ce.fromExtension());
        } else if (entry instanceof SessionEntry.ModelChangeEntry mc) {
            map.put("provider", mc.provider());
            map.put("model_id", mc.modelId());
        } else if (entry instanceof SessionEntry.ThinkingLevelChangeEntry tl) {
            map.put("level", tl.level());
        } else if (entry instanceof SessionEntry.CustomEntry ce) {
            map.put("custom_type", ce.customType());
            map.put("data", ce.data());
        }
        return map;
    }
}
