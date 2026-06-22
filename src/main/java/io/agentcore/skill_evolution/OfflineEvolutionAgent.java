package io.agentcore.skill_evolution;

import io.agentcore.skill_evolution.EvolutionTypes.*;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Offline Skill Evolution Agent — analyzes trace batches and proposes improvements.
 *
 * <p>Mirrors Python {@code agent_core/skill_evolution/agent.py}.
 * Uses heuristic analysis (LLM integration is optional/future work).
 *
 * <p>Architecture:
 * <ol>
 *   <li>Parallel Proposal Phase: success/failure traces analyzed independently</li>
 *   <li>Hierarchical Merge Phase: combine proposals, resolve conflicts, discard noise</li>
 * </ol>
 */
public final class OfflineEvolutionAgent {

    private static final Logger log = LoggerFactory.getLogger(OfflineEvolutionAgent.class);

    private final EvolutionStore.Store store;
    private final int batchSize;
    private final int maxProposals;
    private final Set<String> analyzedTraceIds = new HashSet<>();

    public OfflineEvolutionAgent(EvolutionStore.Store store) {
        this(store, 100, 5);
    }

    public OfflineEvolutionAgent(EvolutionStore.Store store, int batchSize, int maxProposals) {
        this.store = store;
        this.batchSize = batchSize;
        this.maxProposals = maxProposals;
    }

    // ── Cycle result ──────────────────────────────────────────────────

    public record CycleResult(
            String status,
            String reason,
            String cycleId,
            String skillName,
            int tracesAnalyzed,
            int successTraces,
            int failureTraces,
            int proposalsGenerated,
            int proposalsAccepted,
            int conflicts,
            int discarded,
            List<PatchProposal> finalProposals,
            String mergeRationale
    ) {
        public static CycleResult skipped(String reason, int traceCount) {
            return new CycleResult("skipped", reason, null, null,
                    traceCount, 0, 0, 0, 0, 0, 0, List.of(), "");
        }

        public static CycleResult completed(String cycleId, String skillName,
                                             int analyzed, int success, int failure,
                                             int generated, int accepted,
                                             int conflicts, int discarded,
                                             List<PatchProposal> proposals,
                                             String rationale) {
            return new CycleResult("completed", null, cycleId, skillName,
                    analyzed, success, failure, generated, accepted,
                    conflicts, discarded, proposals, rationale);
        }
    }

    // ── Public API ────────────────────────────────────────────────────

    /**
     * Run a complete evolution cycle for a specific skill.
     */
    public CycleResult runEvolutionCycle(String skillName, int minTraces) {
        log.info("[EvolutionAgent] Starting cycle for skill: {}", skillName);

        // Step 1: Fetch traces
        int traceCount = store.getTraceCount(skillName, null);
        if (traceCount < minTraces) {
            log.info("[EvolutionAgent] Insufficient traces ({} < {}), skipping", traceCount, minTraces);
            return CycleResult.skipped("insufficient_traces", traceCount);
        }

        List<SkillEvolutionTrace> traces = store.getTraces(skillName, null, batchSize, 0);

        // Filter out already-analyzed traces
        List<SkillEvolutionTrace> newTraces = traces.stream()
                .filter(t -> !analyzedTraceIds.contains(t.traceId()))
                .toList();

        if (newTraces.isEmpty()) {
            return CycleResult.skipped("no_new_traces", traces.size());
        }

        log.info("[EvolutionAgent] Analyzing {} new traces for {}", newTraces.size(), skillName);

        // Mark as analyzed
        for (SkillEvolutionTrace t : newTraces) {
            analyzedTraceIds.add(t.traceId());
        }

        // Step 2: Separate success/failure traces
        List<SkillEvolutionTrace> successTraces = newTraces.stream()
                .filter(t -> t.executionOutcome() == ExecutionOutcome.SUCCESS)
                .toList();
        List<SkillEvolutionTrace> failureTraces = newTraces.stream()
                .filter(t -> t.executionOutcome() == ExecutionOutcome.FAILURE
                        || t.executionOutcome() == ExecutionOutcome.PARTIAL)
                .toList();

        log.info("[EvolutionAgent] Split: {} success, {} failure", successTraces.size(), failureTraces.size());

        // Step 3: Generate proposals
        List<PatchProposal> proposals = generateProposals(successTraces, failureTraces);
        log.info("[EvolutionAgent] Generated {} raw proposals", proposals.size());

        if (proposals.isEmpty()) {
            return new CycleResult("completed", "no_actionable_patterns", null, skillName,
                    newTraces.size(), successTraces.size(), failureTraces.size(),
                    0, 0, 0, 0, List.of(), "No proposals generated");
        }

        // Step 4: Hierarchical merge
        MergedProposal merged = mergeProposals(proposals);
        log.info("[EvolutionAgent] Merged: {} accepted, {} conflicts, {} discarded",
                merged.mergedProposals().size(), merged.conflicts().size(), merged.discarded().size());

        // Step 5: Limit proposals
        List<PatchProposal> finalProposals = merged.mergedProposals().subList(
                0, Math.min(merged.mergedProposals().size(), maxProposals));

        return CycleResult.completed(
                UUID.randomUUID().toString(),
                skillName,
                traces.size(),
                successTraces.size(),
                failureTraces.size(),
                proposals.size(),
                finalProposals.size(),
                merged.conflicts().size(),
                merged.discarded().size(),
                finalProposals,
                merged.mergeRationale());
    }

