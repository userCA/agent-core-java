package io.agentcore.skill_evolution;

import io.agentcore.skill_evolution.EvolutionTypes.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the skill_evolution package.
 */
class SkillEvolutionTest {

    // ── EvolutionTypes ─────────────────────────────────────────────────────

    @Nested
    class TypesTests {

        @Test
        void executionOutcome_fromString_knownValues() {
            assertEquals(ExecutionOutcome.SUCCESS, ExecutionOutcome.fromString("success"));
            assertEquals(ExecutionOutcome.FAILURE, ExecutionOutcome.fromString("failure"));
            assertEquals(ExecutionOutcome.PARTIAL, ExecutionOutcome.fromString("partial"));
            assertEquals(ExecutionOutcome.REGRESSION, ExecutionOutcome.fromString("regression"));
        }

        @Test
        void executionOutcome_fromString_unknownDefaultsToSuccess() {
            assertEquals(ExecutionOutcome.SUCCESS, ExecutionOutcome.fromString("unknown"));
        }

        @Test
        void skillEvolutionTrace_createMinimal() {
            SkillEvolutionTrace t = SkillEvolutionTrace.create("t1", "my-skill");
            assertEquals("t1", t.traceId());
            assertEquals("my-skill", t.skillName());
            assertEquals(ExecutionOutcome.SUCCESS, t.executionOutcome());
            assertTrue(t.loadedRules().isEmpty());
        }

        @Test
        void skillEvolutionTrace_requiresTraceId() {
            assertThrows(IllegalArgumentException.class, () ->
                    new SkillEvolutionTrace("", 0, null, "", "skill", null, null, null, null, null, null));
        }

        @Test
        void skillEvolutionTrace_markSuccess() {
            SkillEvolutionTrace t = SkillEvolutionTrace.create("t1", "skill");
            SkillEvolutionTrace ok = t.markSuccess(Map.of("steps", 3));
            assertEquals(ExecutionOutcome.SUCCESS, ok.executionOutcome());
            assertEquals(3, ok.executionDetails().get("steps"));
        }

        @Test
        void skillEvolutionTrace_markFailure() {
            SkillEvolutionTrace t = SkillEvolutionTrace.create("t1", "skill");
            SkillEvolutionTrace fail = t.markFailure("oom", Map.of());
            assertEquals(ExecutionOutcome.FAILURE, fail.executionOutcome());
            assertEquals("oom", fail.executionDetails().get("error"));
        }

        @Test
        void patchProposal_toMap_containsAllFields() {
            PatchProposal p = new PatchProposal("p1", List.of("t1"), "my-skill",
                    "add", null, "new rule content", "because reasons", 0.85,
                    List.of("evidence1"));
            Map<String, Object> m = p.toMap();
            assertEquals("p1", m.get("proposal_id"));
            assertEquals("my-skill", m.get("skill_name"));
            assertEquals("add", m.get("operation"));
            assertEquals(0.85, (double) m.get("confidence"), 0.001);
        }

        @Test
        void patchProposal_requiresProposalId() {
            assertThrows(IllegalArgumentException.class, () ->
                    new PatchProposal("", List.of(), "skill", "add", null, "", "", 0.5, null));
        }

        @Test
        void patchProposal_clampsConfidence() {
            PatchProposal p1 = new PatchProposal("p1", null, "s", "add", null, "", "", 1.5, null);
            assertEquals(1.0, p1.confidence());
            PatchProposal p2 = new PatchProposal("p2", null, "s", "add", null, "", "", -0.5, null);
            assertEquals(0.0, p2.confidence());
        }

        @Test
        void validationResult_autoRecommendation() {
            ValidationResult v = new ValidationResult("p1", 0.1, true, List.of(), List.of(), "");
            assertEquals("accept", v.recommendation());

            ValidationResult v2 = new ValidationResult("p2", -0.1, false, List.of(), List.of(), "");
            assertEquals("reject", v2.recommendation());

            ValidationResult v3 = new ValidationResult("p3", 0.01, false, List.of(), List.of(), "");
            assertEquals("needs_review", v3.recommendation());
        }

        @Test
        void evolutionSummary_autoTimestamp() {
            EvolutionSummary s = new EvolutionSummary("c1", 0, 10, 3, 2, 1,
                    List.of("skill-a"), Map.of("score_delta", 0.05));
            assertTrue(s.timestamp() > 0);
        }
    }

