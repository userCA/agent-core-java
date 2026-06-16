package io.agentcore.tools.truncate;

/**
 * Text truncation utilities for tool output.
 */
public final class Truncate {
    private Truncate() {}

    /**
     * Keep the first maxChars characters (head), append hint.
     */
    public static String truncateTail(String text, int maxChars) {
        return truncateTail(text, maxChars, "...");
    }

    public static String truncateTail(String text, int maxChars, String hint) {
        if (text == null || text.length() <= maxChars) return text;
        return text.substring(0, maxChars) + hint;
    }

    /**
     * Keep the last maxChars characters (tail), prepend hint.
     */
    public static String truncateHead(String text, int maxChars) {
        return truncateHead(text, maxChars, "...");
    }

    public static String truncateHead(String text, int maxChars, String hint) {
        if (text == null || text.length() <= maxChars) return text;
        return hint + text.substring(text.length() - maxChars);
    }

    /**
     * Keep first maxLines lines.
     */
    public static String truncateLine(String text, int maxLines) {
        return truncateLine(text, maxLines, "...");
    }

    public static String truncateLine(String text, int maxLines, String hint) {
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
     * Human-readable file size.
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