    // ── Proposal generation ───────────────────────────────────────────

    private List<PatchProposal> generateProposals(
            List<SkillEvolutionTrace> successTraces,
            List<SkillEvolutionTrace> failureTraces) {

        List<PatchProposal> proposals = new ArrayList<>();

        // Analyze success traces
        for (SkillEvolutionTrace trace : successTraces) {
            proposals.addAll(analyzeSuccessTrace(trace));
        }

        // Analyze failure traces
        for (SkillEvolutionTrace trace : failureTraces) {
            PatchProposal p = analyzeFailureTrace(trace);
            if (p != null) proposals.add(p);
        }

        // Batch analysis: group similar failures for cross-trace pattern detection
        if (failureTraces.size() >= 2) {
            proposals.addAll(analyzeFailureBatch(failureTraces));
        }

        return proposals;
    }

    private List<PatchProposal> analyzeSuccessTrace(SkillEvolutionTrace trace) {
        List<PatchProposal> result = new ArrayList<>();

        // Heuristic: discovered new rules during success
        if (trace.newRulesDiscovered() != null && !trace.newRulesDiscovered().isEmpty()) {
            for (Map<String, Object> newRule : trace.newRulesDiscovered()) {
                result.add(new PatchProposal(
                        UUID.randomUUID().toString(),
                        List.of(trace.traceId()),
                        trace.skillName(),
                        "add",
                        null,
                        (String) newRule.getOrDefault("content", ""),
                        "Discovered during successful execution: " + newRule.getOrDefault("context", ""),
                        0.7,
                        List.of(trace.userQuery().substring(0, Math.min(trace.userQuery().length(), 200)))
                ));
            }
        }

        // Heuristic: positive user feedback → confirm loaded rules
        if (trace.userFeedback() != null) {
            String fb = trace.userFeedback().toLowerCase();
            if (fb.contains("helpful") || fb.contains("good")) {
                List<String> loadedRules = trace.loadedRules();
                result.add(new PatchProposal(
                        UUID.randomUUID().toString(),
                        List.of(trace.traceId()),
                        trace.skillName(),
                        "modify",
                        loadedRules.isEmpty() ? null : loadedRules.get(0),
                        null,
                        "User confirmed these rules are helpful",
                        0.6,
                        List.of("Feedback: " + trace.userFeedback())
                ));
            }
        }

        return result;
    }

    private PatchProposal analyzeFailureTrace(SkillEvolutionTrace trace) {
        String errorMsg = trace.executionDetails().getOrDefault("error", "").toString();

        // Pattern 1: Missing rule detection
        if (errorMsg.toLowerCase().contains("rule")
                && (errorMsg.toLowerCase().contains("not found") || errorMsg.toLowerCase().contains("missing"))) {
            return new PatchProposal(
                    UUID.randomUUID().toString(),
                    List.of(trace.traceId()),
                    trace.skillName(),
                    "add",
                    null,
                    null,
                    "Error indicates missing rule: " + errorMsg.substring(0, Math.min(errorMsg.length(), 100)),
                    0.8,
                    List.of("Query: " + trace.userQuery().substring(0, Math.min(trace.userQuery().length(), 200)),
                            "Error: " + errorMsg)
            );
        }

        // Pattern 2: Regression detection
        if (trace.regressionInfo() != null && !trace.regressionInfo().isEmpty()) {
            String affectedRule = trace.regressionInfo().getOrDefault("affected_rule", "").toString();
            return new PatchProposal(
                    UUID.randomUUID().toString(),
                    List.of(trace.traceId()),
                    trace.skillName(),
                    "modify",
                    affectedRule,
                    null,
                    "Regression detected in rule: " + affectedRule,
                    0.9,
                    List.of("Regression details: " + trace.regressionInfo())
            );
        }

        // Pattern 3: Generic failure — suggest reviewing last loaded rule
        List<String> loadedRules = trace.loadedRules();
        if (!loadedRules.isEmpty()) {
            return new PatchProposal(
                    UUID.randomUUID().toString(),
                    List.of(trace.traceId()),
                    trace.skillName(),
                    "modify",
                    loadedRules.getLast(),
                    null,
                    "Failure occurred with these rules active: " + String.join(", ", loadedRules),
                    0.5,
                    List.of("Error: " + errorMsg,
                            "Query: " + trace.userQuery().substring(0, Math.min(trace.userQuery().length(), 200)))
            );
        }

        return null;
    }

