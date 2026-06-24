package io.agentcore.skill.evolution;

import io.agentcore.skill.evolution.EvolutionTypes.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extension that collects skill execution traces for self-evolution.
 *
 * <p>Mirrors Python {@code agent_core/skill_evolution/collector.py}.
 * Records traces whenever skills are loaded and applied.
 */
public final class SkillTraceCollector {

    private static final Logger log = LoggerFactory.getLogger(SkillTraceCollector.class);
    private static final Pattern SKILL_TAG_RE = Pattern.compile("<skill\\s+name=\"([^\"]+)\"");

    private final EvolutionStore.Store store;
    private final boolean enabled;
    private int turnCount = 0;
    private final Map<String, List<String>> loadedSkills = new HashMap<>();

    public SkillTraceCollector(EvolutionStore.Store store) {
        this(store, true);
    }

    public SkillTraceCollector(EvolutionStore.Store store, boolean enabled) {
        this.store = store;
        this.enabled = enabled;
    }

    public String getName() {
        return "skill_trace_collector";
    }

    public boolean isEnabled() {
        return enabled;
    }

    // ── Event hooks ──────────────────────────────────────────────────

    /** Call when agent starts. */
    public void onAgentStart() {
        if (!enabled) return;
        turnCount = 0;
    }

    /** Call when a turn ends. Extracts skill names from system prompt. */
    public void onTurnEnd(String systemPrompt, String userQuery, boolean hasError, String errorMessage) {
        if (!enabled) return;
        turnCount++;

        List<String> skillNames = extractSkillNames(systemPrompt);
        if (skillNames.isEmpty()) return;

        ExecutionOutcome outcome = hasError ? ExecutionOutcome.FAILURE : ExecutionOutcome.SUCCESS;

        for (String skillName : skillNames) {
            List<String> ruleIds = loadedSkills.getOrDefault(skillName, List.of());
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("turn_index", turnCount);
            if (hasError && errorMessage != null) {
                details.put("error", errorMessage);
            }

            SkillEvolutionTrace trace = new SkillEvolutionTrace(
                    UUID.randomUUID().toString(),
                    System.currentTimeMillis() / 1000.0,
                    null,
                    userQuery != null ? userQuery.substring(0, Math.min(userQuery.length(), 500)) : "",
                    skillName,
                    ruleIds,
                    outcome,
                    details,
                    List.of(),
                    null,
                    Map.of());

            try {
                store.saveTrace(trace);
            } catch (Exception e) {
                log.warn("Failed to save trace for skill={}: {}", skillName, e.getMessage(), e);
            }
        }
    }

    /** Register skill→rule mapping for precise trace data. */
    public void onSkillLoaded(String skillName, List<String> ruleIds) {
        if (!enabled) return;
        loadedSkills.put(skillName, ruleIds);
    }

    /** Manually attach user feedback to a trace. */
    public void recordUserFeedback(String traceId, String feedback, Boolean wasHelpful) {
        ExecutionOutcome outcome;
        if (Boolean.TRUE.equals(wasHelpful)) outcome = ExecutionOutcome.SUCCESS;
        else if (Boolean.FALSE.equals(wasHelpful)) outcome = ExecutionOutcome.FAILURE;
        else outcome = ExecutionOutcome.PARTIAL;

        SkillEvolutionTrace feedbackTrace = new SkillEvolutionTrace(
                traceId + "-feedback",
                System.currentTimeMillis() / 1000.0,
                null, "", "",
                List.of(), outcome,
                Map.of("original_trace_id", traceId, "type", "feedback"),
                List.of(), feedback, Map.of());
        store.saveTrace(feedbackTrace);
    }

    // ── Helpers ──────────────────────────────────────────────────────

    static List<String> extractSkillNames(String systemPrompt) {
        if (systemPrompt == null || systemPrompt.isBlank()) return List.of();
        List<String> names = new ArrayList<>();
        Matcher m = SKILL_TAG_RE.matcher(systemPrompt);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    /** Get current turn count. */
    public int getTurnCount() {
        return turnCount;
    }
}
