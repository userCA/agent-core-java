package io.agentcore.tools.truncate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TruncateTest {

    @Test
    void truncateTail_shortText_unchanged() {
        assertEquals("hello", Truncate.truncateTail("hello", 10));
    }

    @Test
    void truncateTail_longText_truncated() {
        String result = Truncate.truncateTail("abcdefghij", 5);
        assertEquals("abcde...", result);
    }

    @Test
    void truncateTail_customHint() {
        String result = Truncate.truncateTail("abcdefghij", 5, " [truncated]");
        assertEquals("abcde [truncated]", result);
    }

    @Test
    void truncateTail_nullInput() {
        assertNull(Truncate.truncateTail(null, 10));
    }

    @Test
    void truncateHead_shortText_unchanged() {
        assertEquals("hello", Truncate.truncateHead("hello", 10));
    }

    @Test
    void truncateHead_longText_truncated() {
        String result = Truncate.truncateHead("abcdefghij", 5);
        assertEquals("...fghij", result);
    }

    @Test
    void truncateLine_shortText_unchanged() {
        assertEquals("a\nb\nc", Truncate.truncateLine("a\nb\nc", 5));
    }

    @Test
    void truncateLine_longText_truncated() {
        String text = "line1\nline2\nline3\nline4\nline5";
        String result = Truncate.truncateLine(text, 3);
        assertEquals("line1\nline2\nline3\n...", result);
    }

    @Test
    void truncateLine_nullInput() {
        assertNull(Truncate.truncateLine(null, 3));
    }

    @Test
    void formatSize_bytes() {
        assertEquals("512 B", Truncate.formatSize(512));
    }

    @Test
    void formatSize_kilobytes() {
        assertEquals("1.0 KB", Truncate.formatSize(1024));
        assertEquals("2.5 KB", Truncate.formatSize(2560));
    }

    @Test
    void formatSize_megabytes() {
        assertEquals("1.0 MB", Truncate.formatSize(1024 * 1024));
    }

    @Test
    void formatSize_gigabytes() {
        assertEquals("1.0 GB", Truncate.formatSize(1024L * 1024 * 1024));
    }
}
