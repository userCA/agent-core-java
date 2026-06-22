package io.agentcore.retrieval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryRetrieverTest {

    private InMemoryRetriever retriever;

    @BeforeEach
    void setUp() {
        retriever = new InMemoryRetriever();
    }

    @Nested
    class BasicRetrieval {
        @Test
        void emptyRetrieverReturnsNoResults() throws Exception {
            List<RetrievedChunk> results = retriever.retrieve(new Query("anything")).get();
            assertTrue(results.isEmpty());
        }

        @Test
        void retrievesMatchingDocument() throws Exception {
            retriever.add("Agent architecture uses ReAct loop pattern");

            List<RetrievedChunk> results = retriever.retrieve(new Query("agent architecture")).get();
            assertFalse(results.isEmpty());
            assertEquals(1, results.size());
            assertTrue(results.get(0).text().contains("ReAct"));
            assertTrue(results.get(0).score() > 0);
        }

        @Test
        void returnsEmptyWhenNoTokenOverlap() throws Exception {
            retriever.add("Agent architecture uses ReAct loop");

            List<RetrievedChunk> results = retriever.retrieve(new Query("xyz abc")).get();
            assertTrue(results.isEmpty());
        }

        @Test
        void ranksByTokenOverlapFraction() throws Exception {
            retriever.add("Python is a scripting tool");             // 0 overlap with query
            retriever.add("Java language supports lambda expressions"); // 2 overlap: "java", "language"

            List<RetrievedChunk> results = retriever.retrieve(
                    new Query("java language features", 5)).get();

            assertEquals(1, results.size()); // only doc2 matches
            assertTrue(results.get(0).text().contains("lambda"));
        }

        @Test
        void respectsTopKLimit() throws Exception {
            for (int i = 0; i < 10; i++) {
                retriever.add("document number " + i + " about testing");
            }

            List<RetrievedChunk> results = retriever.retrieve(
                    new Query("document testing", 3)).get();
            assertEquals(3, results.size());
        }

        @Test
        void blankQueryReturnsEmpty() throws Exception {
            retriever.add("some document");
            List<RetrievedChunk> results = retriever.retrieve(new Query("")).get();
            assertTrue(results.isEmpty());
        }
    }

    @Nested
    class SourceAndMetadata {
        @Test
        void preservesSourceTag() throws Exception {
            retriever.add("Server uses port 8080", "config.yaml");

            List<RetrievedChunk> results = retriever.retrieve(new Query("server port")).get();
            assertEquals(1, results.size());
            assertEquals("config.yaml", results.get(0).source());
        }

        @Test
        void preservesMetadata() throws Exception {
            retriever.add("API endpoint for users", "api.md",
                    Map.of("category", "api", "version", 2));

            List<RetrievedChunk> results = retriever.retrieve(new Query("api endpoint")).get();
            assertEquals(1, results.size());
            assertEquals("api", results.get(0).metadata().get("category"));
            assertEquals(2, results.get(0).metadata().get("version"));
        }
    }

    @Nested
    class Filters {
        @Test
        void filtersExcludeNonMatchingDocs() throws Exception {
            retriever.add("Java guide", "java.md", Map.of("lang", "java"));
            retriever.add("Python guide", "py.md", Map.of("lang", "python"));

            List<RetrievedChunk> results = retriever.retrieve(
                    new Query("guide", 5, Map.of("lang", "java"))).get();

            assertEquals(1, results.size());
            assertTrue(results.get(0).text().contains("Java"));
        }

        @Test
        void emptyFiltersMatchAll() throws Exception {
            retriever.add("Java guide", "java.md", Map.of("lang", "java"));
            retriever.add("Python guide", "py.md", Map.of("lang", "python"));

            List<RetrievedChunk> results = retriever.retrieve(
                    new Query("guide", 5, Map.of())).get();
            assertEquals(2, results.size());
        }
    }

    @Nested
    class ThreadSafety {
        @Test
        void concurrentAddAndRetrieve() throws Exception {
            // Add documents from multiple threads
            for (int i = 0; i < 100; i++) {
                final int idx = i;
                new Thread(() -> retriever.add("document about topic " + idx)).start();
            }
            // Wait a bit for threads to finish
            Thread.sleep(200);

            assertEquals(100, retriever.size());
            List<RetrievedChunk> results = retriever.retrieve(
                    new Query("document topic", 10)).get();
            assertFalse(results.isEmpty());
            assertTrue(results.size() <= 10);
        }
    }
}
