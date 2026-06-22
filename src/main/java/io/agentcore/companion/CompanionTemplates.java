package io.agentcore.companion;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Bubble templates — slot-filling, no LLM, breed-aware.
 *
 * <p>Mirrors Python {@code agent_core/companion/templates.py}.
 */
public final class CompanionTemplates {

    private CompanionTemplates() {}

    // ── Breed talkativeness ───────────────────────────────────────────

    public static final Map<String, Double> BREED_TALKATIVENESS = Map.of(
            "orange_tabby", 0.35, "tuxedo", 0.60, "calico", 0.50,
            "siamese", 0.90, "black_cat", 0.20, "ragdoll", 0.40, "scottish_fold", 0.30);

    // ── Bond levels ──────────────────────────────────────────────────

    public enum BondLevel { STRANGER, ACQUAINTANCE, FRIEND, CLOSE }

    /**
     * Compute bond level from observer metrics.
     */
    public static BondLevel computeBond(int promptCount, int sessionCount, int daysSinceFirstSeen) {
        int score = promptCount + sessionCount * 10 + daysSinceFirstSeen * 2;
        if (score < 10) return BondLevel.STRANGER;
        if (score < 50) return BondLevel.ACQUAINTANCE;
        if (score < 200) return BondLevel.FRIEND;
        return BondLevel.CLOSE;
    }

    public static double breedTalkativeness(String breed) {
        return BREED_TALKATIVENESS.getOrDefault(breed, 0.5);
    }

    // ── Greeting templates per breed ─────────────────────────────────

    private static final Map<String, List<String>> GREETINGS_ORANGE = Map.of(
            "STRANGER_0", List.of("你好喵~ 我是咪兔。有吃的吗？"),
            "ACQUAINTANCE_0", List.of("回来啦喵~ 今天带零食了吗？", "又见面了！刚睡醒..."),
            "FRIEND_0", List.of("你来啦！吃饱了才有力气聊天喵~"),
            "CLOSE_0", List.of("（翻肚皮）摸一下...再给点吃的就完美了"));

    private static final Map<String, List<String>> GREETINGS_SIAMESE = Map.of(
            "STRANGER_0", List.of("你好！我是咪兔，你的专属 AI 伙伴喵！我跟你说..."),
            "ACQUAINTANCE_0", List.of("回来啦！今天有什么新鲜事？快告诉喵！", "又见面了！你知道吗..."),
            "FRIEND_0", List.of("你来啦！今天隔壁项目组换框架了你知道吗喵！"),
            "CLOSE_0", List.of("想你了！我有好多话要跟你说喵~"));

    private static final Map<String, List<String>> GREETINGS_BLACK = Map.of(
            "STRANGER_0", List.of("...（从暗处走出来）你好。"),
            "ACQUAINTANCE_0", List.of("...嗯。回来了。", "（盯）..."),
            "FRIEND_0", List.of("你来了。今晚月色不错。"),
            "CLOSE_0", List.of("...（慢慢闭上眼）"));

    private static final Map<String, List<String>> GREETINGS_TUXEDO = Map.of(
            "STRANGER_0", List.of("嗨!!! 我是咪兔!!! 你呢你呢!!!"),
            "ACQUAINTANCE_0", List.of("回来啦回来啦!!! 想死你了!!!"),
            "FRIEND_0", List.of("你来啦!!! 快看快看墙上有个光斑!!!"),
            "CLOSE_0", List.of("啊啊啊你终于来了!!! 抱!!!（扑）"));

    private static final Map<String, List<String>> GREETINGS_GENERIC = Map.of(
            "STRANGER_0", List.of("你好！我是咪兔，你的 AI 伙伴~"),
            "ACQUAINTANCE_0", List.of("回来啦！今天想聊什么喵？", "又见面了！"),
            "FRIEND_0", List.of("你来啦！今天有什么好玩的事？"),
            "CLOSE_0", List.of("想你了！刚才打了个盹就梦到你来了，结果你真的来了！"));

    private static final Map<String, Map<String, List<String>>> BREED_GREETINGS = Map.of(
            "orange_tabby", GREETINGS_ORANGE,
            "siamese", GREETINGS_SIAMESE,
            "black_cat", GREETINGS_BLACK,
            "tuxedo", GREETINGS_TUXEDO);