    private List<PatchProposal> analyzeFailureBatch(List<SkillEvolutionTrace> traces) {
        // Heuristic batch analysis: find common error patterns
        Map<String, Integer> errorCounts = new LinkedHashMap<>();
        for (SkillEvolutionTrace t : traces) {
            String error = t.executionDetails().getOrDefault("error", "unknown").toString();
            // Normalize: take first 50 chars
            String key = error.length() > 50 ? error.substring(0, 50) : error;
            errorCounts.merge(key, 1, Integer::sum);
        }

        List<PatchProposal> proposals = new ArrayList<>();
        String skillName = traces.get(0).skillName();
        List<String> traceIds = traces.stream().map(SkillEvolutionTrace::traceId).toList();

        for (var entry : errorCounts.entrySet()) {
            if (entry.getValue() >= 2) {
                // Repeated error → suggest a rule to address it
                proposals.add(new PatchProposal(
                        UUID.randomUUID().toString(),
                        traceIds,
                        skillName,
                        "add",
                        null,
                        null,
                        "Cross-trace pattern: '" + entry.getKey() + "' occurred " + entry.getValue() + " times",
                        Math.min(0.5 + entry.getValue() * 0.1, 0.9),
                        List.of("Batch analysis of " + traces.size() + " traces")
                ));
            }
        }

        return proposals;
    }

    // ── Merge ─────────────────────────────────────────────────────────

    MergedProposal mergeProposals(List<PatchProposal> proposals) {
        MergedProposal merged = new MergedProposal();

        if (proposals.isEmpty()) {
            merged.setMergeRationale("No proposals to merge");
            return merged;
        }

        // Group by (skillName, targetRuleId)
        Map<String, List<PatchProposal>> groups = new LinkedHashMap<>();
        for (PatchProposal p : proposals) {
            String key = p.skillName() + "||" + (p.targetRuleId() != null ? p.targetRuleId() : "__null__");
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
        }

        for (var entry : groups.entrySet()) {
            List<PatchProposal> groupProposals = entry.getValue();
            if (groupProposals.size() == 1) {
                PatchProposal p = groupProposals.get(0);
                if (p.confidence() >= 0.6) {
                    merged.mergedProposals().add(p);
                } else {
                    merged.discarded().add(p);
                }
            } else {
                // Multiple proposals for same target — conflict resolution
                Map<String, List<PatchProposal>> resolved = resolveConflicts(groupProposals);
                merged.mergedProposals().addAll(resolved.getOrDefault("accepted", List.of()));
                merged.conflicts().addAll(
                        resolved.getOrDefault("conflicts", List.of()).stream()
                                .map(List::of).toList());
                merged.discarded().addAll(resolved.getOrDefault("discarded", List.of()));
            }
        }

        // Sort by confidence descending
        merged.mergedProposals().sort(Comparator.comparingDouble(PatchProposal::confidence).reversed());

        merged.setMergeRationale(
                "Merged " + proposals.size() + " proposals into " + merged.mergedProposals().size()
                        + " accepted, " + merged.conflicts().size() + " conflicts, "
                        + merged.discarded().size() + " discarded");

        return merged;
    }

    private Map<String, List<PatchProposal>> resolveConflicts(List<PatchProposal> proposals) {
        Map<String, List<PatchProposal>> result = new LinkedHashMap<>();
        result.put("accepted", new ArrayList<>());
        result.put("conflicts", new ArrayList<>());
        result.put("discarded", new ArrayList<>());

        if (proposals.isEmpty()) return result;

        // Sort by confidence * number of source traces
        List<Map.Entry<PatchProposal, Double>> scored = proposals.stream()
                .map(p -> Map.entry(p, p.confidence() * Math.max(1, p.sourceTraces().size())))
                .sorted(Map.Entry.<PatchProposal, Double>comparingByValue().reversed())
                .toList();

        // Accept top proposal
        PatchProposal best = scored.get(0).getKey();
        double bestScore = scored.get(0).getValue();
        result.get("accepted").add(best);

        // Check remaining
        for (int i = 1; i < scored.size(); i++) {
            PatchProposal p = scored.get(i).getKey();
            double score = scored.get(i).getValue();

            if (bestScore == 0 || score / bestScore < 0.5) {
                result.get("discarded").add(p);
            } else if (!p.operation().equals(best.operation())) {
                result.get("conflicts").add(p);
            } else {
                result.get("discarded").add(p);
            }
        }

        return result;
    }

    // ── State ─────────────────────────────────────────────────────────

    public int getAnalyzedCount() { return analyzedTraceIds.size(); }
    public void resetAnalyzed() { analyzedTraceIds.clear(); }
}
