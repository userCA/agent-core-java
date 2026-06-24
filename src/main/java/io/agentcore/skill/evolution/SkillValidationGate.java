package io.agentcore.skill.evolution;

import io.agentcore.skill.evolution.EvolutionTypes.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Skill Evolution Validation Gate — validates proposed skill changes
 * against test cases before applying them.
 *
 * <p>Mirrors Python {@code agent_core/skill_evolution/validation.py}.
 * Acts as a quality filter: only accepts changes with measurable improvement.
 */
public final class SkillValidationGate {

    private static final Logger log = LoggerFactory.getLogger(SkillValidationGate.class);

    private final Path skillDir;
    private final double testThreshold;
    private final boolean requireHumanReview;
    private final Map<String, List<TestCase>> testCases = new HashMap<>();

    /** A test case for validating skill changes. */
    public record TestCase(
            String testId,
            String description,
            String inputQuery,
            String expectedBehavior,
            String successCriteria,
            List<String> tags
    ) {
        public TestCase {
            if (tags == null) tags = List.of();
        }
    }

    public SkillValidationGate(Path skillDir) {
        this(skillDir, 0.05, true);
    }

    public SkillValidationGate(Path skillDir, double testThreshold, boolean requireHumanReview) {
        this.skillDir = skillDir;
        this.testThreshold = testThreshold;
        this.requireHumanReview = requireHumanReview;
    }

    public void registerTestCases(String skillName, List<TestCase> cases) {
        testCases.put(skillName, cases);
    }

    /**
     * Validate a proposed skill change against test cases.
     */
    public ValidationResult validate(PatchProposal proposal) {
        return validate(proposal, null);
    }

    public ValidationResult validate(PatchProposal proposal, List<TestCase> overrideCases) {
        String skillName = proposal.skillName();
        List<TestCase> cases = overrideCases != null ? overrideCases : testCases.getOrDefault(skillName, List.of());

        if (cases.isEmpty()) {
            return new ValidationResult(proposal.proposalId(), 0.0, false, List.of(), List.of(), "needs_review");
        }

        // Load current skill content
        String oldContent = loadSkillContent(skillName);
        if (oldContent == null) {
            return new ValidationResult(proposal.proposalId(), 0.0, false, List.of(),
                    cases.stream().map(TestCase::testId).toList(), "reject");
        }

        // Generate new content
        String newContent = applyProposal(oldContent, proposal);

        // Run tests on both versions
        Map<String, Double> oldScores = runTests(cases, oldContent);
        Map<String, Double> newScores = runTests(cases, newContent);

        // Calculate score delta
        double oldAvg = oldScores.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double newAvg = newScores.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double scoreDelta = newAvg - oldAvg;

        boolean passed = scoreDelta >= testThreshold;
        List<ValidationResult.TestResultEntry> results = new ArrayList<>();
        List<String> failedCases = new ArrayList<>();

        for (TestCase tc : cases) {
            double oldScore = oldScores.getOrDefault(tc.testId(), 0.0);
            double newScore = newScores.getOrDefault(tc.testId(), 0.0);
            boolean improved = newScore > oldScore;
            results.add(new ValidationResult.TestResultEntry(
                    tc.testId(), improved,
                    (improved ? "✓" : "✗") + " " + tc.testId() + ": " +
                    String.format("%.2f", oldScore) + " → " + String.format("%.2f", newScore)));
            if (!improved) failedCases.add(tc.testId());
        }

        String recommendation = passed ? "accept"
                : scoreDelta < -testThreshold ? "reject" : "needs_review";

        return new ValidationResult(proposal.proposalId(), scoreDelta, passed,
                results, failedCases, recommendation);
    }

    /**
     * Apply a validated proposal to the actual skill file.
     */
    public boolean applyProposal(PatchProposal proposal, boolean backup, boolean force) {
        if (requireHumanReview && !force) {
            log.warn("Human review required — use force=true to bypass");
            return false;
        }

        Path skillPath = skillDir.resolve(proposal.skillName()).resolve("SKILL.md");
        if (!Files.exists(skillPath)) return false;

        try {
            if (backup) {
                Path backupPath = skillPath.resolveSibling("SKILL.md.bak");
                Files.writeString(backupPath, Files.readString(skillPath));
            }
            String oldContent = Files.readString(skillPath);
            String newContent = applyProposal(oldContent, proposal);
            Files.writeString(skillPath, newContent);
            return true;
        } catch (IOException e) {
            log.warn("Failed to apply proposal: {}", e.getMessage(), e);
            return false;
        }
    }

