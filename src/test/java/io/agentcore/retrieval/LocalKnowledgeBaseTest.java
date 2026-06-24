package io.agentcore.retrieval;

import io.agentcore.retrieval.Query;
import io.agentcore.retrieval.RetrievedChunk;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LocalKnowledgeBase.
 */
class LocalKnowledgeBaseTest {

    @TempDir
    Path tempDir;

    /**
     * Simple hash-based embedding for testing (not real embeddings).
     */
    private static double[] simpleEmbed(String text) {
        double[] vec = new double[64];
        for (int i = 0; i < text.length(); i++) {
            vec[i % 64] += text.charAt(i) / 128.0;
        }
        // Normalize
        double norm = 0;
        for (double v : vec) norm += v * v;
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < vec.length; i++) vec[i] /= norm;
        }
        return vec;
    }

    // ── Chunking ─────────────────────────────────────────────

    @Test
    void chunkTextShort() {
        var chunks = LocalKnowledgeBase.chunkText("Hello world");
        assertEquals(1, chunks.size());
        assertEquals("Hello world", chunks.get(0));
    }

    @Test
    void chunkTextParagraphs() {
        // Short paragraphs may be merged since they're under CHUNK_SIZE (500)
        String text = "Paragraph one.\n\nParagraph two.\n\nParagraph three.";
        var chunks = LocalKnowledgeBase.chunkText(text);
        assertTrue(chunks.size() >= 1);
        String joined = String.join(" ", chunks);
        assertTrue(joined.contains("Paragraph one"));
        assertTrue(joined.contains("Paragraph three"));
    }

    @Test
    void chunkTextLongParagraph() {
        String longPara = "A".repeat(600);
        var chunks = LocalKnowledgeBase.chunkText(longPara + "\n\n" + "Short.");
        assertTrue(chunks.size() >= 2);
    }

    @Test
    void chunkTextEmpty() {
        var chunks = LocalKnowledgeBase.chunkText("");
        assertEquals(1, chunks.size());
    }

    // ── Cosine similarity ────────────────────────────────────

    @Test
    void cosineSimilarityIdentical() {
        double[] v = {1.0, 0.0, 0.0};
        assertEquals(1.0, LocalKnowledgeBase.cosineSimilarity(v, v), 0.001);
    }

    @Test
    void cosineSimilarityOrthogonal() {
        double[] a = {1.0, 0.0};
        double[] b = {0.0, 1.0};
        assertEquals(0.0, LocalKnowledgeBase.cosineSimilarity(a, b), 0.001);
    }

    @Test
    void cosineSimilarityDifferentLengths() {
        assertEquals(0.0, LocalKnowledgeBase.cosineSimilarity(
                new double[]{1, 2}, new double[]{1, 2, 3}));
    }

    // ── Sanitize ─────────────────────────────────────────────

    @Test
    void sanitizeNormal() {
        assertEquals("hello-world", LocalKnowledgeBase.sanitize("hello-world"));
    }

    @Test
    void sanitizeSpecialChars() {
        String result = LocalKnowledgeBase.sanitize("hello/world:bad");
        assertFalse(result.contains("/"));
        assertFalse(result.contains(":"));
    }

    @Test
    void sanitizeEmpty() {
        assertEquals("doc", LocalKnowledgeBase.sanitize(""));
    }

    // ── Document management ──────────────────────────────────

    @Test
    void addAndListDocs() throws IOException {
        var kb = new LocalKnowledgeBase(tempDir, LocalKnowledgeBaseTest::simpleEmbed);
        kb.add("test-doc", "This is a test document with some content for chunking.");

        var docs = kb.listDocs();
        assertEquals(1, docs.size());
        assertEquals("test-doc", docs.get(0).get("name"));
    }

    @Test
    void addAndDelete() throws IOException {
        var kb = new LocalKnowledgeBase(tempDir, LocalKnowledgeBaseTest::simpleEmbed);
        kb.add("to-delete", "Content to delete.");

        assertTrue(kb.delete("to-delete"));
        assertTrue(kb.listDocs().isEmpty());
    }

    @Test
    void deleteNonexistent() throws IOException {
        var kb = new LocalKnowledgeBase(tempDir);
        assertFalse(kb.delete("nonexistent"));
    }

    @Test
    void addOverwritesExisting() throws IOException {
        var kb = new LocalKnowledgeBase(tempDir, LocalKnowledgeBaseTest::simpleEmbed);
        kb.add("my-doc", "Version 1");
        kb.add("my-doc", "Version 2 with different content");

        var docs = kb.listDocs();
        assertEquals(1, docs.size());

        var doc = kb.getDoc("my-doc");
        assertNotNull(doc);
        assertEquals("Version 2 with different content", doc.get("content"));
    }

    @Test
    void getDoc() throws IOException {
        var kb = new LocalKnowledgeBase(tempDir, LocalKnowledgeBaseTest::simpleEmbed);
        kb.add("readable", "Full document content here.");

        var doc = kb.getDoc("readable");
        assertNotNull(doc);
        assertEquals("readable", doc.get("name"));
        assertEquals("Full document content here.", doc.get("content"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> chunks = (List<Map<String, Object>>) doc.get("chunks");
        assertFalse(chunks.isEmpty());
    }

    @Test
    void getDocNonexistent() throws IOException {
        var kb = new LocalKnowledgeBase(tempDir);
        assertNull(kb.getDoc("nonexistent"));
    }

    @Test
    void setTags() throws IOException {
        var kb = new LocalKnowledgeBase(tempDir, LocalKnowledgeBaseTest::simpleEmbed);
        kb.add("tagged", "Content");
        assertTrue(kb.setTags("tagged", List.of("important", "java")));

        var doc = kb.getDoc("tagged");
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) doc.get("tags");
        assertTrue(tags.contains("important"));
        assertTrue(tags.contains("java"));
    }

    @Test
    void setTagsNonexistent() throws IOException {
        var kb = new LocalKnowledgeBase(tempDir);
        assertFalse(kb.setTags("nope", List.of("tag")));
    }

    // ── Retrieval ────────────────────────────────────────────

    @Test
    void retrieveWithEmbeddings() throws Exception {
        var kb = new LocalKnowledgeBase(tempDir, LocalKnowledgeBaseTest::simpleEmbed);
        kb.add("doc1", "Java programming language features and syntax.");
        kb.add("doc2", "Python is a dynamic scripting language.");

        var future = kb.retrieve(new Query("Java programming", 3));
        var results = future.get();
        assertNotNull(results);
        // Results should include at least some chunks
        assertFalse(results.isEmpty());
    }

    @Test
    void retrieveNoEmbeddings() throws Exception {
        var kb = new LocalKnowledgeBase(tempDir); // no-op embedding
        kb.add("doc1", "Some content");

        var future = kb.retrieve(new Query("test", 3));
        var results = future.get();
        // With no-op embeddings (empty vectors), retrieval returns empty
        assertTrue(results.isEmpty());
    }

    // ── chunkAndSave + embedAll ──────────────────────────────

    @Test
    void chunkAndSaveThenEmbed() throws IOException {
        var kb = new LocalKnowledgeBase(tempDir, LocalKnowledgeBaseTest::simpleEmbed);
        var entry = kb.chunkAndSave("deferred", "Content for deferred embedding.");
        assertEquals("deferred", entry.getKey());
        assertTrue(entry.getValue() > 0);

        int embedded = kb.embedAll("deferred");
        assertTrue(embedded > 0);
    }

    @Test
    void embedAllNonexistent() throws IOException {
        var kb = new LocalKnowledgeBase(tempDir, LocalKnowledgeBaseTest::simpleEmbed);
        assertEquals(0, kb.embedAll("nonexistent"));
    }
}
