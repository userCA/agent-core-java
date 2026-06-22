package io.agentcore.companion;

import java.time.LocalTime;
import java.util.*;

/**
 * Daily rhythm — 7-period cycle affecting energy, mood, animation pool.
 *
 * <p>Mirrors Python {@code agent_core/companion/daily_rhythm.py}.
 * Each period has multipliers for energy, mood cap, bubble frequency,
 * and idle-to-sleep threshold. Quirk overrides adjust per-period values.
 */
public final class DailyRhythm {

    private DailyRhythm() {}

    // ── Period definition ─────────────────────────────────────────────

    public enum Period {
        MORNING, FORENOON, NOON, AFTERNOON, EVENING, NIGHT, LATE_NIGHT;
    }

    /** Half-open hour ranges [start, end). LATE_NIGHT wraps overnight. */
    private static final Map<Period, int[]> PERIOD_RANGES = new LinkedHashMap<>();
    static {
        PERIOD_RANGES.put(Period.MORNING,    new int[]{6, 9});
        PERIOD_RANGES.put(Period.FORENOON,   new int[]{9, 12});
        PERIOD_RANGES.put(Period.NOON,       new int[]{12, 14});
        PERIOD_RANGES.put(Period.AFTERNOON,  new int[]{14, 18});
        PERIOD_RANGES.put(Period.EVENING,    new int[]{18, 20});
        PERIOD_RANGES.put(Period.NIGHT,      new int[]{20, 23});
        PERIOD_RANGES.put(Period.LATE_NIGHT, new int[]{23, 6});
    }

    // ── Per-period params ─────────────────────────────────────────────

    public record PeriodParams(double energy, int moodCap, double bubbleMult, int zzzMin) {}

    private static final Map<Period, PeriodParams> PERIOD_PARAMS = Map.of(
            Period.MORNING,    new PeriodParams(0.7, 80,  0.5, 15),
            Period.FORENOON,   new PeriodParams(1.0, 100, 1.0, 30),
            Period.NOON,       new PeriodParams(0.6, 70,  0.4, 10),
            Period.AFTERNOON,  new PeriodParams(1.0, 100, 1.0, 30),
            Period.EVENING,    new PeriodParams(1.3, 100, 1.5, 60),
            Period.NIGHT,      new PeriodParams(0.9, 90,  0.8, 20),
            Period.LATE_NIGHT, new PeriodParams(0.4, 60,  0.2, 5)
    );

    // ── Quirk overrides ───────────────────────────────────────────────

    private static final Map<Period, Double> NIGHT_OWL_ENERGY = Map.of(
            Period.MORNING,    0.4,
            Period.FORENOON,   0.5,
            Period.NOON,       0.4,
            Period.EVENING,    1.0,
            Period.NIGHT,      1.3,
            Period.LATE_NIGHT, 1.0
    );

    // ── Period idle mood ──────────────────────────────────────────────

    private static final Map<Period, String> PERIOD_IDLE_MOOD = Map.of(
            Period.MORNING,    "awake",
            Period.FORENOON,   "awake",
            Period.NOON,       "sleeping",
            Period.AFTERNOON,  "awake",
            Period.EVENING,    "awake",
            Period.NIGHT,      "awake",
            Period.LATE_NIGHT, "sleeping"
    );

    // ── Public API ────────────────────────────────────────────────────

    /**
     * Return the period for a given hour (0–23). Uses current local hour if null.
     */
    public static Period getPeriod(Integer hour) {
        int h = hour != null ? hour : LocalTime.now().getHour();
        for (var entry : PERIOD_RANGES.entrySet()) {
            int start = entry.getValue()[0];
            int end = entry.getValue()[1];
            if (end > start) {
                if (h >= start && h < end) return entry.getKey();
            } else {
                // overnight range (e.g. 23–6)
                if (h >= start || h < end) return entry.getKey();
            }
        }
        return Period.FORENOON;
    }

    /**
     * Current energy multiplier considering period + quirk.
     */
    public static double energyMultiplier(String quirk) {
        Period period = getPeriod(null);
        PeriodParams params = PERIOD_PARAMS.getOrDefault(period, PERIOD_PARAMS.get(Period.FORENOON));
        double base = params.energy();

        if ("night_owl".equals(quirk)) {
            Double override = NIGHT_OWL_ENERGY.get(period);
            if (override != null) return override;
        }

        return base;
    }

    /**
     * Energy multiplier for a specific hour and quirk.
     */
    public static double energyMultiplier(int hour, String quirk) {
        Period period = getPeriod(hour);
        PeriodParams params = PERIOD_PARAMS.getOrDefault(period, PERIOD_PARAMS.get(Period.FORENOON));
        double base = params.energy();

        if ("night_owl".equals(quirk)) {
            Double override = NIGHT_OWL_ENERGY.get(period);
            if (override != null) return override;
        }

        return base;
    }

    /**
     * Minutes of idle before SLEEPY mood triggers.
     */
    public static int zzzThresholdMinutes(String quirk) {
        return zzzThresholdMinutes(getPeriod(null), quirk);
    }

    /**
     * Zzz threshold for a specific hour and quirk.
     */
    public static int zzzThresholdMinutes(int hour, String quirk) {
        return zzzThresholdMinutes(getPeriod(hour), quirk);
    }

    private static int zzzThresholdMinutes(Period period, String quirk) {
        PeriodParams params = PERIOD_PARAMS.getOrDefault(period, PERIOD_PARAMS.get(Period.FORENOON));
        int base = params.zzzMin();

        if ("sleepyhead".equals(quirk)) {
            return Math.max(1, (int) (base * 0.5));
        }
        if ("night_owl".equals(quirk)) {
            Double energyOverride = NIGHT_OWL_ENERGY.get(period);
            if (energyOverride != null && energyOverride >= 1.0) {
                return base * 2;
            }
        }

        return base;
    }

    /**
     * Adjust bubble frequency by time of day.
     */
    public static double bubbleFrequencyMultiplier(String quirk) {
        Period period = getPeriod(null);
        return PERIOD_PARAMS.getOrDefault(period, PERIOD_PARAMS.get(Period.FORENOON)).bubbleMult();
    }

    /**
     * Bubble frequency multiplier for a specific hour.
     */
    public static double bubbleFrequencyMultiplier(int hour) {
        Period period = getPeriod(hour);
        return PERIOD_PARAMS.getOrDefault(period, PERIOD_PARAMS.get(Period.FORENOON)).bubbleMult();
    }

    /**
     * All current period params, with quirk applied.
     */
    public static PeriodParams currentParams(String quirk) {
        Period period = getPeriod(null);
        PeriodParams base = PERIOD_PARAMS.getOrDefault(period, PERIOD_PARAMS.get(Period.FORENOON));

        if ("night_owl".equals(quirk)) {
            Double energyOv = NIGHT_OWL_ENERGY.get(period);
            if (energyOv != null) {
                return new PeriodParams(energyOv, base.moodCap(), base.bubbleMult(), base.zzzMin());
            }
        }
        if ("sleepyhead".equals(quirk)) {
            return new PeriodParams(base.energy(), base.moodCap(), base.bubbleMult(),
                    Math.max(1, (int) (base.zzzMin() * 0.5)));
        }

        return base;
    }

    /**
     * Idle mood label for a period.
     */
    public static String idleMood(Period period) {
        return PERIOD_IDLE_MOOD.getOrDefault(period, "awake");
    }
}
