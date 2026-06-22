package io.agentcore.companion;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Nested;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the companion package.
 */
class CompanionTest {

    // ── CompanionBones ────────────────────────────────────────────────────

    @Nested
    class BonesTests {

        @Test
        void hashUid_isDeterministic() {
            long h1 = CompanionBones.hashUid("alice");
            long h2 = CompanionBones.hashUid("alice");
            assertEquals(h1, h2);
        }

        @Test
        void hashUid_differentUidsProduceDifferentHashes() {
            long h1 = CompanionBones.hashUid("alice");
            long h2 = CompanionBones.hashUid("bob");
            assertNotEquals(h1, h2);
        }

        @Test
        void mulberry32_producesValuesInRange() {
            var rng = CompanionBones.mulberry32(12345L);
            for (int i = 0; i < 100; i++) {
                double v = rng.getAsDouble();
                assertTrue(v >= 0.0 && v < 1.0, "rng value out of range: " + v);
            }
        }

        @Test
        void mulberry32_isDeterministic() {
            var rng1 = CompanionBones.mulberry32(99999L);
            var rng2 = CompanionBones.mulberry32(99999L);
            for (int i = 0; i < 20; i++) {
                assertEquals(rng1.getAsDouble(), rng2.getAsDouble());
            }
        }

        @Test
        void rollCompanion_isDeterministic() {
            CompanionBones.Bones b1 = CompanionBones.rollCompanion("user-abc");
            CompanionBones.Bones b2 = CompanionBones.rollCompanion("user-abc");
            assertEquals(b1.breed(), b2.breed());
            assertEquals(b1.rarity(), b2.rarity());
            assertEquals(b1.eye(), b2.eye());
            assertEquals(b1.shiny(), b2.shiny());
            assertEquals(b1.stats(), b2.stats());
        }

        @Test
        void rollCompanion_differentUidsProduceDifferentCompanions() {
            CompanionBones.Bones b1 = CompanionBones.rollCompanion("alice");
            CompanionBones.Bones b2 = CompanionBones.rollCompanion("bob");
            // At least one field should differ (extremely unlikely to be identical)
            boolean anyDiff = !b1.breed().equals(b2.breed())
                    || !b1.rarity().equals(b2.rarity())
                    || !b1.eye().equals(b2.eye())
                    || !b1.stats().equals(b2.stats());
            assertTrue(anyDiff, "Different uids should produce different companions");
        }

        @Test
        void rollCompanion_producesValidBreed() {
            CompanionBones.Bones b = CompanionBones.rollCompanion("test-user");
            assertTrue(CompanionSpecies.BREEDS.contains(b.breed()),
                    "breed should be in BREEDS list, got: " + b.breed());
        }

        @Test
        void rollCompanion_producesValidRarity() {
            CompanionBones.Bones b = CompanionBones.rollCompanion("test-user");
            assertTrue(CompanionSpecies.RARITIES.contains(b.rarity()),
                    "rarity should be in RARITIES list, got: " + b.rarity());
        }

        @Test
        void rollCompanion_statsWithinRange() {
            CompanionBones.Bones b = CompanionBones.rollCompanion("stats-test");
            for (Map.Entry<String, Integer> e : b.stats().entrySet()) {
                assertTrue(e.getValue() >= 1 && e.getValue() <= 100,
                        "stat " + e.getKey() + " out of range: " + e.getValue());
            }
        }

        @Test
        void bones_toMap_containsAllFields() {
            CompanionBones.Bones b = CompanionBones.rollCompanion("map-test");
            Map<String, Object> m = b.toMap();
            assertTrue(m.containsKey("uid"));
            assertTrue(m.containsKey("breed"));
            assertTrue(m.containsKey("rarity"));
            assertTrue(m.containsKey("stats"));
            assertEquals("map-test", m.get("uid"));
        }
    }

    // ── EmotionFSM ────────────────────────────────────────────────────────

    @Nested
    class EmotionTests {

        @Test
        void initialState_isNeutral() {
            EmotionFSM fsm = new EmotionFSM();
            assertEquals(EmotionFSM.NEUTRAL, fsm.getEmotion());
            assertEquals(50.0, fsm.getMood());
            assertEquals("awake", fsm.getFrontendMood());
        }

        @Test
        void pet_fromNeutral_transitionsToHappy() {
            // Use a Random that won't trigger annoy resistance
            EmotionFSM fsm = new EmotionFSM("orange_tabby", new Random(0));
            List<String> changes = fsm.process("pet");
            // Either HAPPY or EXCITED (if excite_ease kicks in) — orange_tabby excite_ease=45 (not >70)
            assertTrue(fsm.getEmotion().equals(EmotionFSM.HAPPY)
                    || fsm.getEmotion().equals(EmotionFSM.EXCITED),
                    "Expected HAPPY or EXCITED, got: " + fsm.getEmotion());
        }

