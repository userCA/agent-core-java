package io.agentcore.session;

import io.agentcore.session.SessionEntry.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the session package: SessionStore, MemorySessionStore,
 * JsonlSessionStore, records, and snapshot.
 */
class SessionTest {

    // ── Record tests ──────────────────────────────────────────────────

    @Nested
    class RecordTests {

        @Test
        void sessionHeader_3argConstructor_defaultsVersion() {
            SessionHeader h = new SessionHeader("s1", "2024-01-01T00:00:00Z", "/tmp");
            assertEquals("s1", h.id());
            assertEquals("/tmp", h.cwd());
            assertEquals("1", h.version());
        }

        @Test
        void sessionHeader_2argConstructor_defaultsCwdAndVersion() {
            SessionHeader h = new SessionHeader("s2", "2024-01-01T00:00:00Z");
            assertEquals("", h.cwd());
            assertEquals("1", h.version());
        }

        @Test
        void sessionMeta_nullTitle_defaultsToEmpty() {
            SessionMeta m = new SessionMeta("s1", "ts", 5, null);
            assertEquals("", m.title());
        }

        @Test
        void sessionMeta_normalTitle_preserved() {
            SessionMeta m = new SessionMeta("s1", "ts", 3, "Hello");
            assertEquals("Hello", m.title());
        }

        @Test
        void sessionSnapshot_nullEntries_defaultsToEmptyList() {
            SessionHeader h = new SessionHeader("s1", "ts");
            SessionSnapshot snap = new SessionSnapshot(h, null);
            assertNotNull(snap.entries());
            assertTrue(snap.entries().isEmpty());
        }

        @Test
        void sessionSnapshot_entriesAreImmutable() {
            SessionHeader h = new SessionHeader("s1", "ts");
            List<SessionEntry> entries = new ArrayList<>();
            entries.add(new MessageEntry("m1", Map.of("role", "user"), null));
            SessionSnapshot snap = new SessionSnapshot(h, entries);
            assertEquals(1, snap.entries().size());
            // Mutating original list should not affect snapshot
            entries.add(new MessageEntry("m2", Map.of("role", "assistant"), null));
            assertEquals(1, snap.entries().size());
        }

        @Test
        void compactionEntry_4argConstructor_defaultsDetailsAndExtension() {
            CompactionEntry ce = new CompactionEntry("c1", "summary", "first1", 100);
            assertNull(ce.details());
            assertFalse(ce.fromExtension());
        }

        @Test
        void allEntryTypes_haveCorrectIds() {
            assertEquals("m1", new MessageEntry("m1", null, null).id());
            assertEquals("c1", new CompactionEntry("c1", "", "", 0, null, false).id());
            assertEquals("mc1", new ModelChangeEntry("mc1", "anthropic", "claude-3").id());
            assertEquals("tl1", new ThinkingLevelChangeEntry("tl1", "high").id());
            assertEquals("cu1", new CustomEntry("cu1", "custom_type", null).id());
        }
    }

    // ── MemorySessionStore tests ──────────────────────────────────────

    @Nested
    class MemoryStoreTests {

        private MemorySessionStore store;

        @BeforeEach
        void setup() {
            store = new MemorySessionStore();
        }

        @Test
        void createAndLoad_emptySession() {
            SessionHeader header = new SessionHeader("s1", "ts", "/cwd");
            store.createSession("s1", header);
            SessionSnapshot snap = store.loadSession("s1");
            assertEquals("s1", snap.header().id());
            assertTrue(snap.entries().isEmpty());
        }

        @Test
        void appendEntry_addsEntries() {
            store.createSession("s1", new SessionHeader("s1", "ts"));
            store.appendEntry("s1", new MessageEntry("m1", Map.of("role", "user"), null));
            store.appendEntry("s1", new MessageEntry("m2", Map.of("role", "assistant"), null));
            SessionSnapshot snap = store.loadSession("s1");
            assertEquals(2, snap.entries().size());
        }

        @Test
        void loadSession_notFound_throws() {
            assertThrows(IllegalArgumentException.class, () -> store.loadSession("nonexistent"));
        }

