package io.agentcore.tools.util;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TruncationTest {

    @Nested
    class TruncateTail {
        @Test
        void shortTextUnchanged() {
            assertEquals("hello", Truncation.truncateTail("hello", 100));
        }

        @Test
        void longTextTruncated() {
            String text = "a".repeat(200);
            String result = Truncation.truncateTail(text, 50);
            assertEquals(50, result.length()); // 47 chars + "..."
            assertTrue(result.endsWith("..."));
            assertTrue(result.startsWith("a".repeat(47)));
        }

        @Test
        void nullReturnsNull() {
            assertNull(Truncation.truncateTail(null, 100));
        }
    }

    @Nested
    class TruncateHead {
        @Test
        void shortTextUnchanged() {
            assertEquals("hello", Truncation.truncateHead("hello", 100));
        }

        @Test
        void longTextKeepsEnd() {
            String text = "a".repeat(100) + "b".repeat(100);
            String result = Truncation.truncateHead(text, 50);
            assertEquals(50, result.length()); // "..." + 47 chars
            assertTrue(result.startsWith("..."));
            assertTrue(result.endsWith("b".repeat(47)));
        }
    }

    @Nested
    class TruncateLines {
        @Test
        void underLimitUnchanged() {
            String text = "line1\nline2\nline3";
            assertEquals(text, Truncation.truncateLines(text, 10));
        }

        @Test
        void overLimitTruncated() {
            String text = "line1\nline2\nline3\nline4\nline5";
            String result = Truncation.truncateLines(text, 3);
            assertTrue(result.contains("line1"));
            assertTrue(result.contains("line2"));
            assertTrue(result.contains("line3"));
            assertFalse(result.contains("line4"));
            assertTrue(result.endsWith("..."));
        }

        @Test
        void nullReturnsNull() {
            assertNull(Truncation.truncateLines(null, 10));
        }
    }

    @Nested
    class FormatSize {
        @Test
        void bytes() {
            assertEquals("500 B", Truncation.formatSize(500));
        }

        @Test
        void kilobytes() {
            assertEquals("1.0 KB", Truncation.formatSize(1024));
        }

        @Test
        void megabytes() {
            assertEquals("1.0 MB", Truncation.formatSize(1024 * 1024));
        }

        @Test
        void gigabytes() {
            assertEquals("1.0 GB", Truncation.formatSize(1024L * 1024 * 1024));
        }
    }
}
