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

    // ── Emotion enum ──────────────────────────────────────────────────

    /**
     * Type-safe emotion states with built-in decay, eye, and mood metadata.
     */
    public enum Emotion {
        NEUTRAL(-1, null, "awake"),
        HAPPY(60.0, "♥", "happy"),
        EXCITED(15.0, "✦", "happy"),
        SLEEPY(-1, "-", "sleeping"),
        WORRIED(90.0, "◉", "concerned"),
        ANNOYED(45.0, "▼", "concerned");

        private final double decaySeconds;
        private final String eye;
        private final String mood;

        Emotion(double decaySeconds, String eye, String mood) {
            this.decaySeconds = decaySeconds;
            this.eye = eye;
            this.mood = mood;
        }

        /** Seconds before this emotion decays to NEUTRAL (-1 = never). */
        public double decaySeconds() { return decaySeconds; }

        /** Eye override for sprite rendering (null = use default). */
        public String eye() { return eye; }

        /** Frontend mood label. */
        public String mood() { return mood; }
    }

    // Backward-compatible aliases
    public static final Emotion NEUTRAL = Emotion.NEUTRAL;
    public static final Emotion HAPPY = Emotion.HAPPY;
    public static final Emotion EXCITED = Emotion.EXCITED;
    public static final Emotion SLEEPY = Emotion.SLEEPY;
    public static final Emotion WORRIED = Emotion.WORRIED;
    public static final Emotion ANNOYED = Emotion.ANNOYED;

    public static final List<Emotion> ALL_EMOTIONS = List.of(Emotion.values());

    // ── Transition table ──────────────────────────────────────────────

    private static final Map<Emotion, Map<String, Emotion>> TRANSITIONS = new EnumMap<>(Emotion.class);
    static {
        addTransitions(Emotion.NEUTRAL, Map.of(
                "pet", Emotion.HAPPY, "tool_ok", Emotion.NEUTRAL, "tool_fail", Emotion.WORRIED,
                "swear", Emotion.ANNOYED, "idle_30s", Emotion.SLEEPY, "idle_5min", Emotion.SLEEPY,
                "fail_streak_3", Emotion.WORRIED));
        addTransitions(Emotion.HAPPY, Map.of(
                "pet", Emotion.EXCITED, "tool_ok", Emotion.HAPPY, "tool_fail", Emotion.NEUTRAL,
                "swear", Emotion.ANNOYED, "idle_30s", Emotion.NEUTRAL, "idle_5min", Emotion.SLEEPY,
                "fail_streak_3", Emotion.WORRIED));
        addTransitions(Emotion.EXCITED, Map.of(
                "pet", Emotion.EXCITED, "tool_ok", Emotion.HAPPY, "tool_fail", Emotion.NEUTRAL,
                "swear", Emotion.ANNOYED, "idle_30s", Emotion.HAPPY, "idle_5min", Emotion.SLEEPY,
                "fail_streak_3", Emotion.NEUTRAL));
        addTransitions(Emotion.SLEEPY, Map.of(
                "pet", Emotion.HAPPY, "tool_ok", Emotion.NEUTRAL, "tool_fail", Emotion.WORRIED,
                "swear", Emotion.NEUTRAL, "idle_30s", Emotion.SLEEPY, "idle_5min", Emotion.SLEEPY,
                "fail_streak_3", Emotion.WORRIED));
        addTransitions(Emotion.WORRIED, Map.of(
                "pet", Emotion.HAPPY, "tool_ok", Emotion.HAPPY, "tool_fail", Emotion.WORRIED,
                "swear", Emotion.ANNOYED, "idle_30s", Emotion.NEUTRAL, "idle_5min", Emotion.SLEEPY,
                "fail_streak_3", Emotion.ANNOYED));
        addTransitions(Emotion.ANNOYED, Map.of(
                "pet", Emotion.NEUTRAL, "tool_ok", Emotion.NEUTRAL, "tool_fail", Emotion.ANNOYED,
                "swear", Emotion.ANNOYED, "idle_30s", Emotion.NEUTRAL, "idle_5min", Emotion.SLEEPY,
                "fail_streak_3", Emotion.ANNOYED));
    }

    private static void addTransitions(Emotion from, Map<String, Emotion> events) {
        TRANSITIONS.put(from, events);
    }

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

    private Emotion emotion = Emotion.NEUTRAL;
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

    public Emotion getEmotion()     { return emotion; }
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

        Emotion target = applyModifiers(effectiveEvent);
        if (target != null && target != emotion) {
            Emotion old = emotion;
            emotion = target;
            applyMoodChange(effectiveEvent);
            updateDerived();
            changes.add(old + "→" + target);
        }

        return changes;
    }

    private Emotion applyModifiers(String eventType) {
        Map<String, Emotion> events = TRANSITIONS.get(emotion);
        if (events == null) return null;
        Emotion target = events.get(eventType);
        if (target == null) return null;

        double resist = params.getOrDefault("annoy_resistance", 50.0) / 100.0;
        double exciteEase = params.getOrDefault("excite_ease", 50.0);
        double worryTendency = params.getOrDefault("worry_tendency", 50.0) / 100.0;

        // annoy_resistance: reduce chance of ANNOYED transitions
        if (target == Emotion.ANNOYED) {
            if (random.nextDouble() > (1.0 - resist * 0.5)) {
                return emotion;  // resisted
            }
        }

        // excite_ease: NEUTRAL→HAPPY can skip to EXCITED
        if (target == Emotion.HAPPY && emotion == Emotion.NEUTRAL && exciteEase > 70) {
            return Emotion.EXCITED;
        }

        // worry_tendency: NEUTRAL→WORRIED more likely
        if (target == Emotion.WORRIED && worryTendency < 0.3 && random.nextDouble() < 0.5) {
            return emotion;  // too carefree to worry
        }

        return target;
    }

    private void applyMoodChange(String eventType) {
        int delta = MOOD_DELTAS.getOrDefault(eventType, 0);
        mood = Math.max(0, Math.min(100, mood + delta));
    }

    private void updateDerived() {
        eyeOverride = emotion.eye();
        frontendMood = emotion.mood();
    }

    // ── Decay ─────────────────────────────────────────────────────────

    /**
     * Check and apply natural decay. Call per tick or per TurnEnd.
     */
    public List<String> checkDecay(double idleSeconds) {
        List<String> changes = new ArrayList<>();

        if (emotion == Emotion.NEUTRAL || emotion == Emotion.SLEEPY) return changes;

        double decayS = emotion.decaySeconds();
        if (decayS <= 0) return changes;

        if (idleSeconds >= decayS) {
            Emotion old = emotion;
            emotion = Emotion.NEUTRAL;
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
        if (idleSeconds >= 300 && emotion != Emotion.SLEEPY) {
            Emotion old = emotion;
            emotion = Emotion.SLEEPY;
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