    // ── EvolutionStore ─────────────────────────────────────────────────────

    @Nested
    class StoreTests {

        @Test
        void inMemoryStore_saveAndRetrieve() {
            EvolutionStore.Store store = EvolutionStore.createInMemory();
            SkillEvolutionTrace t = SkillEvolutionTrace.create("t1", "skill-a");
            store.saveTrace(t);
            List<SkillEvolutionTrace> result = store.getTraces("skill-a", null, 10, 0);
            assertEquals(1, result.size());
            assertEquals("t1", result.getFirst().traceId());
        }

        @Test
        void inMemoryStore_filterBySkillName() {
            EvolutionStore.Store store = EvolutionStore.createInMemory();
            store.saveTrace(SkillEvolutionTrace.create("t1", "skill-a"));
            store.saveTrace(SkillEvolutionTrace.create("t2", "skill-b"));
            store.saveTrace(SkillEvolutionTrace.create("t3", "skill-a"));

            assertEquals(2, store.getTraces("skill-a", null, 10, 0).size());
            assertEquals(1, store.getTraces("skill-b", null, 10, 0).size());
        }

        @Test
        void inMemoryStore_filterByOutcome() {
            EvolutionStore.Store store = EvolutionStore.createInMemory();
            store.saveTrace(SkillEvolutionTrace.create("t1", "skill-a")
                    .markSuccess(Map.of()));
            store.saveTrace(SkillEvolutionTrace.create("t2", "skill-a")
                    .markFailure("err", Map.of()));

            assertEquals(1, store.getTraces("skill-a", "success", 10, 0).size());
            assertEquals(1, store.getTraces("skill-a", "failure", 10, 0).size());
        }

        @Test
        void inMemoryStore_getTraceCount() {
            EvolutionStore.Store store = EvolutionStore.createInMemory();
            store.saveTrace(SkillEvolutionTrace.create("t1", "skill-a"));
            store.saveTrace(SkillEvolutionTrace.create("t2", "skill-a"));
            store.saveTrace(SkillEvolutionTrace.create("t3", "skill-b"));

            assertEquals(2, store.getTraceCount("skill-a", null));
            assertEquals(3, store.getTraceCount(null, null));
        }

        @Test
        void inMemoryStore_offsetAndLimit() {
            EvolutionStore.Store store = EvolutionStore.createInMemory();
            for (int i = 1; i <= 5; i++) {
                store.saveTrace(SkillEvolutionTrace.create("t" + i, "skill-a")
                        .withDetails(Map.of("i", i)));
            }
            List<SkillEvolutionTrace> page = store.getTraces(null, null, 2, 2);
            assertEquals(2, page.size());
        }

        @Test
        void factory_createsMemoryStore() {
            EvolutionStore.Store store = EvolutionStore.create("memory", null);
            assertNotNull(store);
            assertTrue(store instanceof EvolutionStore.InMemoryStore);
        }

        @Test
        void factory_throwsForUnknownType() {
            assertThrows(IllegalArgumentException.class, () ->
                    EvolutionStore.create("redis", null));
        }
    }

    // ── EvolutionStore JSONL ────────────────────────────────────────────────

    @Nested
    class JsonlStoreTests {

        @TempDir
        Path tmpDir;

        @Test
        void jsonlStore_saveAndRetrieve() {
            String path = tmpDir.resolve("traces.jsonl").toString();
            EvolutionStore.Store store = EvolutionStore.create("jsonl", path);
            SkillEvolutionTrace t = SkillEvolutionTrace.create("t1", "skill-a");
            store.saveTrace(t);

            List<SkillEvolutionTrace> result = store.getTraces("skill-a", null, 10, 0);
            assertEquals(1, result.size());
            assertEquals("t1", result.getFirst().traceId());
        }

        @Test
        void jsonlStore_appendOnly() {
            String path = tmpDir.resolve("traces.jsonl").toString();
            EvolutionStore.Store store = EvolutionStore.create("jsonl", path);
            store.saveTrace(SkillEvolutionTrace.create("t1", "skill-a"));
            store.saveTrace(SkillEvolutionTrace.create("t2", "skill-b"));

            assertEquals(2, store.getTraceCount(null, null));
            assertEquals(1, store.getTraceCount("skill-b", null));
        }

