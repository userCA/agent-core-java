package io.agentcore.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentcore.llm.ProviderUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Local knowledge base — file-backed document store with pluggable embeddings.
 *
 * <p>Mirrors Python {@code agent_core/knowledge/local_kb.py} LocalKnowledgeBase.
 * Each document is stored as a subdirectory:
 * <pre>
 *   {name}/meta.json     — {name, original_name, created, chunk_count, tags}
 *   {name}/content.txt   — original full content
 *   {name}/chunk_000.txt — chunk text
 *   {name}/chunk_000.vec — embedding vector (comma-separated doubles)
 * </pre>
 */
public class LocalKnowledgeBase implements Retriever {

    private static final Logger log = LoggerFactory.getLogger(LocalKnowledgeBase.class);
    private static final ObjectMapper MAPPER = ProviderUtils.mapper();

    private static final Pattern SENT_SPLIT = Pattern.compile("(?<=[。！？.!?\\n])\\s*");
    public static final int CHUNK_SIZE = 500;
    public static final int CHUNK_OVERLAP = 80;
    private static final double MIN_SCORE = 0.2;

    /**
     * Pluggable embedding function: takes a text string, returns a normalized float vector.
     */
    @FunctionalInterface
    public interface EmbeddingFunction {
        double[] embed(String text);
    }

    private final Path directory;
    private final EmbeddingFunction embeddingFn;
    private final Map<Path, double[]> vectorCache = new ConcurrentHashMap<>();

    /**
     * Create a knowledge base in the given directory with a pluggable embedding function.
     */
    public LocalKnowledgeBase(Path directory, EmbeddingFunction embeddingFn) throws IOException {
        this.directory = directory;
        this.embeddingFn = embeddingFn;
        Files.createDirectories(directory);
    }

    /**
     * Create a knowledge base with a no-op embedding function (text-only, no vector search).
     */
    public LocalKnowledgeBase(Path directory) throws IOException {
        this(directory, text -> new double[0]);
    }

    // ── Document management ──────────────────────────────────

    /**
     * Add a document: chunk, embed, and store to disk.
     *
     * @return the sanitized document name
     */
    public String add(String name, String content) throws IOException {
        String safe = sanitize(name);
        delete(safe);
        Path docDir = directory.resolve(safe);
        Files.createDirectories(docDir);

        // Store original content
        Files.writeString(docDir.resolve("content.txt"), content);

        // Chunk and embed
        List<String> chunks = chunkText(content);
        for (int i = 0; i < chunks.size(); i++) {
            String chunkFile = String.format("chunk_%03d.txt", i);
            Files.writeString(docDir.resolve(chunkFile), chunks.get(i));

            if (embeddingFn != null) {
                double[] vec = embeddingFn.embed(chunks.get(i));
                if (vec != null && vec.length > 0) {
                    Path vecPath = docDir.resolve(String.format("chunk_%03d.vec", i));
                    Files.writeString(vecPath, vectorToString(vec));
                    vectorCache.put(vecPath, vec);
                }
            }
        }

        // Write metadata
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("name", safe);
        meta.put("original_name", name);
        meta.put("created", System.currentTimeMillis() / 1000.0);
        meta.put("chunk_count", chunks.size());
        meta.put("tags", List.of());
        writeMeta(docDir, meta);

        return safe;
    }

