package io.agentcore.companion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Companion naming — LLM-generated name + personality at hatch time.
 *
 * <p>Mirrors Python {@code agent_core/companion/naming.py}.
 * The LLM path is handled via a pluggable {@link NamingProvider} interface;
 * without one, falls back to breed-specific name pools.
 */
public final class CompanionNaming {

    private static final Logger log = LoggerFactory.getLogger(CompanionNaming.class);

    private CompanionNaming() {}

    // ── Data ──────────────────────────────────────────────────────────

    /** Generated soul: name + personality + hatch timestamp. */
    public record CompanionSoul(String name, String personality, long hatchedAt) {}

    /** Pluggable LLM-based naming provider. */
    @FunctionalInterface
    public interface NamingProvider {
        CompanionSoul hatchName(CompanionBones.Bones bones);
    }

    private static volatile NamingProvider provider = null;

    public static void configureNaming(NamingProvider p) {
        provider = p;
    }

    // ── Breed name pools ─────────────────────────────────────────────

    public static final Map<String, List<String>> NAME_POOL = Map.of(
            "orange_tabby", List.of("橘子", "大橘", "橘胖", "橘糖", "麦芽", "吐司", "蛋黄", "南瓜", "芝士", "布丁"),
            "tuxedo", List.of("芝麻", "墨水", "奥利奥", "斑斑", "企鹅", "围棋", "熊猫", "墨点", "珍珠"),
            "calico", List.of("琥珀", "麻薯", "咖喱", "麻衣", "花卷", "豆花", "拿铁", "太妃", "栗子", "玛瑙"),
            "siamese", List.of("芝麻糊", "小米", "可可", "摩卡", "咖啡", "奶茶", "乌龙", "可可豆", "松露", "黑豆"),
            "black_cat", List.of("露娜", "影子", "墨墨", "玄月", "芝麻球", "黑糖", "墨鱼", "曜", "暗夜", "星尘"),
            "ragdoll", List.of("棉花", "糯米", "汤圆", "雪球", "云朵", "奶油", "年糕", "冰激凌", "奶糖", "白巧"),
            "scottish_fold", List.of("团子", "馒头", "豆包", "丸子", "麻圆", "汤包", "软糖", "果冻", "泡芙", "糯米糍"));

    public static final Map<String, String> QUIRK_CN = Map.ofEntries(
            Map.entry("night_owl", "夜猫子"), Map.entry("picky_eater", "挑食怪"),
            Map.entry("chatterbox", "话痨"), Map.entry("shy", "社恐"),
            Map.entry("collector", "收集癖"), Map.entry("hyperactive", "多动症"),
            Map.entry("sleepyhead", "睡神"), Map.entry("glass_heart", "玻璃心"),
            Map.entry("foodie", "贪吃"), Map.entry("clean_freak", "洁癖"),
            Map.entry("tsundere_extreme", "究极傲娇"),
            Map.entry("philosopher", "哲学家"), Map.entry("comedian", "搞笑猫"));

    public static final Map<String, String> EYE_CN = Map.of(
            "·", "眯眯眼", "✦", "星光眼", "◉", "大圆眼",
            "o", "圆眼", "♥", "爱心眼", "☆", "星眼");

    public static final Map<String, String> RARITY_CN = Map.of(
            "common", "普通", "uncommon", "稀有", "rare", "珍稀",
            "epic", "史诗", "legendary", "传说");

    // ── Naming prompt builder ────────────────────────────────────────

    public static String buildNamingPrompt(CompanionBones.Bones bones) {
        String breedCn = CompanionSpecies.BREED_NAMES.getOrDefault(bones.breed(), bones.breed());
        String rarityCn = RARITY_CN.getOrDefault(bones.rarity(), bones.rarity());
        String eyeCn = EYE_CN.getOrDefault(bones.eye(), bones.eye());
        String quirkCn = QUIRK_CN.getOrDefault(bones.quirk(), bones.quirk());
        String shinyText = bones.shiny() ? "是，全身闪光粒子" : "否";
        Map<String, Integer> stats = bones.stats();

        return """
                你是一只住在终端里的 %s 猫精灵的"灵魂生成器"。

                这只猫的基因特征是:
                - 品种: %s
                - 稀有度: %s (%s/5星)
                - 眼睛: %s
                - 配饰: %s
                - 帽子: %s
                - 性格数值: 好奇%d 社交%d 亲昵%d 贪玩%d 幸运%d
                - 怪癖: %s
                - 是否闪亮: %s

                请为这只猫:
                1. 起一个中文名 (2-4字, 食物/自然/可爱系, 不能和人名重名)
                2. 写一句 25 字以内的人格描述 (温暖、有趣、体现性格和怪癖)

                输出 JSON:
                {"name": "...", "personality": "..."}

                只输出 JSON, 不要解释。""".formatted(
                breedCn, breedCn, rarityCn, bones.rarity(), eyeCn,
                bones.accent(), bones.hat(),
                stats.getOrDefault("CURIOSITY", 50),
                stats.getOrDefault("SOCIAL", 50),
                stats.getOrDefault("AFFECTION", 50),
                stats.getOrDefault("PLAYFUL", 50),
                stats.getOrDefault("LUCK", 50),
                quirkCn, shinyText);
    }

    // ── Public API ───────────────────────────────────────────────────

    /**
     * Generate name + personality. Uses LLM provider if configured,
     * falls back to breed-specific name pool otherwise.
     */
    public static CompanionSoul hatchName(CompanionBones.Bones bones) {
        NamingProvider p = provider;
        if (p != null) {
            try {
                return p.hatchName(bones);
            } catch (Exception e) {
                log.warn("LLM naming provider failed, falling back to pool", e);
            }
        }
        return fallbackSoul(bones);
    }

    public static CompanionSoul fallbackSoul(CompanionBones.Bones bones) {
        String breedCn = CompanionSpecies.BREED_NAMES.getOrDefault(bones.breed(), "猫");
        return new CompanionSoul(
                randomName(bones.breed()),
                "一只性格独特的" + breedCn,
                System.currentTimeMillis() / 1000);
    }

    public static String randomName(String breed) {
        List<String> pool = NAME_POOL.getOrDefault(breed, List.of("咪咪", "小喵"));
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }
}
