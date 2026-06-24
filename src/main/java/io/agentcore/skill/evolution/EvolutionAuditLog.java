package io.agentcore.skill.evolution;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Skill evolution audit log — append-only JSONL record of accept/reject decisions.
 *
 * <p>Mirrors Python {@code agent_core/skill_evolution/audit.py}.
 */
public final class EvolutionAuditLog {

    private static final Logger log = LoggerFactory.getLogger(EvolutionAuditLog.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path storagePath;

    public EvolutionAuditLog(Path storagePath) {
        this.storagePath = storagePath;
        try {
            Files.createDirectories(storagePath.getParent());
        } catch (IOException e) {
            log.warn("Failed to create audit directory", e);
        }
    }

    public EvolutionAuditLog(String path) {
        this(Path.of(path.replaceFirst("^~", System.getProperty("user.home"))));
    }

    /**
     * An audit entry record.
     */
    public record AuditEntry(
            String auditId,
            double timestamp,
            String proposalId,
            String skillName,
            String action,          // "accept" | "reject"
            String operation,
            String targetRuleId,
            String diffSummary,
            String rationale,
            double validationScore,
            String rejectReason
    ) {
        public AuditEntry {
            if (auditId == null || auditId.isBlank()) auditId = UUID.randomUUID().toString();
            if (timestamp <= 0) timestamp = System.currentTimeMillis() / 1000.0;
            if (operation == null) operation = "";
            if (diffSummary == null) diffSummary = "";
            if (rationale == null) rationale = "";
            if (rejectReason == null) rejectReason = "";
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("audit_id", auditId);
            m.put("timestamp", timestamp);
            m.put("proposal_id", proposalId);
            m.put("skill_name", skillName);
            m.put("action", action);
            m.put("operation", operation);
            m.put("target_rule_id", targetRuleId);
            m.put("diff_summary", diffSummary.length() > 200 ? diffSummary.substring(0, 200) : diffSummary);
            m.put("rationale", rationale.length() > 500 ? rationale.substring(0, 500) : rationale);
            m.put("validation_score", validationScore);
            m.put("reject_reason", rejectReason);
            return m;
        }
    }

    /**
     * Write an audit entry and return its audit_id.
     */
    public String writeAuditEntry(AuditEntry entry) {
        try {
            String line = MAPPER.writeValueAsString(entry.toMap()) + "\n";
            Files.writeString(storagePath, line,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("Failed to write audit entry", e);
        }
        return entry.auditId();
    }

    /**
     * Builder for creating audit entries fluently.
     */
    public String writeAuditEntry(String proposalId, String skillName, String action,
                                   String operation, String targetRuleId,
                                   String diffSummary, String rationale,
                                   double validationScore, String rejectReason) {
        AuditEntry entry = new AuditEntry(
                null, 0, proposalId, skillName, action,
                operation, targetRuleId, diffSummary, rationale,
                validationScore, rejectReason);
        return writeAuditEntry(entry);
    }

    /**
     * Read recent audit entries, optionally filtered by skill.
     */
    @SuppressWarnings("unchecked")
    public List<AuditEntry> readAuditLog(String skillName, int limit) {
        if (!Files.exists(storagePath)) return List.of();

        List<AuditEntry> entries = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(storagePath, StandardCharsets.UTF_8)) {
                line = line.strip();
                if (line.isEmpty()) continue;
                try {
                    Map<String, Object> data = MAPPER.readValue(line, Map.class);
                    AuditEntry entry = new AuditEntry(
                            (String) data.getOrDefault("audit_id", ""),
                            ((Number) data.getOrDefault("timestamp", 0)).doubleValue(),
                            (String) data.getOrDefault("proposal_id", ""),
                            (String) data.getOrDefault("skill_name", ""),
                            (String) data.getOrDefault("action", ""),
                            (String) data.getOrDefault("operation", ""),
                            (String) data.get("target_rule_id"),
                            (String) data.getOrDefault("diff_summary", ""),
                            (String) data.getOrDefault("rationale", ""),
                            data.get("validation_score") instanceof Number n ? n.doubleValue() : 0.0,
                            (String) data.get("reject_reason")
                    );
                    if (skillName != null && !skillName.equals(entry.skillName())) continue;
                    entries.add(entry);
                } catch (Exception e) {
                    log.debug("Skip malformed audit entry", e);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read audit log", e);
        }

        // Sort by timestamp descending, limit
        entries.sort(Comparator.comparingDouble(AuditEntry::timestamp).reversed());
        return entries.subList(0, Math.min(entries.size(), limit));
    }

    /**
     * Read all audit entries.
     */
    public List<AuditEntry> readAuditLog() {
        return readAuditLog(null, 50);
    }

    /**
     * Read audit entries for a specific skill.
     */
    public List<AuditEntry> readAuditLog(String skillName) {
        return readAuditLog(skillName, 50);
    }
}