        @Test
        void toolFail_decreasesMood() {
            EmotionFSM fsm = new EmotionFSM("orange_tabby", new Random(0));
            double before = fsm.getMood();
            fsm.process("tool_fail");
            assertTrue(fsm.getMood() <= before, "mood should decrease after tool_fail");
        }

        @Test
        void checkDecay_happyDecaysAfter60s() {
            EmotionFSM fsm = new EmotionFSM("orange_tabby", new Random(0));
            fsm.process("pet");  // → HAPPY
            assertEquals(EmotionFSM.HAPPY, fsm.getEmotion());
            List<String> decay = fsm.checkDecay(70.0);
            assertFalse(decay.isEmpty(), "should decay after 70s idle");
            assertEquals(EmotionFSM.NEUTRAL, fsm.getEmotion());
        }

        @Test
        void checkDecay_neutralDoesNotDecay() {
            EmotionFSM fsm = new EmotionFSM();
            List<String> decay = fsm.checkDecay(999.0);
            assertTrue(decay.isEmpty(), "NEUTRAL should not decay");
        }

        @Test
        void markIdle_triggersSleepyAfter300s() {
            EmotionFSM fsm = new EmotionFSM();
            List<String> changes = fsm.markIdle(400.0);
            assertFalse(changes.isEmpty());
            assertEquals(EmotionFSM.SLEEPY, fsm.getEmotion());
        }

        @Test
        void markIdle_noTriggerBelow300s() {
            EmotionFSM fsm = new EmotionFSM();
            List<String> changes = fsm.markIdle(200.0);
            assertTrue(changes.isEmpty());
            assertEquals(EmotionFSM.NEUTRAL, fsm.getEmotion());
        }

        @Test
        void allEmotions_hasCorrectSize() {
            assertEquals(6, EmotionFSM.ALL_EMOTIONS.size());
        }

        @Test
        void toMap_containsEmotionAndMood() {
            EmotionFSM fsm = new EmotionFSM();
            Map<String, Object> m = fsm.toMap();
            assertEquals(EmotionFSM.NEUTRAL, m.get("emotion"));
            assertEquals(50.0, m.get("mood"));
        }
    }

    // ── CompanionTemplates ────────────────────────────────────────────────

    @Nested
    class TemplatesTests {

        @Test
        void computeBond_stranger_lowScore() {
            CompanionTemplates.BondLevel bond = CompanionTemplates.computeBond(0, 0, 0);
            assertEquals(CompanionTemplates.BondLevel.STRANGER, bond);
        }

        @Test
        void computeBond_acquaintance_mediumScore() {
            // score = 20 + 2*10 + 5*2 = 50 → ACQUAINTANCE (10 <= 50 < 50... wait)
            // score = 20 + 10 + 10 = 40 → ACQUAINTANCE
            CompanionTemplates.BondLevel bond = CompanionTemplates.computeBond(20, 1, 5);
            assertEquals(CompanionTemplates.BondLevel.ACQUAINTANCE, bond);
        }

        @Test
        void computeBond_friend_highScore() {
            // score = 100 + 5*10 + 30*2 = 210 → FRIEND or CLOSE
            CompanionTemplates.BondLevel bond = CompanionTemplates.computeBond(100, 5, 30);
            assertTrue(bond == CompanionTemplates.BondLevel.FRIEND
                    || bond == CompanionTemplates.BondLevel.CLOSE);
        }

        @Test
        void computeBond_close_veryHighScore() {
            CompanionTemplates.BondLevel bond = CompanionTemplates.computeBond(500, 50, 365);
            assertEquals(CompanionTemplates.BondLevel.CLOSE, bond);
        }

        @Test
        void breedTalkativeness_knownBreed() {
            assertEquals(0.35, CompanionTemplates.breedTalkativeness("orange_tabby"));
            assertEquals(0.90, CompanionTemplates.breedTalkativeness("siamese"));
        }

        @Test
        void breedTalkativeness_unknownBreed_returnsDefault() {
            assertEquals(0.5, CompanionTemplates.breedTalkativeness("unknown_breed"));
        }

        @Test
        void pickGreeting_returnsNonEmpty() {
            String g = CompanionTemplates.pickGreeting(
                    CompanionTemplates.BondLevel.STRANGER, 0, "orange_tabby");
            assertNotNull(g);
            assertFalse(g.isEmpty());
        }

        @Test
        void pickIdleTip_knownBreed() {
            String tip = CompanionTemplates.pickIdleTip("siamese", 10);
            assertNotNull(tip);
            assertFalse(tip.isEmpty());
        }

        @Test
        void pickToolNote_knownBreedAndTool() {
            String note = CompanionTemplates.pickToolNote("orange_tabby", "bash");
            assertNotNull(note);
        }

