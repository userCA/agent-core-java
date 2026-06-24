package io.agentcore.skill.evolution;

import io.agentcore.skill.evolution.EvolutionTypes.*;
import io.agentcore.skill.evolution.EvolutionAuditLog.AuditEntry;
import io.agentcore.skill.evolution.EvolutionStore.InMemoryStore;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class EvolutionAgentTest {

    @Nested
    class AgentCycleTests {
        private InMemoryStore store;
        private OfflineEvolutionAgent agent;

        @BeforeEach
        void setup() {
            store = new InMemoryStore();
            agent = new OfflineEvolutionAgent(store, 100, 5);
        }

        @Test
        void runCycle_insufficientTraces_skipped() {
            for (int i = 0; i < 5; i++) {
                store.saveTrace(SkillEvolutionTrace.create("t" + i, "skill-a"));
            }
            var result = agent.runEvolutionCycle("skill-a", 20);
            assertEquals("skipped", result.status());
            assertEquals("insufficient_traces", result.reason());
        }

        @Test
        void runCycle_noNewTraces_skipped() {
            for (int i = 0; i < 25; i++) {
                store.saveTrace(SkillEvolutionTrace.create("t" + i, "skill-a"));
            }
            agent.runEvolutionCycle("skill-a", 20);
            var result = agent.runEvolutionCycle("skill-a", 20);
            assertEquals("skipped", result.status());
            assertEquals("no_new_traces", result.reason());
        }

        @Test
        void runCycle_successWithFeedback_generatesProposals() {
            for (int i = 0; i < 25; i++) {
                store.saveTrace(new SkillEvolutionTrace(
                        "t" + i, 0, null, "how to build?",
                        "dev-backend", List.of("rule_1"),
                        ExecutionOutcome.SUCCESS, Map.of(), List.of(),
                        "very helpful answer!", Map.of()));
            }
            var result = agent.runEvolutionCycle("dev-backend", 20);
            assertEquals("completed", result.status());
            assertTrue(result.proposalsGenerated() > 0);
        }

        @Test
        void runCycle_failureTraces_generatesProposals() {
            for (int i = 0; i < 25; i++) {
                store.saveTrace(new SkillEvolutionTrace(
                        "t" + i, 0, null, "deploy",
                        "devops", List.of("rule_deploy"),
                        ExecutionOutcome.FAILURE,
                        Map.of("error", "rule not found for deployment"),
                        List.of(), null, Map.of()));
            }
            var result = agent.runEvolutionCycle("devops", 20);
            assertEquals("completed", result.status());
            assertTrue(result.proposalsGenerated() > 0);
        }

        @Test
        void runCycle_regression_highConfidenceProposal() {
            for (int i = 0; i < 25; i++) {
                store.saveTrace(new SkillEvolutionTrace(
                        "t" + i, 0, null, "refactor auth",
                        "auth", List.of("rule_auth_1"),
                        ExecutionOutcome.FAILURE,
                        Map.of("error", "regression in auth"),
                        List.of(), null,
                        Map.of("affected_rule", "rule_auth_1")));
            }
            var result = agent.runEvolutionCycle("auth", 20);
            assertEquals("completed", result.status());
            assertFalse(result.finalProposals().isEmpty());
            assertTrue(result.finalProposals().get(0).confidence() >= 0.8);
        }

        @Test
        void runCycle_mixedTraces_correctSplit() {
            for (int i = 0; i < 15; i++) {
                store.saveTrace(new SkillEvolutionTrace(
                        "s" + i, 0, null, "q", "mix",
                        List.of("r1"), ExecutionOutcome.SUCCESS,
                        Map.of(), List.of(), null, Map.of()));
            }
            for (int i = 0; i < 10; i++) {
                store.saveTrace(new SkillEvolutionTrace(
                        "f" + i, 0, null, "q", "mix",
                        List.of("r1"), ExecutionOutcome.FAILURE,
                        Map.of("error", "missing rule"), List.of(),
                        null, Map.of()));
            }
            var result = agent.runEvolutionCycle("mix", 20);
            assertEquals(15, result.successTraces());
            assertEquals(10, result.failureTraces());
        }

        @Test
        void runCycle_discoveredRules_addProposals() {
            for (int i = 0; i < 25; i++) {
                store.saveTrace(new SkillEvolutionTrace(
                        "t" + i, 0, null, "build api", "api",
                        List.of("r1"), ExecutionOutcome.SUCCESS, Map.of(),
                        List.of(Map.of("content", "validate inputs", "context", "found")),
                        null, Map.of()));
            }
            var result = agent.runEvolutionCycle("api", 20);
            assertTrue(result.finalProposals().stream().anyMatch(p -> "add".equals(p.operation())));
        }

        @Test
        void resetAnalyzed_allowsReanalysis() {
            for (int i = 0; i < 25; i++) {
                store.saveTrace(SkillEvolutionTrace.create("t" + i, "skill-a"));
            }
            agent.runEvolutionCycle("skill-a", 20);
            assertEquals(25, agent.getAnalyzedCount());
            agent.resetAnalyzed();
            assertEquals(0, agent.getAnalyzedCount());
        }
    }

    @Nested
    class MergeTests {
        private OfflineEvolutionAgent agent;

        @BeforeEach
        void setup() {
            agent = new OfflineEvolutionAgent(new InMemoryStore());
        }

        @Test
        void merge_emptyList() {
            var merged = agent.mergeProposals(List.of());
            assertTrue(merged.mergedProposals().isEmpty());
        }

        @Test
        void merge_singleHighConfidence_accepted() {
            PatchProposal p = new PatchProposal("p1", List.of("t1"), "skill",
                    "add", null, "new rule", "good", 0.8, List.of());
            var merged = agent.mergeProposals(List.of(p));
            assertEquals(1, merged.mergedProposals().size());
        }

        @Test
        void merge_singleLowConfidence_discarded() {
            PatchProposal p = new PatchProposal("p1", List.of("t1"), "skill",
                    "add", null, "rule", "weak", 0.3, List.of());
            var merged = agent.mergeProposals(List.of(p));
            assertTrue(merged.mergedProposals().isEmpty());
            assertEquals(1, merged.discarded().size());
        }

        @Test
        void merge_conflictResolution_higherConfidenceWins() {
            PatchProposal p1 = new PatchProposal("p1", List.of("t1"), "skill",
                    "modify", "rule_1", "A", "rA", 0.9, List.of());
            PatchProposal p2 = new PatchProposal("p2", List.of("t2"), "skill",
                    "modify", "rule_1", "B", "rB", 0.5, List.of());
            var merged = agent.mergeProposals(List.of(p1, p2));
            assertEquals(1, merged.mergedProposals().size());
            assertEquals("p1", merged.mergedProposals().get(0).proposalId());
        }

        @Test
        void merge_sortedByConfidence() {
            PatchProposal p1 = new PatchProposal("p1", List.of("t1"), "skill",
                    "add", null, "r1", "r", 0.7, List.of());
            PatchProposal p2 = new PatchProposal("p2", List.of("t2"), "skill",
                    "add", "rule_2", "r2", "r", 0.9, List.of());
            var merged = agent.mergeProposals(List.of(p1, p2));
            assertEquals(2, merged.mergedProposals().size());
            assertEquals("p2", merged.mergedProposals().get(0).proposalId());
        }
    }

    @Nested
    class AuditLogTests {
        @TempDir
        Path tmpDir;
        private EvolutionAuditLog auditLog;

        @BeforeEach
        void setup() {
            auditLog = new EvolutionAuditLog(tmpDir.resolve("audit.jsonl"));
        }

        @Test
        void writeAndRead_singleEntry() {
            auditLog.writeAuditEntry("p1", "skill-a", "accept",
                    "add", null, "diff", "reason", 0.85, null);
            var entries = auditLog.readAuditLog();
            assertEquals(1, entries.size());
            assertEquals("skill-a", entries.get(0).skillName());
            assertEquals("accept", entries.get(0).action());
        }

        @Test
        void writeAndRead_multiple() {
            for (int i = 0; i < 5; i++) {
                auditLog.writeAuditEntry("p" + i, "s" + (i % 2), "accept",
                        "add", null, "", "", 0.5, null);
            }
            assertEquals(5, auditLog.readAuditLog().size());
        }

        @Test
        void read_filterBySkill() {
            auditLog.writeAuditEntry("p1", "a", "accept", "add", null, "", "", 0.5, null);
            auditLog.writeAuditEntry("p2", "b", "reject", "modify", "r1", "", "", 0.3, "low");
            auditLog.writeAuditEntry("p3", "a", "accept", "add", null, "", "", 0.7, null);
            assertEquals(2, auditLog.readAuditLog("a").size());
        }

        @Test
        void read_respectsLimit() {
            for (int i = 0; i < 10; i++) {
                auditLog.writeAuditEntry("p" + i, "s", "accept", "add", null, "", "", 0.5, null);
            }
            assertEquals(3, auditLog.readAuditLog(null, 3).size());
        }

        @Test
        void read_nonexistent_empty() {
            EvolutionAuditLog empty = new EvolutionAuditLog(tmpDir.resolve("none.jsonl"));
            assertTrue(empty.readAuditLog().isEmpty());
        }

        @Test
        void rejectWithReason() {
            auditLog.writeAuditEntry("p1", "s", "reject", "modify", "r1",
                    "diff", "tried", 0.2, "score decreased");
            var entries = auditLog.readAuditLog();
            assertEquals("reject", entries.get(0).action());
            assertEquals("score decreased", entries.get(0).rejectReason());
        }

        @Test
        void autoGeneratesId() {
            AuditEntry e = new AuditEntry(null, 0, "p1", "s", "accept",
                    "add", null, "", "", 0.5, null);
            assertFalse(e.auditId().isBlank());
            assertTrue(e.timestamp() > 0);
        }

        @Test
        void truncatesLongFields() {
            AuditEntry e = new AuditEntry(null, 0, "p1", "s", "accept",
                    "add", null, "x".repeat(300), "y".repeat(600), 0.5, null);
            Map<String, Object> m = e.toMap();
            assertTrue(((String) m.get("diff_summary")).length() <= 200);
            assertTrue(((String) m.get("rationale")).length() <= 500);
        }
    }
}