        @Test
        void jsonlStore_nonExistentFile_returnsEmpty() {
            String path = tmpDir.resolve("nonexistent.jsonl").toString();
            EvolutionStore.Store store = EvolutionStore.create("jsonl", path);
            assertTrue(store.getTraces(null, null, 10, 0).isEmpty());
        }
    }

    // ── SkillTraceCollector ────────────────────────────────────────────────

    @Nested
    class CollectorTests {

        @Test
        void extractSkillNames_parsesSkillTags() {
            String prompt = "You are an AI. <skill name=\"dev-process-backend\"> rules here"
                    + " <skill name=\"soft-skill\"> more rules";
            List<String> names = SkillTraceCollector.extractSkillNames(prompt);
            assertEquals(2, names.size());
            assertTrue(names.contains("dev-process-backend"));
            assertTrue(names.contains("soft-skill"));
        }

        @Test
        void extractSkillNames_emptyForNull() {
            assertTrue(SkillTraceCollector.extractSkillNames(null).isEmpty());
            assertTrue(SkillTraceCollector.extractSkillNames("").isEmpty());
        }

        @Test
        void onAgentStart_resetsTurnCount() {
            EvolutionStore.Store store = EvolutionStore.createInMemory();
            SkillTraceCollector collector = new SkillTraceCollector(store);
            collector.onAgentStart();
            assertEquals(0, collector.getTurnCount());
        }

        @Test
        void onTurnEnd_incrementsTurnCount() {
            EvolutionStore.Store store = EvolutionStore.createInMemory();
            SkillTraceCollector collector = new SkillTraceCollector(store);
            collector.onAgentStart();
            collector.onTurnEnd("<skill name=\"test-skill\"> rules", "hello", false, null);
            assertEquals(1, collector.getTurnCount());
        }

        @Test
        void onTurnEnd_savesTraceWhenSkillPresent() {
            EvolutionStore.Store store = EvolutionStore.createInMemory();
            SkillTraceCollector collector = new SkillTraceCollector(store);
            collector.onAgentStart();
            collector.onTurnEnd("<skill name=\"test-skill\"> rules", "hello", false, null);

            assertEquals(1, store.getTraceCount("test-skill", null));
        }

        @Test
        void onTurnEnd_noTraceWhenNoSkill() {
            EvolutionStore.Store store = EvolutionStore.createInMemory();
            SkillTraceCollector collector = new SkillTraceCollector(store);
            collector.onTurnEnd("No skill tags here", "hello", false, null);
            assertEquals(0, store.getTraceCount(null, null));
        }

        @Test
        void onTurnEnd_recordsFailureOutcome() {
            EvolutionStore.Store store = EvolutionStore.createInMemory();
            SkillTraceCollector collector = new SkillTraceCollector(store);
            collector.onTurnEnd("<skill name=\"s1\"> rules", "query", true, "NPE");
            assertEquals(1, store.getTraceCount("s1", "failure"));
        }

        @Test
        void disabled_doesNotRecordTraces() {
            EvolutionStore.Store store = EvolutionStore.createInMemory();
            SkillTraceCollector collector = new SkillTraceCollector(store, false);
            collector.onAgentStart();
            collector.onTurnEnd("<skill name=\"s1\"> rules", "query", false, null);
            assertEquals(0, store.getTraceCount(null, null));
        }

        @Test
        void recordUserFeedback_savesFeedbackTrace() {
            EvolutionStore.Store store = EvolutionStore.createInMemory();
            SkillTraceCollector collector = new SkillTraceCollector(store);
            collector.recordUserFeedback("t1", "great!", true);
            assertEquals(1, store.getTraceCount(null, "success"));
        }

        @Test
        void getName_returnsCorrectName() {
            EvolutionStore.Store store = EvolutionStore.createInMemory();
            SkillTraceCollector collector = new SkillTraceCollector(store);
            assertEquals("skill_trace_collector", collector.getName());
        }

        @Test
        void onSkillLoaded_registersRules() {
            EvolutionStore.Store store = EvolutionStore.createInMemory();
            SkillTraceCollector collector = new SkillTraceCollector(store);
            collector.onSkillLoaded("my-skill", List.of("r1", "r2"));
            collector.onTurnEnd("<skill name=\"my-skill\"> content", "query", false, null);

            List<SkillEvolutionTrace> traces = store.getTraces("my-skill", null, 10, 0);
            assertEquals(1, traces.size());
            assertEquals(List.of("r1", "r2"), traces.getFirst().loadedRules());
        }
    }

