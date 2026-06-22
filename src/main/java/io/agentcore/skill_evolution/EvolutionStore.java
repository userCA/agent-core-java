package io.agentcore.skill_evolution;

import io.agentcore.skill_evolution.EvolutionTypes.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Skill Evolution Memory Store — persistent storage for execution traces.
 *
 * <p>Mirrors Python {@code agent_core/skill_evolution/store.py}.
 * Write-optimized append-only log that can be queried by various criteria.
 */
public final class EvolutionStore {

    private static final Logger log = LoggerFactory.getLogger(EvolutionStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EvolutionStore() {}

    // ── Interface ────────────────────────────────────────────────────

    public interface Store {
        void saveTrace(SkillEvolutionTrace trace);
        List<SkillEvolutionTrace> getTraces(String skillName, String outcome, int limit, int offset);
        int getTraceCount(String skillName, String outcome);
        int deleteOldTraces(int olderThanDays);
    }

    // ── In-Memory implementation ─────────────────────────────────────

    public static final class InMemoryStore implements Store {
        private final List<SkillEvolutionTrace> traces = new ArrayList<>();

        @Override
        public void saveTrace(SkillEvolutionTrace trace) {
            traces.add(trace);
        }

        @Override
        public List<SkillEvolutionTrace> getTraces(String skillName, String outcome, int limit, int offset) {
            List<SkillEvolutionTrace> filtered = traces.stream()
                    .filter(t -> skillName == null || t.skillName().equals(skillName))
                    .filter(t -> outcome == null || t.executionOutcome().getValue().equals(outcome))
                    .sorted(Comparator.comparingDouble(SkillEvolutionTrace::timestamp).reversed())
                    .toList();
            int from = Math.min(offset, filtered.size());
            int to = Math.min(from + limit, filtered.size());
            return filtered.subList(from, to);
        }

        @Override
        public int getTraceCount(String skillName, String outcome) {
            return (int) traces.stream()
                    .filter(t -> skillName == null || t.skillName().equals(skillName))
                    .filter(t -> outcome == null || t.executionOutcome().getValue().equals(outcome))
                    .count();
        }

        @Override
        public int deleteOldTraces(int olderThanDays) {
            double cutoff = System.currentTimeMillis() / 1000.0 - (olderThanDays * 86400.0);
            int before = traces.size();
            traces.removeIf(t -> t.timestamp() < cutoff);
            return before - traces.size();
        }
    }

    // ── JSONL implementation ─────────────────────────────────────────

    public static final class JsonlStore implements Store {
        private final Path storagePath;

        public JsonlStore(String path) {
            this.storagePath = Path.of(path.replaceFirst("^~", System.getProperty("user.home")));
            try {
                Files.createDirectories(storagePath.getParent());
            } catch (IOException e) {
                log.warn("Failed to create store directory", e);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public void saveTrace(SkillEvolutionTrace trace) {
            try {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("trace_id", trace.traceId());
                data.put("timestamp", trace.timestamp());
                data.put("session_id", trace.sessionId());
                data.put("user_query", trace.userQuery());
                data.put("skill_name", trace.skillName());
                data.put("loaded_rules", trace.loadedRules());
                data.put("execution_outcome", trace.executionOutcome().getValue());
                data.put("execution_details", trace.executionDetails());
                data.put("new_rules_discovered", trace.newRulesDiscovered());
                data.put("user_feedback", trace.userFeedback());
                data.put("regression_info", trace.regressionInfo());
                Files.writeString(storagePath,
                        MAPPER.writeValueAsString(data) + "\n",
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                log.warn("Failed to save trace", e);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public List<SkillEvolutionTrace> getTraces(String skillName, String outcome, int limit, int offset) {
            List<SkillEvolutionTrace> all = readAllTraces();
            List<SkillEvolutionTrace> filtered = all.stream()
                    .filter(t -> skillName == null || t.skillName().equals(skillName))
                    .filter(t -> outcome == null || t.executionOutcome().getValue().equals(outcome))
                    .sorted(Comparator.comparingDouble(SkillEvolutionTrace::timestamp).reversed())
                    .toList();
            int from = Math.min(offset, filtered.size());
            int to = Math.min(from + limit, filtered.size());
            return filtered.subList(from, to);
        }

        @Override
        public int getTraceCount(String skillName, String outcome) {
            return (int) readAllTraces().stream()
                    .filter(t -> skillName == null || t.skillName().equals(skillName))
                    .filter(t -> outcome == null || t.executionOutcome().getValue().equals(outcome))
                    .count();
        }

        @Override
        public int deleteOldTraces(int olderThanDays) {
            double cutoff = System.currentTimeMillis() / 1000.0 - (olderThanDays * 86400.0);
            List<SkillEvolutionTrace> all = readAllTraces();
            int before = all.size();
            List<SkillEvolutionTrace> recent = all.stream()
                    .filter(t -> t.timestamp() >= cutoff).toList();
            // Rewrite file
            try {
                StringBuilder sb = new StringBuilder();
                for (SkillEvolutionTrace t : recent) {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("trace_id", t.traceId());
                    data.put("timestamp", t.timestamp());
                    data.put("session_id", t.sessionId());
                    data.put("user_query", t.userQuery());
                    data.put("skill_name", t.skillName());
                    data.put("loaded_rules", t.loadedRules());
                    data.put("execution_outcome", t.executionOutcome().getValue());
                    data.put("execution_details", t.executionDetails());
                    data.put("new_rules_discovered", t.newRulesDiscovered());
                    data.put("user_feedback", t.userFeedback());
                    data.put("regression_info", t.regressionInfo());
                    sb.append(MAPPER.writeValueAsString(data)).append("\n");
                }
                Files.writeString(storagePath, sb.toString(),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                log.warn("Failed to rewrite store", e);
            }
            return before - recent.size();
        }

        @SuppressWarnings("unchecked")
        private List<SkillEvolutionTrace> readAllTraces() {
            if (!Files.exists(storagePath)) return List.of();
            List<SkillEvolutionTrace> traces = new ArrayList<>();
            try {
                for (String line : Files.readAllLines(storagePath)) {
                    if (line.isBlank()) continue;
                    Map<String, Object> data = MAPPER.readValue(line, Map.class);
                    traces.add(new SkillEvolutionTrace(
                            (String) data.get("trace_id"),
                            ((Number) data.getOrDefault("timestamp", 0)).doubleValue(),
                            (String) data.get("session_id"),
                            (String) data.getOrDefault("user_query", ""),
                            (String) data.getOrDefault("skill_name", ""),
                            (List<String>) data.getOrDefault("loaded_rules", List.of()),
                            ExecutionOutcome.fromString((String) data.getOrDefault("execution_outcome", "success")),
                            (Map<String, Object>) data.getOrDefault("execution_details", Map.of()),
                            (List<Map<String, Object>>) data.getOrDefault("new_rules_discovered", List.of()),
                            (String) data.get("user_feedback"),
                            (Map<String, Object>) data.getOrDefault("regression_info", Map.of())));
                }
            } catch (IOException e) {
                log.warn("Failed to read traces", e);
            }
            return traces;
        }
    }

    // ── Factory ──────────────────────────────────────────────────────

    public static Store create(String type, String path) {
        if ("memory".equals(type)) return new InMemoryStore();
        if ("jsonl".equals(type)) {
            String p = path != null ? path : "~/.agent-core/skill-evolution-traces.jsonl";
            return new JsonlStore(p);
        }
        throw new IllegalArgumentException("Unknown store type: " + type);
    }

    public static Store createInMemory() {
        return new InMemoryStore();
    }
}
