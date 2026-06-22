package io.agentcore.companion;

import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DailyRhythm and GuideNPC companion modules.
 */
class CompanionAdvancedTest {

    // ── CompanionMemory thread safety and capacity tests ────────────────

    @Nested
    class CompanionMemoryTests {

        @Test
        void observeAndRecall() {
            var memory = new CompanionMemory();
            memory.observe("u1", new CompanionMemory.Observation("test", 1000, "hello", Map.of()));
            memory.observe("u1", new CompanionMemory.Observation("test", 2000, "world", Map.of()));

            var results = memory.recall("u1", 10);
            assertEquals(2, results.size());
            assertEquals("hello", results.get(0).summary());
        }

        @Test
        void recall_limitReturnsRecent() {
            var memory = new CompanionMemory();
            for (int i = 0; i < 10; i++) {
                memory.observe("u1", new CompanionMemory.Observation("test", i, "msg-" + i, Map.of()));
            }

            var results = memory.recall("u1", 3);
            assertEquals(3, results.size());
            assertEquals("msg-7", results.get(0).summary());
            assertEquals("msg-9", results.get(2).summary());
        }

        @Test
        void recall_emptyUser() {
            var memory = new CompanionMemory();
            assertTrue(memory.recall("nonexistent", 5).isEmpty());
        }

        @Test
        void capacity_maxObsPerUser() {
            var memory = new CompanionMemory();
            // Add 250 observations (max is 200)
            for (int i = 0; i < 250; i++) {
                memory.observe("u1", new CompanionMemory.Observation("test", i, "msg-" + i, Map.of()));
            }

            assertEquals(200, memory.count("u1"));
            // Oldest should have been removed
            var all = memory.recallAll("u1");
            assertEquals("msg-50", all.get(0).summary()); // first 50 removed
        }

        @Test
        void clear_removesUserData() {
            var memory = new CompanionMemory();
            memory.observe("u1", new CompanionMemory.Observation("test", 0, "data", Map.of()));
            memory.clear("u1");
            assertEquals(0, memory.count("u1"));
        }

        @Test
        void concurrentObserve_threadSafe() throws Exception {
            var memory = new CompanionMemory();
            int count = 100;
            ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor();
            try {
                for (int i = 0; i < count; i++) {
                    final int idx = i;
                    exec.submit(() -> memory.observe("u1",
                            new CompanionMemory.Observation("test", idx, "msg-" + idx, Map.of())));
                }
            } finally {
                exec.shutdown();
                exec.awaitTermination(5, TimeUnit.SECONDS);
            }
            assertEquals(count, memory.count("u1"));
        }
    }

    // ── DailyRhythm tests ─────────────────────────────────────────────

    @Nested
    class DailyRhythmTests {

        @Test
        void getPeriod_allHours_returnsValidPeriod() {
            for (int h = 0; h < 24; h++) {
                DailyRhythm.Period p = DailyRhythm.getPeriod(h);
                assertNotNull(p, "Hour " + h + " should map to a period");
            }
        }

        @Test
        void getPeriod_morning() {
            assertEquals(DailyRhythm.Period.MORNING, DailyRhythm.getPeriod(6));
            assertEquals(DailyRhythm.Period.MORNING, DailyRhythm.getPeriod(8));
        }

        @Test
        void getPeriod_forenoon() {
            assertEquals(DailyRhythm.Period.FORENOON, DailyRhythm.getPeriod(9));
            assertEquals(DailyRhythm.Period.FORENOON, DailyRhythm.getPeriod(11));
        }

        @Test
        void getPeriod_noon() {
            assertEquals(DailyRhythm.Period.NOON, DailyRhythm.getPeriod(12));
            assertEquals(DailyRhythm.Period.NOON, DailyRhythm.getPeriod(13));
        }

        @Test
        void getPeriod_afternoon() {
            assertEquals(DailyRhythm.Period.AFTERNOON, DailyRhythm.getPeriod(14));
            assertEquals(DailyRhythm.Period.AFTERNOON, DailyRhythm.getPeriod(17));
        }

        @Test
        void getPeriod_evening() {
            assertEquals(DailyRhythm.Period.EVENING, DailyRhythm.getPeriod(18));
            assertEquals(DailyRhythm.Period.EVENING, DailyRhythm.getPeriod(19));
        }

        @Test
        void getPeriod_night() {
            assertEquals(DailyRhythm.Period.NIGHT, DailyRhythm.getPeriod(20));
            assertEquals(DailyRhythm.Period.NIGHT, DailyRhythm.getPeriod(22));
        }

        @Test
        void getPeriod_lateNight_overnight() {
            assertEquals(DailyRhythm.Period.LATE_NIGHT, DailyRhythm.getPeriod(23));
            assertEquals(DailyRhythm.Period.LATE_NIGHT, DailyRhythm.getPeriod(0));
            assertEquals(DailyRhythm.Period.LATE_NIGHT, DailyRhythm.getPeriod(5));
        }

        @Test
        void energyMultiplier_noQuirk_forenoon() {
            assertEquals(1.0, DailyRhythm.energyMultiplier(10, ""));
        }

        @Test
        void energyMultiplier_noQuirk_evening() {
            assertEquals(1.3, DailyRhythm.energyMultiplier(19, ""));
        }

