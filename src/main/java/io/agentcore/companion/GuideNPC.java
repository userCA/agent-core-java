package io.agentcore.companion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * GuideNPC — decides when and what companion bubbles to show.
 *
 * <p>Mirrors Python {@code agent_core/companion/guide.py}.
 * Rule engine for companion bubbles — breed-aware, no LLM involved.
 */
public final class GuideNPC {

    private static final Logger log = LoggerFactory.getLogger(GuideNPC.class);

    private final CompanionMemory memory;
    private final SilentObserver observer;
    private final String breed;
    private boolean greeted = false;

    public GuideNPC(CompanionMemory memory, SilentObserver observer, String breed) {
        this.memory = memory;
        this.observer = observer;
        this.breed = breed != null ? breed : "orange_tabby";
    }

    public GuideNPC(CompanionMemory memory, SilentObserver observer) {
        this(memory, observer, "orange_tabby");
    }

    /**
     * Decide which bubble to show, or null if none.
     *
     * <p>Priority order:
     * <ol>
     *   <li>Onboarding (low prompt count)</li>
     *   <li>Greeting on first turn of session</li>
     *   <li>Repeated tool use observation</li>
     *   <li>Long session rest reminder</li>
     *   <li>Random idle tip (probability scaled by breed talkativeness)</li>
     * </ol>
     */
    public CompanionBubble decideBubble(String uid) {
        CompanionTemplates.BondLevel bond = CompanionTemplates.computeBond(
                observer.getPromptCount(),
                observer.getSessionCount(),
                observer.daysSinceFirstSeen());
        double talk = CompanionTemplates.breedTalkativeness(breed);

        // -- onboarding (low prompt count) --
        if (observer.getPromptCount() <= 2) {
            log.debug("Bubble decision: onboarding (promptCount={})", observer.getPromptCount());
            return new CompanionBubble(
                    "提示：按 / 可以切换模型，试试问我「帮我写一个脚本」吧！",
                    15_000,
                    "onboarding");
        }

        // -- greeting on first turn of session --
        if (!greeted) {
            greeted = true;
            List<CompanionMemory.Observation> observations = memory.recall(uid, 5);
            int daysAway = 0;
            if (!observations.isEmpty()) {
                long lastTs = observations.getLast().timestamp();
                daysAway = (int) ((System.currentTimeMillis() / 1000 - lastTs) / 86400);
            }
            String text = CompanionTemplates.pickGreeting(bond, daysAway, breed);
            log.debug("Bubble decision: greeting (daysAway={}, bond={})", daysAway, bond);
            return new CompanionBubble(text, 12_000, "greeting");
        }

        // -- repeated tool use (breed-specific observation) --
        if (observer.repeatedTool("bash", 3)) {
            String note = CompanionTemplates.pickToolNote(breed, "bash");
            if (note != null) {
                log.debug("Bubble decision: repeated tool suggestion (breed={})", breed);
                return new CompanionBubble(note, 10_000, "suggestion");
            }
        }

        // -- long session reminder --
        if (observer.sessionDuration() > 3600) {
            log.debug("Bubble decision: rest reminder (duration={}s)", Math.round(observer.sessionDuration()));
            return new CompanionBubble(
                    "你已经连续聊了 1 小时，要不要休息一下？咪兔也有点困了...",
                    10_000,
                    "care");
        }

        // -- random tip (probability scaled by talkativeness) --
        double baseChance = 0.03 * (0.5 + talk);
        if (ThreadLocalRandom.current().nextDouble() < baseChance) {
            log.debug("Bubble decision: random idle tip (chance={})", baseChance);
            return new CompanionBubble(
                    CompanionTemplates.pickIdleTip(breed, observer.getPromptCount()),
                    8_000,
                    "low");
        }

        log.debug("No bubble this turn");
        return null;
    }

    /** Reset greeting flag (e.g. on new session). */
    public void resetGreeting() {
        this.greeted = false;
    }

    public boolean isGreeted() { return greeted; }
    public String breed() { return breed; }
}