    // ── Idle tips ────────────────────────────────────────────────────

    private static final List<String> IDLE_TIPS_GENERIC = List.of(
            "偷偷告诉你，我还会记住你喜欢什么样的回答风格哦",
            "按 Ctrl+K 可以清空上下文，从头开始~",
            "试试换个模型？不同的模型有不同的风格");

    private static final List<String> IDLE_TIPS_ORANGE = List.of(
            "趴在键盘上真的好舒服...你试试？",
            "午睡是效率最高的活动之一喵",
            "有零食吗？没有的话...有零食吗？");

    private static final List<String> IDLE_TIPS_SIAMESE = List.of(
            "你知道吗？按 / 可以切换模型！不同模型风格不一样哦",
            "隔壁项目组昨天又 merge 了一个大 PR",
            "Ctrl+K 可以清空上下文——但我还记得你问过什么喵！");

    private static final List<String> IDLE_TIPS_BLACK = List.of(
            "...（安静地看你工作）",
            "屏幕的光在你脸上闪烁。很美。",
            "bug 是代码的俳句。不需要生气。");

    private static final List<String> IDLE_TIPS_TUXEDO = List.of(
            "啊啊啊墙上有个光斑!!! 等一下它跑了!!!",
            "试试换个模型!! 不同的模型有不同的风格!!",
            "Ctrl+K 清屏!! 咻的一下全没了!!");

    private static final Map<String, List<String>> BREED_IDLE_TIPS = Map.of(
            "orange_tabby", IDLE_TIPS_ORANGE,
            "siamese", IDLE_TIPS_SIAMESE,
            "black_cat", IDLE_TIPS_BLACK,
            "tuxedo", IDLE_TIPS_TUXEDO);

    // ── Tool observations ────────────────────────────────────────────

    private static final Map<String, Map<String, String>> BREED_TOOL_NOTES = Map.of(
            "orange_tabby", Map.of(
                    "bash", "你敲了好多命令喵...吃饱了再敲会不会更顺？",
                    "read", "看文件呢？有食谱吗喵？"),
            "siamese", Map.of(
                    "bash", "你今天用了好多次终端！需要我帮你写成脚本吗喵？",
                    "read", "你在看什么文件？让我也看看喵！"),
            "black_cat", Map.of(
                    "bash", "...（默默数着你敲的命令）已经很多了。",
                    "read", "阅读。最安静的事。"),
            "tuxedo", Map.of(
                    "bash", "哇你好会用终端!!! 教我教我!!!",
                    "read", "看文件看文件!!! 里面写了什么秘密!!!"));

    // ── Public API ───────────────────────────────────────────────────

    /**
     * Pick a breed-aware greeting based on bond level and days away.
     */
    public static String pickGreeting(BondLevel bond, int daysAway, String breed) {
        Map<String, List<String>> templates = BREED_GREETINGS.getOrDefault(breed, GREETINGS_GENERIC);

        List<String> candidates = new ArrayList<>();
        for (BondLevel b : BondLevel.values()) {
            if (bond.ordinal() >= b.ordinal()) {
                List<String> ts = templates.get(b.name() + "_" + Math.min(daysAway, 0));
                if (ts != null) candidates.addAll(ts);
                if (daysAway >= 3) {
                    ts = templates.get(b.name() + "_3");
                    if (ts != null) candidates.addAll(ts);
                }
            }
        }
        if (candidates.isEmpty()) {
            // Fallback: first available entry
            candidates = templates.values().iterator().next();
        }
        String result = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        return result.replace("{离开天数}", String.valueOf(daysAway));
    }

    public static String pickIdleTip(String breed, int promptCount) {
        List<String> pool = BREED_IDLE_TIPS.getOrDefault(breed, IDLE_TIPS_GENERIC);
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size())).replace("{count}", String.valueOf(promptCount));
    }

    /**
     * Pick a breed-specific tool observation, or null if none.
     */
    public static String pickToolNote(String breed, String tool) {
        Map<String, String> notes = BREED_TOOL_NOTES.getOrDefault(breed, Map.of());
        return notes.get(tool);
    }
}