    // ── SkillValidationGate ────────────────────────────────────────────────

    @Nested
    class ValidationTests {

        @TempDir
        Path tmpDir;

        private Path createSkillDir(String skillName, String content) throws Exception {
            Path skillPath = tmpDir.resolve(skillName);
            Files.createDirectories(skillPath);
            Files.writeString(skillPath.resolve("SKILL.md"), content);
            return skillPath;
        }

        @Test
        void validate_noTestCases_returnsNeedsReview() {
            SkillValidationGate gate = new SkillValidationGate(tmpDir);
            PatchProposal p = new PatchProposal("p1", List.of(), "no-skill",
                    "add", null, "new rule", "", 0.8, null);
            ValidationResult result = gate.validate(p);
            assertFalse(result.passed());
            assertEquals("needs_review", result.recommendation());
        }

        @Test
        void validate_withTestCases_scoresCorrectly() throws Exception {
            String content = "# My Skill\n\n## 规则 1：Always use camelCase for variables\n";
            createSkillDir("my-skill", content);

            SkillValidationGate gate = new SkillValidationGate(tmpDir, 0.0, false);
            gate.registerTestCases("my-skill", List.of(
                    new SkillValidationGate.TestCase("tc1", "check naming",
                            "camelCase variables naming convention",
                            "should enforce camelCase", "", List.of())
            ));

            PatchProposal p = new PatchProposal("p1", List.of("t1"), "my-skill",
                    "add", null, "Also enforce PascalCase for classes", "improvement", 0.9, null);
            ValidationResult result = gate.validate(p);
            assertNotNull(result);
            assertFalse(result.testResults().isEmpty());
        }

        @Test
        void applyProposal_add_appendsContent() throws Exception {
            String content = "# My Skill\n\n## 规则 1：Use camelCase\n";
            createSkillDir("my-skill", content);

            SkillValidationGate gate = new SkillValidationGate(tmpDir, 0.0, false);
            PatchProposal p = new PatchProposal("p1", List.of(), "my-skill",
                    "add", null, "Also use PascalCase for classes", "", 0.9, null);
            boolean applied = gate.applyProposal(p, false, true);
            assertTrue(applied);

            String updated = Files.readString(tmpDir.resolve("my-skill/SKILL.md"));
            assertTrue(updated.contains("PascalCase"));
        }

        @Test
        void applyProposal_requiresForceWhenHumanReviewRequired() throws Exception {
            String content = "# Skill\n\n## 规则 1：Rule one\n";
            createSkillDir("guarded-skill", content);

            SkillValidationGate gate = new SkillValidationGate(tmpDir, 0.05, true);
            PatchProposal p = new PatchProposal("p1", List.of(), "guarded-skill",
                    "add", null, "New rule", "", 0.9, null);
            boolean applied = gate.applyProposal(p, false, false);
            assertFalse(applied, "should require force=true");
        }

        @Test
        void applyProposal_createsBackup() throws Exception {
            String content = "# Skill original\n";
            createSkillDir("backup-skill", content);

            SkillValidationGate gate = new SkillValidationGate(tmpDir, 0.0, false);
            PatchProposal p = new PatchProposal("p1", List.of(), "backup-skill",
                    "add", null, "New content", "", 0.9, null);
            gate.applyProposal(p, true, true);

            assertTrue(Files.exists(tmpDir.resolve("backup-skill/SKILL.md.bak")));
            String backup = Files.readString(tmpDir.resolve("backup-skill/SKILL.md.bak"));
            assertEquals(content, backup);
        }

        @Test
        void diffProposal_showsChange() throws Exception {
            String content = "# Original Skill\n";
            createSkillDir("diff-skill", content);

            SkillValidationGate gate = new SkillValidationGate(tmpDir);
            PatchProposal p = new PatchProposal("p1", List.of(), "diff-skill",
                    "add", null, "Added rule", "", 0.9, null);
            String diff = gate.diffProposal(p);
            assertTrue(diff.contains("diff-skill"));
            assertTrue(diff.contains("add"));
        }

        @Test
        void applyProposal_returnsFalseForMissingSkill() {
            SkillValidationGate gate = new SkillValidationGate(tmpDir, 0.0, false);
            PatchProposal p = new PatchProposal("p1", List.of(), "nonexistent-skill",
                    "add", null, "content", "", 0.5, null);
            assertFalse(gate.applyProposal(p, false, true));
        }
    }
}