        @Test
        void pickToolNote_unknownBreed_returnsNull() {
            String note = CompanionTemplates.pickToolNote("unknown_breed", "bash");
            assertNull(note);
        }
    }

    // ── CompanionMemory ────────────────────────────────────────────────────

    @Nested
    class MemoryTests {

        @Test
        void observe_and_recall() {
            CompanionMemory mem = new CompanionMemory();
            mem.observe("u1", new CompanionMemory.Observation("test", 1000L, "hello", Map.of()));
            List<CompanionMemory.Observation> obs = mem.recall("u1", 10);
            assertEquals(1, obs.size());
            assertEquals("hello", obs.getFirst().summary());
        }

        @Test
        void recall_emptyForUnknownUser() {
            CompanionMemory mem = new CompanionMemory();
            assertTrue(mem.recall("unknown", 10).isEmpty());
        }

        @Test
        void recall_limitReturnsMostRecent() {
            CompanionMemory mem = new CompanionMemory();
            for (int i = 0; i < 5; i++) {
                mem.observe("u1", new CompanionMemory.Observation("test", 1000L + i, "msg" + i, Map.of()));
            }
            List<CompanionMemory.Observation> obs = mem.recall("u1", 3);
            assertEquals(3, obs.size());
            assertEquals("msg2", obs.getFirst().summary());
        }

        @Test
        void observe_capsAt200() {
            CompanionMemory mem = new CompanionMemory();
            for (int i = 0; i < 250; i++) {
                mem.observe("u1", new CompanionMemory.Observation("test", i, "msg" + i, Map.of()));
            }
            assertEquals(200, mem.count("u1"));
            // Oldest should be msg50
            List<CompanionMemory.Observation> all = mem.recallAll("u1");
            assertEquals("msg50", all.getFirst().summary());
        }

        @Test
        void clear_removesAllObservations() {
            CompanionMemory mem = new CompanionMemory();
            mem.observe("u1", new CompanionMemory.Observation("test", 1000L, "hello", Map.of()));
            mem.clear("u1");
            assertEquals(0, mem.count("u1"));
        }

        @Test
        void observation_handlesNullFields() {
            CompanionMemory.Observation obs = new CompanionMemory.Observation(null, 0, null, null);
            assertEquals("", obs.type());
            assertEquals("", obs.summary());
            assertEquals(Map.of(), obs.metadata());
        }
    }

    // ── SilentObserver ────────────────────────────────────────────────────

    @Nested
    class ObserverTests {

        @Test
        void onSessionStart_incrementsSessionCount() {
            SilentObserver obs = new SilentObserver(new CompanionMemory());
            obs.onSessionStart("u1");
            obs.onSessionStart("u1");
            assertEquals(2, obs.getSessionCount());
        }

        @Test
        void onPrompt_incrementsPromptCount() {
            SilentObserver obs = new SilentObserver(new CompanionMemory());
            obs.onPrompt("u1", "Hello, how are you?");
            obs.onPrompt("u1", "Tell me a joke");
            assertEquals(2, obs.getPromptCount());
        }

        @Test
        void onPrompt_storesObservationInMemory() {
            CompanionMemory mem = new CompanionMemory();
            SilentObserver obs = new SilentObserver(mem);
            obs.onPrompt("u1", "What is the weather today?");
            List<CompanionMemory.Observation> recall = mem.recall("u1", 10);
            assertFalse(recall.isEmpty());
            assertEquals("user_prompt", recall.getFirst().type());
        }

        @Test
        void onToolStart_tracksRecentTools() {
            SilentObserver obs = new SilentObserver(new CompanionMemory());
            obs.onToolStart("u1", "bash");
            obs.onToolStart("u1", "read");
            obs.onToolStart("u1", "bash");
            // recentTools = [bash, read, bash]
            assertTrue(obs.repeatedTool("bash", 1));  // last tool is bash
        }

        @Test
        void extractTopicHint_firstSentence() {
            String topic = SilentObserver.extractTopicHint("What is AI? Tell me more about it.");
            assertEquals("What is AI", topic);
        }

        @Test
        void extractTopicHint_truncatesLongPrompt() {
            String longPrompt = "A".repeat(100);
            String topic = SilentObserver.extractTopicHint(longPrompt);
            assertEquals(50, topic.length());
        }

        @Test
        void extractTopicHint_nullOrBlank() {
            assertNull(SilentObserver.extractTopicHint(null));
            assertNull(SilentObserver.extractTopicHint(""));
            assertNull(SilentObserver.extractTopicHint("   "));
        }

        @Test
        void daysSinceFirstSeen_zeroBeforeSession() {
            SilentObserver obs = new SilentObserver(new CompanionMemory());
            assertEquals(0, obs.daysSinceFirstSeen());
        }

        @Test
        void getLastTopic_nullInitially() {
            SilentObserver obs = new SilentObserver(new CompanionMemory());
            assertNull(obs.getLastTopic());
        }
    }
}
