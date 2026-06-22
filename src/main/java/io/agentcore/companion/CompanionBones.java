package io.agentcore.companion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;

/**
 * Deterministic companion generation from user id.
 *
 * <p>FNV-1a hash + mulberry32 PRNG — same algorithm as Claude Code buddy.
 * Roll order is append-only: new fields must be added AFTER existing rolls
 * to preserve the PRNG sequence for existing users.
 *
 * <p>Mirrors Python {@code agent_core/companion/bones.py}.
 */
public final class CompanionBones {

    private static final Logger log = LoggerFactory.getLogger(CompanionBones.class);
    private static final String SALT = "mitu-2026-companion";

    private CompanionBones() {}

    // ── Data record ───────────────────────────────────────────────────

    public record Bones(
            String uid,
            String breed,
            String rarity,
            String eye,
            String ear,
            String accent,
            String hat,
            String quirk,
            boolean shiny,
            String color,
            Map<String, Integer> stats
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("uid", uid);
            m.put("breed", breed);
            m.put("rarity", rarity);
            m.put("eye", eye);
            m.put("ear", ear);
            m.put("accent", accent);
            m.put("hat", hat);
            m.put("quirk", quirk);
            m.put("shiny", shiny);
            m.put("color", color);
            m.put("stats", stats);
            return m;
        }
    }

    // ── Hash / PRNG ──────────────────────────────────────────────────

    static long hashUid(String uid) {
        long h = 2166136261L;
        String input = uid + SALT;
        for (int i = 0; i < input.length(); i++) {
            h ^= input.charAt(i);
            h = (h * 16777619L) & 0xFFFFFFFFL;
        }
        return h;
    }

    static DoubleSupplier mulberry32(long seed) {
        final long[] a = {seed & 0xFFFFFFFFL};
        return () -> {
            a[0] = (a[0] + 0x6D2B79F5L) & 0xFFFFFFFFL;
            long t = ((a[0] ^ (a[0] >> 15)) * (1 | a[0])) & 0xFFFFFFFFL;
            t = ((t + (t ^ (t >> 7)) * (61 | t)) ^ t) & 0xFFFFFFFFL;
            return ((t ^ (t >> 14)) & 0xFFFFFFFFL) / 4294967296.0;
        };
    }

    static String pick(DoubleSupplier rng, List<String> arr) {
        return arr.get((int) (rng.getAsDouble() * arr.size()));
    }

    // ── Rarity roll ──────────────────────────────────────────────────

    static String rollRarity(DoubleSupplier rng) {
        int total = CompanionSpecies.RARITY_WEIGHTS.values().stream().mapToInt(Integer::intValue).sum();
        double roll = rng.getAsDouble() * total;
        for (String rarity : CompanionSpecies.RARITIES) {
            roll -= CompanionSpecies.RARITY_WEIGHTS.get(rarity);
            if (roll < 0) return rarity;
        }
        return "common";
    }

    // ── Stats ────────────────────────────────────────────────────────

    static Map<String, Integer> rollStats(DoubleSupplier rng, String rarity) {
        Map<String, Integer> rarityFloor = Map.of(
                "common", 5, "uncommon", 15, "rare", 25, "epic", 35, "legendary", 50);
        int floor = rarityFloor.getOrDefault(rarity, 5);
        Map<String, Integer> stats = new LinkedHashMap<>();
        for (String name : CompanionSpecies.STAT_NAMES) {
            stats.put(name, floor + (int) (rng.getAsDouble() * 40));
        }
        int peak = (int) (rng.getAsDouble() * CompanionSpecies.STAT_NAMES.size());
        int dump = (int) (rng.getAsDouble() * CompanionSpecies.STAT_NAMES.size());
        while (dump == peak) {
            dump = (int) (rng.getAsDouble() * CompanionSpecies.STAT_NAMES.size());
        }
        String peakName = CompanionSpecies.STAT_NAMES.get(peak);
        String dumpName = CompanionSpecies.STAT_NAMES.get(dump);
        stats.put(peakName, Math.min(100, stats.get(peakName) + 50 + (int) (rng.getAsDouble() * 30)));
        stats.put(dumpName, Math.max(1, stats.get(dumpName) - 15));
        return stats;
    }

    // ── Breed ────────────────────────────────────────────────────────

    static int rarityRank(String rarity) {
        return CompanionSpecies.RARITIES.indexOf(rarity);
    }

    static String rollBreed(DoubleSupplier rng, String rarity) {
        int minRank = rarityRank(rarity);
        List<String> eligible = CompanionSpecies.BREEDS.stream()
                .filter(b -> {
                    String gate = CompanionSpecies.BREED_RARITY_GATE.get(b);
                    return gate == null || rarityRank(gate) <= minRank;
                }).toList();
        if (eligible.isEmpty()) {
            eligible = CompanionSpecies.BREEDS.stream()
                    .filter(b -> !CompanionSpecies.BREED_RARITY_GATE.containsKey(b)).toList();
        }
        int total = eligible.stream()
                .mapToInt(b -> CompanionSpecies.BREED_WEIGHTS.getOrDefault(b, 10)).sum();
        double roll = rng.getAsDouble() * total;
        for (String b : eligible) {
            roll -= CompanionSpecies.BREED_WEIGHTS.getOrDefault(b, 10);
            if (roll < 0) return b;
        }
        return eligible.getLast();
    }

    // ── Hat ──────────────────────────────────────────────────────────

    static String rollHat(DoubleSupplier rng, String rarity) {
        Map<String, Double> thresholds = Map.of(
                "common", 0.0, "uncommon", 0.3, "rare", 0.6, "epic", 0.85, "legendary", 1.0);
        double t = thresholds.getOrDefault(rarity, 0.0);
        int cutoff = (int) (CompanionSpecies.HATS.size() * t);
        if (cutoff <= 0) return "none";
        return pick(rng, CompanionSpecies.HATS.subList(0, Math.max(1, cutoff)));
    }

    // ── Quirk ────────────────────────────────────────────────────────

    static String rollQuirk(DoubleSupplier rng, String breed) {
        int baseWeight = 10;
        java.util.ArrayList<String> weighted = new java.util.ArrayList<>();
        for (String q : CompanionSpecies.QUIRKS) {
            double bonus = CompanionSpecies.QUIRK_BREED_BONUS
                    .getOrDefault(q, Map.of()).getOrDefault(breed, 1.0);
            int count = (int) (baseWeight * bonus);
            for (int i = 0; i < count; i++) weighted.add(q);
        }
        return pick(rng, weighted);
    }

    // ── Public API ───────────────────────────────────────────────────

    /**
     * Roll a deterministic companion from a user id.
     */
    public static Bones rollCompanion(String uid) {
        DoubleSupplier rng = mulberry32(hashUid(uid));
        String rarity = rollRarity(rng);
        String eye = pick(rng, CompanionSpecies.EYES);
        String ear = pick(rng, CompanionSpecies.EARS);
        String accent = "common".equals(rarity) ? "none" : pick(rng, CompanionSpecies.ACCENTS);
        boolean shiny = rng.getAsDouble() < 0.01 || "legendary".equals(rarity);
        String color = pick(rng, CompanionSpecies.COLOR_PALETTES);
        Map<String, Integer> stats = rollStats(rng, rarity);
        // New fields appended after original roll sequence
        String breed = rollBreed(rng, rarity);
        String hat = rollHat(rng, rarity);
        String quirk = rollQuirk(rng, breed);
        return new Bones(uid, breed, rarity, eye, ear, accent, hat, quirk, shiny, color, stats);
    }
}
