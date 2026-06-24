package io.agentcore.retrieval;

import io.agentcore.tools.ToolContext;
import io.agentcore.model.ToolResult;

import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RetrieverToolTest {

    @Nested
    class QueryTests {
        @Test
        void query_fullConstructor() {
            Query q = new Query("search text", 10, Map.of("lang", "java"));
            assertEquals("search text", q.text());
            assertEquals(10, q.topK());
            assertEquals("java", q.filters().get("lang"));
        }

        @Test
        void query_twoArgConstructor() {
            Query q = new Query("test", 3);
            assertEquals("test", q.text());
            assertEquals(3, q.topK());
            assertTrue(q.filters().isEmpty());
        }

        @Test
        void query_singleArgConstructor() {
            Query q = new Query("hello");
            assertEquals("hello", q.text());
            assertEquals(5, q.topK());
            assertTrue(q.filters().isEmpty());
        }
    }

    @Nested
    class RetrievedChunkTests {
        @Test
        void chunk_fullConstructor() {
            RetrievedChunk c = new RetrievedChunk("text", 0.95, "src.md", Map.of("k", "v"));
            assertEquals("text", c.text());
            assertEquals(0.95, c.score());
            assertEquals("src.md", c.source());
            assertEquals("v", c.metadata().get("k"));
        }

        @Test
        void chunk_twoArgConstructor() {
            RetrievedChunk c = new RetrievedChunk("text", 0.8);
            assertNull(c.source());
            assertTrue(c.metadata().isEmpty());
        }

        @Test
        void chunk_threeArgConstructor() {
            RetrievedChunk c = new RetrievedChunk("text", 0.7, "doc.txt");
            assertEquals("doc.txt", c.source());
            assertTrue(c.metadata().isEmpty());
        }
    }

    @Nested
    class RetrieverToolTests {
        private InMemoryRetriever retriever;
        private RetrieverTool tool;

        @BeforeEach
        void setup() {
            retriever = new InMemoryRetriever();
            retriever.add("Agent architecture uses ReAct loop", "arch.md");
            retriever.add("Tool calling supports parallel execution", "tools.md");
            retriever.add("Java supports virtual threads", "java.md");
            tool = new RetrieverTool(retriever);
        }

        @Test
        void definition_hasCorrectNameAndParams() {
            var def = tool.definition();
            assertEquals("retrieve", def.name());
            assertTrue(def.description().contains("Retrieve"));
        }

        @Test
        void execute_basicQuery() throws Exception {
            ToolContext ctx = new ToolContext(null, null, Map.of(), null);
            ToolResult result = tool.execute("c1", Map.of("query", "agent architecture"), ctx);
            assertFalse(result.text().contains("ERROR"));
            assertTrue(result.text().contains("ReAct"));
        }

        @Test
        void execute_missingQuery() throws Exception {
            ToolContext ctx = new ToolContext(null, null, Map.of(), null);
            ToolResult result = tool.execute("c2", Map.of(), ctx);
            assertTrue(result.text().contains("ERROR"));
        }

        @Test
        void execute_blankQuery() throws Exception {
            ToolContext ctx = new ToolContext(null, null, Map.of(), null);
            ToolResult result = tool.execute("c3", Map.of("query", "  "), ctx);
            assertTrue(result.text().contains("ERROR"));
        }

        @Test
        void execute_noMatch() throws Exception {
            ToolContext ctx = new ToolContext(null, null, Map.of(), null);
            ToolResult result = tool.execute("c4", Map.of("query", "xyzzy nonsense"), ctx);
            assertEquals("No matching results.", result.text());
        }

        @Test
        void execute_resultContainsScoreAndSource() throws Exception {
            ToolContext ctx = new ToolContext(null, null, Map.of(), null);
            ToolResult result = tool.execute("c5", Map.of("query", "agent architecture"), ctx);
            assertTrue(result.text().contains("score="));
            assertTrue(result.text().contains("[arch.md]"));
        }

        @Test
        void customNameAndDescription() {
            RetrieverTool custom = new RetrieverTool(retriever, "search", "Custom search");
            assertEquals("search", custom.definition().name());
            assertEquals("Custom search", custom.definition().description());
        }
    }
}