    /**
     * Chunk text without embedding — call {@link #embedAll(String)} separately.
     */
    public Map.Entry<String, Integer> chunkAndSave(String name, String content) throws IOException {
        String safe = sanitize(name);
        delete(safe);
        Path docDir = directory.resolve(safe);
        Files.createDirectories(docDir);

        List<String> chunks = chunkText(content);
        for (int i = 0; i < chunks.size(); i++) {
            Files.writeString(docDir.resolve(String.format("chunk_%03d.txt", i)), chunks.get(i));
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("name", safe);
        meta.put("original_name", name);
        meta.put("created", System.currentTimeMillis() / 1000.0);
        meta.put("chunk_count", chunks.size());
        writeMeta(docDir, meta);

        return Map.entry(safe, chunks.size());
    }

    /**
     * Embed all un-embedded chunks for a document.
     */
    public int embedAll(String name) throws IOException {
        Path docDir = directory.resolve(name);
        if (!Files.isDirectory(docDir)) return 0;

        int count = 0;
        try (Stream<Path> files = Files.list(docDir)) {
            for (Path txtPath : files.sorted().toList()) {
                String fileName = txtPath.getFileName().toString();
                if (!fileName.startsWith("chunk_") || !fileName.endsWith(".txt")) continue;

                String vecName = fileName.replace(".txt", ".vec");
                Path vecPath = docDir.resolve(vecName);
                if (Files.exists(vecPath)) continue;

                String text = Files.readString(txtPath);
                double[] vec = embeddingFn.embed(text);
                if (vec != null && vec.length > 0) {
                    Files.writeString(vecPath, vectorToString(vec));
                    vectorCache.put(vecPath, vec);
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Set tags for a document.
     */
    public boolean setTags(String name, List<String> tags) throws IOException {
        Path docDir = directory.resolve(name);
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = readMeta(docDir);
        if (meta == null) return false;
        meta.put("tags", tags);
        writeMeta(docDir, meta);
        return true;
    }

    /**
     * List all documents with metadata.
     */
    public List<Map<String, Object>> listDocs() throws IOException {
        List<Map<String, Object>> docs = new ArrayList<>();
        if (!Files.isDirectory(directory)) return docs;

        try (Stream<Path> entries = Files.list(directory)) {
            for (Path docDir : entries.sorted().toList()) {
                if (!Files.isDirectory(docDir)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> meta = readMeta(docDir);
                if (meta == null) continue;

                // Count actual chunk files
                long txtCount;
                try (Stream<Path> chunks = Files.list(docDir)) {
                    txtCount = chunks.filter(p -> {
                        String n = p.getFileName().toString();
                        return n.startsWith("chunk_") && n.endsWith(".txt");
                    }).count();
                }
                meta.put("chunk_count", txtCount > 0 ? (int) txtCount :
                        ((Number) meta.getOrDefault("chunk_count", 0)).intValue());
                docs.add(meta);
            }
        }
        return docs;
    }

    /**
     * Get a single document with chunk previews and full content.
     */
    public Map<String, Object> getDoc(String name) throws IOException {
        Path docDir = directory.resolve(name);
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = readMeta(docDir);
        if (meta == null) return null;

        // Read full content
        Path contentFile = docDir.resolve("content.txt");
        String content = Files.exists(contentFile) ? Files.readString(contentFile) : "";
        meta.put("content", content);

        // Read chunk previews
        List<Map<String, Object>> chunks = new ArrayList<>();
        try (Stream<Path> files = Files.list(docDir)) {
            for (Path txtPath : files.sorted().toList()) {
                String fileName = txtPath.getFileName().toString();
                if (!fileName.startsWith("chunk_") || !fileName.endsWith(".txt")) continue;
                String text = Files.readString(txtPath);
                String index = fileName.replace("chunk_", "").replace(".txt", "");
                chunks.add(Map.of(
                        "index", index,
                        "text", text.length() > 200 ? text.substring(0, 200) : text,
                        "full_length", text.length()
                ));
            }
        }
        meta.put("chunks", chunks);
        return meta;
    }

    /**
     * Delete a document directory.
     */
    public boolean delete(String name) throws IOException {
        Path docDir = directory.resolve(name);
        if (!Files.isDirectory(docDir)) return false;

        // Invalidate cached vectors for this document
        vectorCache.keySet().removeIf(p -> p.startsWith(docDir));

        try (Stream<Path> walk = Files.walk(docDir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException e) {
                    log.debug("Failed to delete file during cleanup: {}", p, e);
                }
            });
        }
        return true;
    }

    // ── Retrieval ────────────────────────────────────────────

    /**
     * Retrieve chunks matching a query using cosine similarity on embeddings.
     */
    @Override
    public List<RetrievedChunk> retrieve(Query query) {
        try {
            double[] qVec = embeddingFn.embed(query.text());
            if (qVec == null || qVec.length == 0) return List.of();

            List<ScoredChunk> candidates = new ArrayList<>();

            if (!Files.isDirectory(directory)) return List.of();

            try (Stream<Path> docs = Files.list(directory)) {
                for (Path docDir : docs.sorted().toList()) {
                    if (!Files.isDirectory(docDir)) continue;
                    String docName = docDir.getFileName().toString();

                    try (Stream<Path> files = Files.list(docDir)) {
                        for (Path vecPath : files.sorted().toList()) {
                            String fileName = vecPath.getFileName().toString();
                            if (!fileName.endsWith(".vec")) continue;

                            Path txtPath = docDir.resolve(fileName.replace(".vec", ".txt"));
                            if (!Files.exists(txtPath)) continue;

                            try {
                                double[] docVec = vectorCache.computeIfAbsent(vecPath, p -> {
                                    try { return readVector(p); }
                                    catch (IOException e) { throw new RuntimeException(e); }
                                });
                                String text = Files.readString(txtPath);
                                double score = cosineSimilarity(qVec, docVec);

                                if (score >= MIN_SCORE) {
                                    String chunkId = fileName.replace(".vec", "").replace("chunk_", "");
                                    candidates.add(new ScoredChunk(text, score, docName + "/" + chunkId));
                                }
                            } catch (Exception e) {
                                log.debug("Failed to load chunk for {}", docName, e);
                            }
                        }
                    }
                }
            }

            return candidates.stream()
                    .sorted((a, b) -> Double.compare(b.score(), a.score()))
                    .limit(query.topK())
                    .map(c -> new RetrievedChunk(
                            c.text().length() > 1200 ? c.text().substring(0, 1200) : c.text(),
                            Math.round(c.score() * 10000.0) / 10000.0,
                            c.source()))
                    .toList();
        } catch (IOException e) {
            log.warn("Retrieval failed", e);
            return List.of();
        }
    }

    // ── Chunking ─────────────────────────────────────────────

    /**
     * Split text into chunks of approximately CHUNK_SIZE characters.
     */
    public static List<String> chunkText(String text) {
        List<String> pieces = new ArrayList<>();
        for (String para : text.split("\\n\\n")) {
            para = para.strip();
            if (para.isEmpty()) continue;
            if (para.length() <= CHUNK_SIZE) {
                pieces.add(para);
            } else {
                String[] sentences = SENT_SPLIT.split(para);
                for (String s : sentences) {
                    s = s.strip();
                    if (!s.isEmpty()) pieces.add(s);
                }
            }
        }

        List<String> chunks = new ArrayList<>();
        String buf = "";
        for (String piece : pieces) {
            if (piece.length() > CHUNK_SIZE * 2) {
                for (int i = 0; i < piece.length(); i += CHUNK_SIZE - CHUNK_OVERLAP) {
                    chunks.add(piece.substring(i, Math.min(i + CHUNK_SIZE, piece.length())));
                }
                buf = "";
                continue;
            }
            String candidate = buf.isEmpty() ? piece : (buf + " " + piece).strip();
            if (candidate.length() <= CHUNK_SIZE) {
                buf = candidate;
            } else {
                if (!buf.isEmpty()) chunks.add(buf);
                buf = piece;
            }
        }
        if (!buf.isEmpty()) chunks.add(buf);

        return chunks.isEmpty() ? List.of(text) : chunks;
    }

    // ── Vector helpers ───────────────────────────────────────

    static double cosineSimilarity(double[] a, double[] b) {
        if (a.length != b.length) return 0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0 : dot / denom;
    }

    private static String vectorToString(double[] vec) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vec[i]);
        }
        return sb.toString();
    }

    private static double[] readVector(Path path) throws IOException {
        String content = Files.readString(path).strip();
        String[] parts = content.split(",");
        double[] vec = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vec[i] = Double.parseDouble(parts[i].strip());
        }
        return vec;
    }

    // ── Metadata helpers ─────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMeta(Path docDir) {
        Path metaPath = docDir.resolve("meta.json");
        try {
            return MAPPER.readValue(Files.readString(metaPath), Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    private void writeMeta(Path docDir, Map<String, Object> meta) throws IOException {
        Files.writeString(docDir.resolve("meta.json"),
                MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(meta));
    }

    static String sanitize(String name) {
        String safe = name.replaceAll("[^a-zA-Z0-9_\\-. ]", "_").strip();
        return safe.isEmpty() ? "doc" : safe;
    }

    private record ScoredChunk(String text, double score, String source) {}
}