        @Test
        void appendEntry_sessionNotFound_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> store.appendEntry("nonexistent", new MessageEntry("m1", null, null)));
        }

        @Test
        void listSessions_returnsSortedById() {
            store.createSession("c", new SessionHeader("c", "ts3"));
            store.createSession("a", new SessionHeader("a", "ts1"));
            store.createSession("b", new SessionHeader("b", "ts2"));
            List<SessionMeta> list = store.listSessions(null, 10);
            assertEquals(3, list.size());
            assertEquals("a", list.get(0).sessionId());
            assertEquals("b", list.get(1).sessionId());
            assertEquals("c", list.get(2).sessionId());
        }

        @Test
        void listSessions_respectsLimit() {
            store.createSession("a", new SessionHeader("a", "ts1"));
            store.createSession("b", new SessionHeader("b", "ts2"));
            store.createSession("c", new SessionHeader("c", "ts3"));
            List<SessionMeta> list = store.listSessions(null, 2);
            assertEquals(2, list.size());
        }

        @Test
        void listSessions_entryCountAccurate() {
            store.createSession("s1", new SessionHeader("s1", "ts"));
            store.appendEntry("s1", new MessageEntry("m1", null, null));
            store.appendEntry("s1", new CompactionEntry("c1", "sum", "m1", 100));
            List<SessionMeta> list = store.listSessions(null, 10);
            assertEquals(1, list.size());
            assertEquals(2, list.get(0).entryCount());
        }

        @Test
        void close_isNoop() {
            assertDoesNotThrow(() -> store.close());
        }
    }

    // ── JsonlSessionStore tests ───────────────────────────────────────

    @Nested
    class JsonlStoreTests {

        @TempDir
        Path tmpDir;

        private JsonlSessionStore store;

        @BeforeEach
        void setup() throws Exception {
            store = new JsonlSessionStore(tmpDir);
        }

        @Test
        void createAndLoad_roundtrip() {
            SessionHeader header = new SessionHeader("s1", "2024-01-01T00:00:00Z", "/cwd", "2");
            store.createSession("s1", header);

            SessionSnapshot snap = store.loadSession("s1");
            assertEquals("s1", snap.header().id());
            assertEquals("2024-01-01T00:00:00Z", snap.header().timestamp());
            assertEquals("/cwd", snap.header().cwd());
            assertEquals("2", snap.header().version());
        }

        @Test
        void appendEntry_messageEntry_roundtrip() {
            store.createSession("s1", new SessionHeader("s1", "ts"));
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("role", "user");
            msg.put("content", "Hello");
            store.appendEntry("s1", new MessageEntry("m1", msg, null));

            SessionSnapshot snap = store.loadSession("s1");
            assertEquals(1, snap.entries().size());
            assertInstanceOf(MessageEntry.class, snap.entries().get(0));
        }

        @Test
        void appendEntry_compactionEntry_roundtrip() {
            store.createSession("s1", new SessionHeader("s1", "ts"));
            store.appendEntry("s1", new CompactionEntry("c1", "summary text", "m1", 500, null, false));

            SessionSnapshot snap = store.loadSession("s1");
            assertEquals(1, snap.entries().size());
            assertInstanceOf(CompactionEntry.class, snap.entries().get(0));
            CompactionEntry ce = (CompactionEntry) snap.entries().get(0);
            assertEquals("summary text", ce.summary());
            assertEquals(500, ce.tokensBefore());
        }

        @Test
        void appendEntry_modelChangeEntry_roundtrip() {
            store.createSession("s1", new SessionHeader("s1", "ts"));
            store.appendEntry("s1", new ModelChangeEntry("mc1", "anthropic", "claude-3-opus"));

            SessionSnapshot snap = store.loadSession("s1");
            assertEquals(1, snap.entries().size());
            assertInstanceOf(ModelChangeEntry.class, snap.entries().get(0));
            ModelChangeEntry mce = (ModelChangeEntry) snap.entries().get(0);
            assertEquals("anthropic", mce.provider());
            assertEquals("claude-3-opus", mce.modelId());
        }

        @Test
        void appendEntry_thinkingLevelChangeEntry_roundtrip() {
            store.createSession("s1", new SessionHeader("s1", "ts"));
            store.appendEntry("s1", new ThinkingLevelChangeEntry("tl1", "high"));

            SessionSnapshot snap = store.loadSession("s1");
            assertEquals(1, snap.entries().size());
            assertInstanceOf(ThinkingLevelChangeEntry.class, snap.entries().get(0));
        }

        @Test
        void appendEntry_customEntry_roundtrip() {
            store.createSession("s1", new SessionHeader("s1", "ts"));
            store.appendEntry("s1", new CustomEntry("cu1", "heartbeat", Map.of("ping", 1)));

            SessionSnapshot snap = store.loadSession("s1");
            assertEquals(1, snap.entries().size());
            assertInstanceOf(CustomEntry.class, snap.entries().get(0));
            CustomEntry ce = (CustomEntry) snap.entries().get(0);
            assertEquals("heartbeat", ce.customType());
        }

        @Test
        void loadSession_notFound_throws() {
            assertThrows(IllegalArgumentException.class, () -> store.loadSession("nonexistent"));
        }

        @Test
        void listSessions_findsJsonlFiles() {
            store.createSession("s1", new SessionHeader("s1", "2024-01-01T10:00:00Z"));
            store.createSession("s2", new SessionHeader("s2", "2024-01-01T11:00:00Z"));

            List<SessionMeta> list = store.listSessions(null, 10);
            assertEquals(2, list.size());
        }

        @Test
        void listSessions_respectsLimit() {
            store.createSession("s1", new SessionHeader("s1", "ts1"));
            store.createSession("s2", new SessionHeader("s2", "ts2"));
            store.createSession("s3", new SessionHeader("s3", "ts3"));

            List<SessionMeta> list = store.listSessions(null, 2);
            assertEquals(2, list.size());
        }

        @Test
        void multipleEntries_preserveOrder() {
            store.createSession("s1", new SessionHeader("s1", "ts"));
            for (int i = 0; i < 5; i++) {
                store.appendEntry("s1", new MessageEntry("m" + i,
                        Map.of("role", "user", "content", "msg " + i), null));
            }
            SessionSnapshot snap = store.loadSession("s1");
            assertEquals(5, snap.entries().size());
            for (int i = 0; i < 5; i++) {
                assertEquals("m" + i, snap.entries().get(i).id());
            }
        }

        @Test
        void stringConstructor_createsDirectory() throws Exception {
            Path sub = tmpDir.resolve("sub/dir");
            JsonlSessionStore s = new JsonlSessionStore(sub.toString());
            s.createSession("test", new SessionHeader("test", "ts"));
            assertNotNull(s.loadSession("test"));
        }
    }
}
