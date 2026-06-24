package io.agentcore.skill.evolution;

import java.util.*;

/**
 * Skill Self-Evolution Type Definitions.
 *
 * <p>Mirrors Python {@code agent_core/skill_evolution/types.py}.
 * Defines data structures for tracking skill execution traces
 * and proposing evolution updates.
 */
public final class EvolutionTypes {

    private EvolutionTypes() {}

    /** Result of a skill-guided task execution. */
    public enum ExecutionOutcome {
        SUCCESS("success"),
        PARTIAL("partial"),
        FAILURE("failure"),
        REGRESSION("regression");

        private final String value;
        ExecutionOutcome(String value) { this.value = value; }
        public String getValue() { return value; }

        public static ExecutionOutcome fromString(String s) {
            for (ExecutionOutcome o : values()) {
                if (o.value.equals(s)) return o;
            }
            return SUCCESS;
        }
    }

    /** A complete trace of a single skill invocation. */
    public record SkillEvolutionTrace(
            String traceId,
            double timestamp,
            String sessionId,
            String userQuery,
            String skillName,
            List<String> loadedRules,
            ExecutionOutcome executionOutcome,
            Map<String, Object> executionDetails,
            List<Map<String, Object>> newRulesDiscovered,
            String userFeedback,
            Map<String, Object> regressionInfo
    ) {
        public SkillEvolutionTrace {
            if (traceId == null || traceId.isBlank()) throw new IllegalArgumentException("traceId required");
            if (timestamp <= 0) timestamp = System.currentTimeMillis() / 1000.0;
            if (loadedRules == null) loadedRules = List.of();
            if (executionOutcome == null) executionOutcome = ExecutionOutcome.SUCCESS;
            if (executionDetails == null) executionDetails = Map.of();
            if (newRulesDiscovered == null) newRulesDiscovered = List.of();
            if (regressionInfo == null) regressionInfo = Map.of();
        }

        /** Convenience builder with minimal fields. */
        public static SkillEvolutionTrace create(String traceId, String skillName) {
            return new SkillEvolutionTrace(traceId, 0, null, "", skillName,
                    List.of(), ExecutionOutcome.SUCCESS, Map.of(), List.of(), null, Map.of());
        }

        public SkillEvolutionTrace withOutcome(ExecutionOutcome outcome) {
            return new SkillEvolutionTrace(traceId, timestamp, sessionId, userQuery,
                    skillName, loadedRules, outcome, executionDetails,
                    newRulesDiscovered, userFeedback, regressionInfo);
        }

        public SkillEvolutionTrace withDetails(Map<String, Object> details) {
            return new SkillEvolutionTrace(traceId, timestamp, sessionId, userQuery,
                    skillName, loadedRules, executionOutcome, details,
                    newRulesDiscovered, userFeedback, regressionInfo);
        }

        public SkillEvolutionTrace markSuccess(Map<String, Object> details) {
            Map<String, Object> merged = new LinkedHashMap<>(executionDetails);
            if (details != null) merged.putAll(details);
            return new SkillEvolutionTrace(traceId, timestamp, sessionId, userQuery,
                    skillName, loadedRules, ExecutionOutcome.SUCCESS, merged,
                    newRulesDiscovered, userFeedback, regressionInfo);
        }

        public SkillEvolutionTrace markFailure(String error, Map<String, Object> details) {
            Map<String, Object> merged = new LinkedHashMap<>(executionDetails);
            merged.put("error", error);
            if (details != null) merged.putAll(details);
            return new SkillEvolutionTrace(traceId, timestamp, sessionId, userQuery,
                    skillName, loadedRules, ExecutionOutcome.FAILURE, merged,
                    newRulesDiscovered, userFeedback, regressionInfo);
        }
    }

    /** A proposed change to a skill's rules. */
    public record PatchProposal(
            String proposalId,
            List<String> sourceTraces,
            String skillName,
            String operation,       // "add" | "modify" | "delete" | "merge"
            String targetRuleId,
            String newContent,
            String rationale,
            double confidence,
            List<String> supportingEvidence
    ) {
        public PatchProposal {
            if (proposalId == null || proposalId.isBlank()) throw new IllegalArgumentException("proposalId required");
            if (sourceTraces == null) sourceTraces = List.of();
            if (skillName == null || skillName.isBlank()) throw new IllegalArgumentException("skillName required");
            if (operation == null || operation.isBlank()) throw new IllegalArgumentException("operation required");
            if (rationale == null) rationale = "";
            if (confidence < 0) confidence = 0;
            if (confidence > 1) confidence = 1;
            if (supportingEvidence == null) supportingEvidence = List.of();
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("proposal_id", proposalId);
            m.put("source_traces", sourceTraces);
            m.put("skill_name", skillName);
            m.put("operation", operation);
            m.put("target_rule_id", targetRuleId);
            m.put("new_content", newContent);
            m.put("rationale", rationale);
            m.put("confidence", confidence);
            m.put("supporting_evidence", supportingEvidence);
            return m;
        }
    }

    /** Result of validating a proposed skill change. */
    public record ValidationResult(
            String proposalId,
            double scoreDelta,
            boolean passed,
            List<TestResultEntry> testResults,
            List<String> failedCases,
            String recommendation   // "accept" | "reject" | "needs_review"
    ) {
        public ValidationResult {
            if (testResults == null) testResults = List.of();
            if (failedCases == null) failedCases = List.of();
            if (recommendation == null || recommendation.isBlank()) {
                if (passed && scoreDelta >= 0.05) recommendation = "accept";
                else if (scoreDelta < -0.05) recommendation = "reject";
                else recommendation = "needs_review";
            }
        }

        public record TestResultEntry(String testId, boolean passed, String message) {}
    }

    /** Result of merging multiple proposals from parallel analysis. */
    public static final class MergedProposal {
        private final List<PatchProposal> mergedProposals = new ArrayList<>();
        private final List<List<PatchProposal>> conflicts = new ArrayList<>();
        private final List<PatchProposal> discarded = new ArrayList<>();
        private String mergeRationale = "";

        public List<PatchProposal> mergedProposals() { return mergedProposals; }
        public List<List<PatchProposal>> conflicts() { return conflicts; }
        public List<PatchProposal> discarded() { return discarded; }
        public String mergeRationale() { return mergeRationale; }
        public void setMergeRationale(String r) { this.mergeRationale = r != null ? r : ""; }
    }

    /** Summary of a complete evolution cycle. */
    public record EvolutionSummary(
            String cycleId,
            double timestamp,
            int tracesAnalyzed,
            int proposalsGenerated,
            int proposalsAccepted,
            int proposalsRejected,
            List<String> skillsUpdated,
            Map<String, Double> performanceImpact
    ) {
        public EvolutionSummary {
            if (timestamp <= 0) timestamp = System.currentTimeMillis() / 1000.0;
            if (skillsUpdated == null) skillsUpdated = List.of();
            if (performanceImpact == null) performanceImpact = Map.of();
        }
    }
}