        @Test
        void energyMultiplier_nightOwl_morning_low() {
            assertEquals(0.4, DailyRhythm.energyMultiplier(7, "night_owl"));
        }

        @Test
        void energyMultiplier_nightOwl_night_high() {
            assertEquals(1.3, DailyRhythm.energyMultiplier(21, "night_owl"));
        }

        @Test
        void zzzThreshold_noQuirk_forenoon() {
            assertEquals(30, DailyRhythm.zzzThresholdMinutes(10, ""));
        }

        @Test
        void zzzThreshold_sleepyhead_halved() {
            assertEquals(15, DailyRhythm.zzzThresholdMinutes(10, "sleepyhead"));
        }

        @Test
        void zzzThreshold_nightOwl_evening_doubled() {
            // evening base=60, night_owl energy>=1.0 → doubled
            assertEquals(120, DailyRhythm.zzzThresholdMinutes(19, "night_owl"));
        }

        @Test
        void zzzThreshold_nightOwl_morning_normal() {
            // morning base=15, night_owl energy=0.4 (<1.0) → no change
            assertEquals(15, DailyRhythm.zzzThresholdMinutes(7, "night_owl"));
        }

        @Test
        void bubbleFrequency_afternoon() {
            assertEquals(1.0, DailyRhythm.bubbleFrequencyMultiplier(15));
        }

        @Test
        void bubbleFrequency_evening_high() {
            assertEquals(1.5, DailyRhythm.bubbleFrequencyMultiplier(19));
        }

        @Test
        void bubbleFrequency_lateNight_low() {
            assertEquals(0.2, DailyRhythm.bubbleFrequencyMultiplier(1));
        }

        @Test
        void idleMood_noon_sleeping() {
            assertEquals("sleeping", DailyRhythm.idleMood(DailyRhythm.Period.NOON));
        }

        @Test
        void idleMood_lateNight_sleeping() {
            assertEquals("sleeping", DailyRhythm.idleMood(DailyRhythm.Period.LATE_NIGHT));
        }

        @Test
        void idleMood_forenoon_awake() {
            assertEquals("awake", DailyRhythm.idleMood(DailyRhythm.Period.FORENOON));
        }
    }

    // ── GuideNPC tests ────────────────────────────────────────────────

    @Nested
    class GuideNPCTests {

        private CompanionMemory memory;
        private SilentObserver observer;

        @BeforeEach
        void setup() {
            memory = new CompanionMemory();
            observer = new SilentObserver(memory);
        }

        @Test
        void decideBubble_onboarding_firstPrompt() {
            observer.onSessionStart("user1");
            observer.onPrompt("user1", "hello");

            GuideNPC guide = new GuideNPC(memory, observer, "orange_tabby");
            CompanionBubble bubble = guide.decideBubble("user1");

            assertNotNull(bubble);
            assertEquals("onboarding", bubble.priority());
        }

        @Test
        void decideBubble_greeting_afterOnboarding() {
            observer.onSessionStart("user1");
            // 3 prompts to pass onboarding
            observer.onPrompt("user1", "prompt1");
            observer.onPrompt("user1", "prompt2");
            observer.onPrompt("user1", "prompt3");

            GuideNPC guide = new GuideNPC(memory, observer, "orange_tabby");
            CompanionBubble bubble = guide.decideBubble("user1");

            assertNotNull(bubble);
            assertEquals("greeting", bubble.priority());
            assertTrue(guide.isGreeted());
        }

        @Test
        void decideBubble_noGreeting_twice() {
            observer.onSessionStart("user1");
            for (int i = 0; i < 5; i++) observer.onPrompt("user1", "p" + i);

            GuideNPC guide = new GuideNPC(memory, observer, "orange_tabby");
            CompanionBubble first = guide.decideBubble("user1");
            assertEquals("greeting", first.priority());

            // Second call should NOT be greeting
            CompanionBubble second = guide.decideBubble("user1");
            if (second != null) {
                assertNotEquals("greeting", second.priority());
            }
        }

        @Test
        void decideBubble_repeatedTool_bash() {
            observer.onSessionStart("user1");
            for (int i = 0; i < 5; i++) observer.onPrompt("user1", "p" + i);

            GuideNPC guide = new GuideNPC(memory, observer, "orange_tabby");
            guide.decideBubble("user1"); // consume greeting

            // Use bash 3 times
            observer.onToolStart("user1", "bash");
            observer.onToolStart("user1", "bash");
            observer.onToolStart("user1", "bash");

            CompanionBubble bubble = guide.decideBubble("user1");
            assertNotNull(bubble);
            assertEquals("suggestion", bubble.priority());
        }

        @Test
        void decideBubble_defaultBreed() {
            GuideNPC guide = new GuideNPC(memory, observer);
            assertEquals("orange_tabby", guide.breed());
        }

        @Test
        void resetGreeting_clearsFlag() {
            observer.onSessionStart("user1");
            for (int i = 0; i < 5; i++) observer.onPrompt("user1", "p" + i);

            GuideNPC guide = new GuideNPC(memory, observer);
            guide.decideBubble("user1");
            assertTrue(guide.isGreeted());

            guide.resetGreeting();
            assertFalse(guide.isGreeted());

            CompanionBubble bubble = guide.decideBubble("user1");
            assertNotNull(bubble);
            assertEquals("greeting", bubble.priority());
        }
    }
}
