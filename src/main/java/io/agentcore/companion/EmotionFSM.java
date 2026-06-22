package io.agentcore.companion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Emotion state machine — 6-state FSM with transitions, decay, and stacking.
 *
 * <p>Mirrors Python {@code agent_core/companion/state_machine.py}.
 * Input: agent events (pet, tool_ok, tool_fail, swear, idle_30s, idle_5min, fail_streak_3)
 * Output: current emotion + eye override for sprite rendering.
 */
public final class EmotionFSM {

    private static final Logger log = LoggerFactory.getLogger(EmotionFSM.class);

    // ── Emotion states ────────────────────────────────────────────────

    public static final String NEUTRAL  = "NEUTRAL";
    public static final String HAPPY    = "HAPPY";
    public static final String EXCITED  = "EXCITED";
    public static final String SLEEPY   = "SLEEPY";
    public static final String WORRIED  = "WORRIED";
    public static final String ANNOYED  = "ANNOYED";

    public static final List<String> ALL_EMOTIONS =
            List.of(NEUTRAL, HAPPY, EXCITED, SLEEPY, WORRIED, ANNOYED);

    // ── Transition table ──────────────────────────────────────────────

    private static final Map<String, Map<String, String>> TRANSITIONS = new LinkedHashMap<>();
    static {
        addTransitions(NEUTRAL, Map.of(
                "pet", HAPPY, "tool_ok", NEUTRAL, "tool_fail", WORRIED,
                "swear", ANNOYED, "idle_30s", SLEEPY, "idle_5min", SLEEPY,
                "fail_streak_3", WORRIED));
        addTransitions(HAPPY, Map.of(
                "pet", EXCITED, "tool_ok", HAPPY, "tool_fail", NEUTRAL,
                "swear", ANNOYED, "idle_30s", NEUTRAL, "idle_5min", SLEEPY,
                "fail_streak_3", WORRIED));
        addTransitions(EXCITED, Map.of(
                "pet", EXCITED, "tool_ok", HAPPY, "tool_fail", NEUTRAL,
                "swear", ANNOYED, "idle_30s", HAPPY, "idle_5min", SLEEPY,
                "fail_streak_3", NEUTRAL));
        addTransitions(SLEEPY, Map.of(
                "pet", HAPPY, "tool_ok", NEUTRAL, "tool_fail", WORRIED,
                "swear", NEUTRAL, "idle_30s", SLEEPY, "idle_5min", SLEEPY,
                "fail_streak_3", WORRIED));
        addTransitions(WORRIED, Map.of(
                "pet", HAPPY, "tool_ok", HAPPY, "tool_fail", WORRIED,
                "swear", ANNOYED, "idle_30s", NEUTRAL, "idle_5min", SLEEPY,
                "fail_streak_3", ANNOYED));
        addTransitions(ANNOYED, Map.of(
                "pet", NEUTRAL, "tool_ok", NEUTRAL, "tool_fail", ANNOYED,
                "swear", ANNOYED, "idle_30s", NEUTRAL, "idle_5min", SLEEPY,
                "fail_streak_3", ANNOYED));
    }

    private static void addTransitions(String from, Map<String, String> events) {
        TRANSITIONS.put(from, events);
    }

    // ── Decay ─────────────────────────────────────────────────────────

    private static final Map<String, Double> DECAY_SECONDS = Map.of(
            EXCITED, 15.0,
            HAPPY, 60.0,
            WORRIED, 90.0,
            ANNOYED, 45.0,
            SLEEPY, -1.0);  // never auto-decay

    // ── Eye override per emotion ──────────────────────────────────────

    private static final Map<String, String> EMOTION_EYE = Map.of(
            NEUTRAL, "",   // null → use bones.eye
            HAPPY, "♥",
            EXCITED, "✦",
            SLEEPY, "-",
            WORRIED, "◉",
            ANNOYED, "▼");

    // ── Emotion → frontend mood ───────────────────────────────────────

    private static final Map<String, String> EMOTION_TO_MOOD = Map.of(
            NEUTRAL, "awake",
            HAPPY, "happy",
            EXCITED, "happy",
            SLEEPY, "sleeping",
            WORRIED, "concerned",
            ANNOYED, "concerned");

    // ── Breed emotional params ────────────────────────────────────────

    private static final Map<String, Map<String, Double>> BREED_EMOTION_PARAMS = Map.of(
            "orange_tabby", Map.of("annoy_resistance", 85.0, "excite_ease", 45.0, "worry_tendency", 25.0),
            "tuxedo",       Map.of("annoy_resistance", 60.0, "excite_ease", 80.0, "worry_tendency", 20.0),
            "calico",       Map.of("annoy_resistance", 40.0, "excite_ease", 55.0, "worry_tendency", 50.0),
            "siamese",      Map.of("annoy_resistance", 55.0, "excite_ease", 70.0, "worry_tendency", 60.0),
            "black_cat",    Map.of("annoy_resistance", 70.0, "excite_ease", 30.0, "worry_tendency", 40.0),
            "ragdoll",      Map.of("annoy_resistance", 90.0, "excite_ease", 55.0, "worry_tendency", 70.0),
            "scottish_fold",Map.of("annoy_resistance", 80.0, "excite_ease", 35.0, "worry_tendency", 60.0));

    // ── Mood deltas ───────────────────────────────────────────────────

