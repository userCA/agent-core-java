package io.agentcore.companion;

/**
 * A bubble notification shown to the user.
 *
 * <p>Mirrors Python {@code agent_core/companion/types.py CompanionBubble}.
 *
 * @param text     bubble text to display
 * @param ttlMs    time-to-live in milliseconds (default 8000)
 * @param priority "normal" | "high" | "low"
 */
public record CompanionBubble(String text, int ttlMs, String priority) {

    public CompanionBubble {
        if (text == null) text = "";
        if (ttlMs <= 0) ttlMs = 8000;
        if (priority == null || priority.isBlank()) priority = "normal";
    }

    public CompanionBubble(String text) {
        this(text, 8000, "normal");
    }

    public CompanionBubble(String text, int ttlMs) {
        this(text, ttlMs, "normal");
    }
}
