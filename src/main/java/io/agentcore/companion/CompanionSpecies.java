package io.agentcore.companion;

import java.util.List;
import java.util.Map;

/**
 * Companion species definitions — breeds, rarity, stats, quirks, visual traits.
 *
 * <p>Mirrors Python {@code agent_core/companion/species.py}.
 * All lists are append-only: never delete, insert, or reorder existing entries.
 */
public final class CompanionSpecies {

    private CompanionSpecies() {}

    // ── rarity ─────────────────────────────────────────────────────────

    public static final Map<String, Integer> RARITY_WEIGHTS = Map.ofEntries(
            Map.entry("common", 60),
            Map.entry("uncommon", 25),
            Map.entry("rare", 10),
            Map.entry("epic", 4),
            Map.entry("legendary", 1));

    public static final Map<String, String> RARITY_COLORS = Map.of(
            "common", "#8e8e93",
            "uncommon", "#30d158",
            "rare", "#409cff",
            "epic", "#bf5af2",
            "legendary", "#ff9f0a");

    public static final Map<String, String> RARITY_STARS = Map.of(
            "common", "*",
            "uncommon", "**",
            "rare", "***",
            "epic", "****",
            "legendary", "*****");

    public static final List<String> RARITIES =
            List.of("common", "uncommon", "rare", "epic", "legendary");

    // ── breeds (append-only) ──────────────────────────────────────────

    public static final Map<String, Integer> BREED_WEIGHTS = Map.ofEntries(
            Map.entry("orange_tabby", 30),
            Map.entry("tuxedo", 20),
            Map.entry("calico", 15),
            Map.entry("siamese", 15),
            Map.entry("black_cat", 10),
            Map.entry("ragdoll", 8),
            Map.entry("scottish_fold", 2));

    public static final Map<String, String> BREED_NAMES = Map.of(
            "orange_tabby", "橘猫",
            "tuxedo", "奶牛猫",
            "calico", "三花猫",
            "siamese", "暹罗猫",
            "black_cat", "黑猫",
            "ragdoll", "布偶猫",
            "scottish_fold", "折耳猫");

    /** Minimum rarity rank required to unlock this breed (null = no gate). */
    public static final Map<String, String> BREED_RARITY_GATE = Map.of(
            "calico", "uncommon",
            "ragdoll", "rare",
            "scottish_fold", "epic");

    public static final List<String> BREEDS =
            List.of("orange_tabby", "tuxedo", "calico", "siamese",
                    "black_cat", "ragdoll", "scottish_fold");

    // ── stats (5-axis personality) ────────────────────────────────────

    public static final List<String> STAT_NAMES =
            List.of("CURIOSITY", "SOCIAL", "AFFECTION", "PLAYFUL", "LUCK");

    // ── quirks (append-only) ──────────────────────────────────────────

    public static final List<String> QUIRKS = List.of(
            "night_owl", "picky_eater", "chatterbox", "shy", "collector",
            "hyperactive", "sleepyhead", "glass_heart", "foodie",
            "clean_freak", "tsundere_extreme", "philosopher", "comedian");

    /** Breed-specific bonus multipliers for quirk rolls. */
    public static final Map<String, Map<String, Double>> QUIRK_BREED_BONUS = Map.ofEntries(
            Map.entry("night_owl", Map.of("black_cat", 2.0)),
            Map.entry("picky_eater", Map.of("siamese", 2.0)),
            Map.entry("chatterbox", Map.of("siamese", 3.0)),
            Map.entry("shy", Map.of("scottish_fold", 3.0, "black_cat", 2.0)),
            Map.entry("collector", Map.of()),
            Map.entry("hyperactive", Map.of("tuxedo", 2.0)),
            Map.entry("sleepyhead", Map.of("orange_tabby", 3.0)),
            Map.entry("glass_heart", Map.of("ragdoll", 2.0)),
            Map.entry("foodie", Map.of("orange_tabby", 4.0)),
            Map.entry("clean_freak", Map.of("calico", 2.0)),
            Map.entry("tsundere_extreme", Map.of("calico", 3.0)),
            Map.entry("philosopher", Map.of("black_cat", 3.0)),
            Map.entry("comedian", Map.of("tuxedo", 3.0)));

    // ── visual traits (append-only) ───────────────────────────────────

    public static final List<String> EYES = List.of("·", "✦", "◉", "o", "♥", "☆");
    public static final List<String> EARS = List.of("cat", "rabbit", "mix");
    public static final List<String> ACCENTS = List.of("none", "bow", "scarf", "glasses", "crown", "bell");
    public static final List<String> HATS = List.of(
            "none", "crown", "tophat", "propeller", "halo", "wizard", "beanie", "tinyduck");
    public static final List<String> COLOR_PALETTES = List.of(
            "warm_gray", "cool_gray", "cream", "charcoal", "snow");
}