    private static final Map<String, Integer> MOOD_DELTAS = Map.of(
            "pet", 20, "tool_ok", 5, "tool_fail", -10,
            "swear", -25, "fail_streak_3", -20,
            "idle_30s", 0, "idle_5min", 0);

    // ── State ─────────────────────────────────────────────────────────

    private String emotion = NEUTRAL;
    private double mood = 50.0;
    private String lastEvent = "";
    private long lastEventAt = 0;
    private final Map<String, Integer> streaks = new HashMap<>();
    private String eyeOverride = null;
    private String frontendMood = "awake";
    private final String breed;
    private final Map<String, Double> params;
    private final Random random;

    public EmotionFSM() {
        this("orange_tabby");
    }

    public EmotionFSM(String breed) {
        this(breed, ThreadLocalRandom.current());
    }

    public EmotionFSM(String breed, Random random) {
        this.breed = breed;
        this.params = BREED_EMOTION_PARAMS.getOrDefault(breed,
                BREED_EMOTION_PARAMS.get("orange_tabby"));
        this.random = random;
    }

    // ── Read state ────────────────────────────────────────────────────

    public String getEmotion()      { return emotion; }
    public double getMood()         { return mood; }
    public String getEyeOverride()  { return eyeOverride; }
    public String getFrontendMood() { return frontendMood; }
    public String getBreed()        { return breed; }

    // ── Event processing ──────────────────────────────────────────────

    /**
     * Feed an event, return list of significant changes (for logging).
     */
    public List<String> process(String eventType) {
        List<String> changes = new ArrayList<>();

        long now = System.currentTimeMillis() / 1000;
        if (lastEvent.equals(eventType) && (now - lastEventAt) < 30) {
            streaks.merge(eventType, 1, Integer::sum);
        } else {
            streaks.put(eventType, 1);
        }
        lastEvent = eventType;
        lastEventAt = now;

        // Check streak threshold
        String effectiveEvent = eventType;
        if ("tool_fail".equals(eventType) && streaks.getOrDefault("tool_fail", 0) >= 3) {
            effectiveEvent = "fail_streak_3";
        }

        String target = applyModifiers(effectiveEvent);
        if (target != null && !target.equals(emotion)) {
            String old = emotion;
            emotion = target;
            applyMoodChange(effectiveEvent);
            updateDerived();
            changes.add(old + "→" + target);
        }

        return changes;
    }

    private String applyModifiers(String eventType) {
        Map<String, String> events = TRANSITIONS.get(emotion);
        if (events == null) return null;
        String target = events.get(eventType);
        if (target == null) return null;

        double resist = params.getOrDefault("annoy_resistance", 50.0) / 100.0;
        double exciteEase = params.getOrDefault("excite_ease", 50.0);
        double worryTendency = params.getOrDefault("worry_tendency", 50.0) / 100.0;

        // annoy_resistance: reduce chance of ANNOYED transitions
        if (ANNOYED.equals(target)) {
            if (random.nextDouble() > (1.0 - resist * 0.5)) {
                return emotion;  // resisted
            }
        }

        // excite_ease: NEUTRAL→HAPPY can skip to EXCITED
        if (HAPPY.equals(target) && NEUTRAL.equals(emotion) && exciteEase > 70) {
            return EXCITED;
        }

        // worry_tendency: NEUTRAL→WORRIED more likely
        if (WORRIED.equals(target) && worryTendency < 0.3 && random.nextDouble() < 0.5) {
            return emotion;  // too carefree to worry
        }

        return target;
    }

    private void applyMoodChange(String eventType) {
        int delta = MOOD_DELTAS.getOrDefault(eventType, 0);
        mood = Math.max(0, Math.min(100, mood + delta));
    }

    private void updateDerived() {
        eyeOverride = EMOTION_EYE.getOrDefault(emotion, null);
        if (eyeOverride != null && eyeOverride.isEmpty()) eyeOverride = null;
        frontendMood = EMOTION_TO_MOOD.getOrDefault(emotion, "awake");
    }

    // ── Decay ─────────────────────────────────────────────────────────

    /**
     * Check and apply natural decay. Call per tick or per TurnEnd.
     */
    public List<String> checkDecay(double idleSeconds) {
        List<String> changes = new ArrayList<>();

        if (NEUTRAL.equals(emotion) || SLEEPY.equals(emotion)) return changes;

        double decayS = DECAY_SECONDS.getOrDefault(emotion, -1.0);
        if (decayS <= 0) return changes;

        if (idleSeconds >= decayS) {
            String old = emotion;
            emotion = NEUTRAL;
            mood = 50;
            updateDerived();
            changes.add(old + "→NEUTRAL (decay)");
        }
        return changes;
    }

    // ── Idle ──────────────────────────────────────────────────────────

    /**
     * Mark idle duration, trigger SLEEPY if needed.
     */
    public List<String> markIdle(double idleSeconds) {
        List<String> changes = new ArrayList<>();
        if (idleSeconds >= 300 && !SLEEPY.equals(emotion)) {
            String old = emotion;
            emotion = SLEEPY;
            mood = Math.max(0, mood - 20);
            updateDerived();
            changes.add(old + "→SLEEPY (idle " + String.format("%.0f", idleSeconds) + "s)");
        }
        return changes;
    }

    // ── Serialization ─────────────────────────────────────────────────

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("emotion", emotion);
        map.put("mood", mood);
        map.put("eye_override", eyeOverride);
        map.put("frontend_mood", frontendMood);
        return map;
    }
}
