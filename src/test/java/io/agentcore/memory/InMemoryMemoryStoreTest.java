package io.agentcore.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryMemoryStoreTest {

    private InMemoryMemoryStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryMemoryStore();
    }

    @Nested
    class Remember {
        @Test
        void storeAndRecallSingleRecord() {
            store.remember("s1", "Java is great", null);
            List<MemoryRecord> results = store.recall("s1", "Java", 10);

            assertEquals(1, results.size());
            assertEquals("Java is great", results.get(0).text());
            assertEquals("s1", results.get(0).sessionId());
        }

        @Test
        void storeMultipleRecordsForSameSession() {
            store.remember("s1", "Java is great", null);
            store.remember("s1", "Python is fun", null);
            store.remember("s1", "Java streams are powerful", null);

            assertEquals(3, store.size());
        }

        @Test
        void storeWithMetadata() {
            store.remember("s1", "test", Map.of("source", "chat"));
            List<MemoryRecord> results = store.recall("s1", "test", 10);

            assertEquals(1, results.size());
            assertEquals("chat", results.get(0).metadata().get("source"));
        }
    }

    @Nested
    class Recall {
        @Test
        void recallByTokenOverlap() {
            store.remember("s1", "Java programming language", null);
            store.remember("s1", "Python data science", null);
            store.remember("s1", "Java virtual machine", null);

            List<MemoryRecord> results = store.recall("s1", "Java", 10);

            assertEquals(2, results.size());
            // Both Java records should be returned
            assertTrue(results.stream().allMatch(r -> r.text().contains("Java")));
        }

        @Test
        void recallRespectsLimit() {
            for (int i = 0; i < 10; i++) {
                store.remember("s1", "token " + i, null);
            }

            List<MemoryRecord> results = store.recall("s1", "token", 3);
            assertEquals(3, results.size());
        }

        @Test
        void recallEmptySession() {
            List<MemoryRecord> results = store.recall("nonexistent", "query", 10);
            assertTrue(results.isEmpty());
        }

        @Test
        void recallNoMatch() {
            store.remember("s1", "hello world", null);
            List<MemoryRecord> results = store.recall("s1", "zzzzz", 10);
            assertTrue(results.isEmpty());
        }

        @Test
        void recallSessionIsolation() {
            store.remember("s1", "session one data", null);
            store.remember("s2", "session two data", null);

            List<MemoryRecord> r1 = store.recall("s1", "session data", 10);
            List<MemoryRecord> r2 = store.recall("s2", "session data", 10);

            assertEquals(1, r1.size());
            assertEquals("session one data", r1.get(0).text());
            assertEquals(1, r2.size());
            assertEquals("session two data", r2.get(0).text());
        }

        @Test
        void recallRanksByRelevance() {
            store.remember("s1", "cat", null);
            store.remember("s1", "dog cat bird", null);
            store.remember("s1", "fish", null);

            List<MemoryRecord> results = store.recall("s1", "cat dog bird", 10);

            // "dog cat bird" has 3 overlapping tokens, should rank first
            assertFalse(results.isEmpty());
            assertEquals("dog cat bird", results.get(0).text());
        }
    }

    @Nested
    class Forget {
        @Test
        void forgetRemovesAllSessionRecords() {
            store.remember("s1", "record 1", null);
            store.remember("s1", "record 2", null);
            store.remember("s2", "other session", null);

            store.forget("s1");

            List<MemoryRecord> r1 = store.recall("s1", "record", 10);
            List<MemoryRecord> r2 = store.recall("s2", "other", 10);

            assertTrue(r1.isEmpty());
            assertEquals(1, r2.size());
        }

        @Test
        void forgetNonexistentSessionIsNoOp() {
            assertDoesNotThrow(() -> store.forget("nonexistent"));
        }
    }
}
