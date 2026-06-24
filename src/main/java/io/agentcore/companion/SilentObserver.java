package io.agentcore.companion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * SilentObserver — writes structured observations to CompanionMemory.
 *
 * <p>Mirrors Python {@code agent_core/companion/observer.py}.
 * Does not interrupt agent flow — all hooks return immediately.
 */
public final class SilentObserver {

    private static final Logger log = LoggerFactory.getLogger(SilentObserver.class);
    private static final int TOOL_HISTORY_LEN = 10;

    private final CompanionMemory memory;
    private Long firstSeenAt = null;
    private int sessionCount = 0;
    private int promptCount = 0;
    private int toolUseCount = 0;
    private final Deque<String> recentTools = new ArrayDeque<>(TOOL_HISTORY_LEN);
    private long lastActiveAt = 0;
    private String lastTopic = null;

    public SilentObserver(CompanionMemory memory) {
        this.memory = memory;
    }

    // ── Queries used by GuideNPC ──────────────────────────────────────

    public int daysSinceFirstSeen() {
        if (firstSeenAt == null) return 0;
        return (int) ((System.currentTimeMillis() / 1000 - firstSeenAt) / 86400);
    }

    public int getPromptCount()   { return promptCount; }
    public int getSessionCount()  { return sessionCount; }
    public String getLastTopic()  { return lastTopic; }

    public double sessionDuration() {
        long now = System.currentTimeMillis() / 1000;
        return (double) (now - (lastActiveAt > 0 ? lastActiveAt : now));
    }

    public boolean repeatedTool(String tool, int count) {
        if (recentTools.size() < count) return false;
        List<String> recent = new ArrayList<>(recentTools);
        int from = recent.size() - count;
        for (int i = from; i < recent.size(); i++) {
            if (!recent.get(i).equals(tool)) return false;
        }
        return true;
    }

    // ── Lifecycle hooks ──────────────────────────────────────────────

    public void onSessionStart(String uid) {
        if (firstSeenAt == null) {
            firstSeenAt = System.currentTimeMillis() / 1000;
        }
        sessionCount++;
        lastActiveAt = System.currentTimeMillis() / 1000;
        log.debug("Session start for uid={}, sessionCount={}", uid, sessionCount);
    }

    public void onPrompt(String uid, String prompt) {
        promptCount++;
        String topic = extractTopicHint(prompt);
        if (topic != null && !topic.equals(lastTopic)) {
            lastTopic = topic;
            log.debug("New topic detected for uid={}: {}", uid, topic);
            memory.observe(uid, new CompanionMemory.Observation(
                    "user_prompt",
                    System.currentTimeMillis() / 1000,
                    topic,
                    Map.of("len", prompt.length())));
        }
    }

    public void onToolStart(String uid, String tool) {
        toolUseCount++;
        recentTools.addLast(tool);
        while (recentTools.size() > TOOL_HISTORY_LEN) {
            recentTools.removeFirst();
        }
        log.debug("Tool use #{} for uid={}: {}", toolUseCount, uid, tool);
    }

    public void onTurnEnd(String uid) {
        lastActiveAt = System.currentTimeMillis() / 1000;
        log.debug("Turn end for uid={}", uid);
    }

    public void onIdleReturn(String uid, double awaySeconds) {
        memory.observe(uid, new CompanionMemory.Observation(
                "idle_return",
                System.currentTimeMillis() / 1000,
                "离开 " + String.format("%.0f", awaySeconds) + "s 后回来",
                Map.of("away_s", awaySeconds)));
    }

    // ── Topic extraction ─────────────────────────────────────────────

    /**
     * Extract a simple topic hint from a prompt (first sentence or keyword).
     */
    static String extractTopicHint(String prompt) {
        if (prompt == null || prompt.isBlank()) return null;
        // Take first sentence or first 50 chars
        String trimmed = prompt.strip();
        int end = -1;
        for (char c : new char[]{'.', '。', '?', '？', '!', '！', '\n'}) {
            int idx = trimmed.indexOf(c);
            if (idx > 0 && (end < 0 || idx < end)) end = idx;
        }
        if (end > 0) return trimmed.substring(0, Math.min(end, 80));
        return trimmed.length() > 50 ? trimmed.substring(0, 50) : trimmed;
    }
}