    // ── Internal helpers ─────────────────────────────────────────────

    private String loadSkillContent(String skillName) {
        Path skillPath = skillDir.resolve(skillName).resolve("SKILL.md");
        if (!Files.exists(skillPath)) return null;
        try {
            return Files.readString(skillPath);
        } catch (IOException e) {
            return null;
        }
    }

    String applyProposal(String oldContent, PatchProposal proposal) {
        String operation = proposal.operation();
        String targetRule = proposal.targetRuleId();
        String newContent = proposal.newContent();

        if ("add".equals(operation)) {
            return oldContent + "\n\n## 新增规则：" + newContent + "\n";
        } else if ("modify".equals(operation) && targetRule != null) {
            String ruleNum = extractRuleNumber(targetRule);
            Pattern p = Pattern.compile("## 规则 \\d+：.*?(?=## 规则|\\Z)", Pattern.DOTALL);
            Matcher m = p.matcher(oldContent);
            while (m.find()) {
                if (m.group().contains(targetRule) || m.group().contains("规则 " + ruleNum)) {
                    return oldContent.substring(0, m.start())
                            + "## 规则 " + ruleNum + "：" + newContent + "\n\n"
                            + oldContent.substring(m.end());
                }
            }
            return oldContent + "\n\n## 规则 " + ruleNum + "：" + newContent + "\n";
        } else if ("delete".equals(operation) && targetRule != null) {
            return oldContent.replaceAll("## 规则 \\d+：.*?(?=## 规则|\\Z)", "");
        }
        return oldContent;
    }

    private String extractRuleNumber(String ruleId) {
        if (ruleId.startsWith("rule_")) return ruleId.split("_")[1];
        Matcher m = Pattern.compile("规则\\s*(\\d+)").matcher(ruleId);
        return m.find() ? m.group(1) : ruleId;
    }

    private Map<String, Double> runTests(List<TestCase> cases, String skillContent) {
        Map<String, Double> scores = new LinkedHashMap<>();
        for (TestCase tc : cases) {
            scores.put(tc.testId(), executeTestCase(tc, skillContent));
        }
        return scores;
    }

    /**
     * Heuristic keyword-overlap scoring (fallback when no agent runner is available).
     */
    private double executeTestCase(TestCase tc, String skillContent) {
        String queryLower = tc.inputQuery().toLowerCase();
        String skillLower = skillContent.toLowerCase();

        Set<String> queryWords = new HashSet<>(Arrays.asList(queryLower.split("\\W+")));
        Set<String> skillWords = new HashSet<>(Arrays.asList(skillLower.split("\\W+")));
        queryWords.removeIf(String::isEmpty);
        skillWords.removeIf(String::isEmpty);

        double overlap = queryWords.isEmpty() ? 0 :
                (double) queryWords.stream().filter(skillWords::contains).count() / queryWords.size();
        double baseScore = Math.min(overlap * 2, 1.0);

        String expectedLower = tc.expectedBehavior().toLowerCase();
        Set<String> expectedWords = new HashSet<>(Arrays.asList(expectedLower.split("\\W+")));
        expectedWords.removeIf(String::isEmpty);
        double expectedOverlap = expectedWords.isEmpty() ? 0 :
                (double) expectedWords.stream().filter(skillWords::contains).count() / expectedWords.size();
        double bonus = expectedOverlap * 0.2;

        return Math.min(baseScore + bonus, 1.0);
    }

    /**
     * Generate a human-readable diff preview.
     */
    public String diffProposal(PatchProposal proposal) {
        String oldContent = loadSkillContent(proposal.skillName());
        if (oldContent == null) oldContent = "";
        String newContent = applyProposal(oldContent, proposal);
        if (oldContent.equals(newContent)) {
            return "# No change (proposal " + proposal.proposalId() + " had no effect)\n";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# Proposed change for: ").append(proposal.skillName()).append("\n");
        sb.append("# Operation: ").append(proposal.operation()).append("\n");
        sb.append("# Target rule: ").append(proposal.targetRuleId() != null ? proposal.targetRuleId() : "(new)").append("\n");
        sb.append("# Confidence: ").append(String.format("%.0f%%", proposal.confidence() * 100)).append("\n\n");
        for (String line : oldContent.split("\n")) sb.append("-").append(line).append("\n");
        for (String line : newContent.split("\n")) sb.append("+").append(line).append("\n");
        return sb.toString();
    }
}
