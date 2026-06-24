package io.agentcore.tools.shell;

/**
 * Text truncation utilities for tool output.
 *
 * <p>Provides head/tail/line-based truncation with configurable hints,
 * plus a human-readable byte-size formatter.
 */
public final class Truncation {

    private Truncation() {}

    /**
     * Keep the first {@code maxChars} characters (head), append hint.
     */
    public static String truncateTail(String text, int maxChars) {
        return truncateTail(text, maxChars, "...");
    }

    public static String truncateTail(String text, int maxChars, String hint) {
        if (text == null || text.length() <= maxChars) return text;
        int keep = Math.max(0, maxChars - hint.length());
        return text.substring(0, keep) + hint;
    }

    /**
     * Keep the last {@code maxChars} characters (tail), prepend hint.
     */
    public static String truncateHead(String text, int maxChars) {
        return truncateHead(text, maxChars, "...");
    }

    public static String truncateHead(String text, int maxChars, String hint) {
        if (text == null || text.length() <= maxChars) return text;
        int keep = Math.max(0, maxChars - hint.length());
        return hint + text.substring(text.length() - keep);
    }

    /**
     * Keep first {@code maxLines} lines.
     */
    public static String truncateLines(String text, int maxLines) {
        return truncateLines(text, maxLines, "...");
    }

    public static String truncateLines(String text, int maxLines, String hint) {
        if (text == null) return null;
        String[] lines = text.split("\n", -1);
        if (lines.length <= maxLines) return text;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxLines; i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines[i]);
        }
        sb.append('\n').append(hint);
        return sb.toString();
    }

    /**
     * Human-readable byte size (e.g. "1.2 MB").
     */
    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.1f MB", mb);
        double gb = mb / 1024.0;
        return String.format("%.1f GB", gb);
    }
}
