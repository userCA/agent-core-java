package io.agentcore.retrieval;

import io.agentcore.util.TextTokenizer;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory keyword-overlap {@link Retriever} for tests and small-scale demos.
 *
 * <p>Documents are tokenized on word boundaries and ranked by the fraction
 * of query tokens they contain (Jaccard-like score). Metadata filters are
 * applied as equality checks before scoring.
 *
 * <p>Thread-safe: backed by a {@link CopyOnWriteArrayList}.
 *
 * <pre>{@code
 * InMemoryRetriever retriever = new InMemoryRetriever();
 * retriever.add("Agent architecture uses ReAct loop", "docs/arch.md");
 * retriever.add("Tool calling supports parallel execution", "docs/tools.md");
 *
 * List<RetrievedChunk> results = retriever.retrieve(
 *         new Query("agent tool calling", 5)).get();
 * }</pre>
 */
public class InMemoryRetriever implements Retriever {

    private final List<Doc> docs = new CopyOnWriteArrayList<>();

    /** Add a document to the index. */
    public void add(String text) {
        add(text, null, Map.of());
    }

    /** Add a document with a source tag. */
    public void add(String text, String source) {
        add(text, source, Map.of());
    }

    /** Add a document with source and metadata. */
    public void add(String text, String source, Map<String, Object> metadata) {
        docs.add(new Doc(text, source, metadata != null ? Map.copyOf(metadata) : Map.of(),
                TextTokenizer.tokenize(text)));
    }

    /** Returns the number of indexed documents. */
    public int size() {
        return docs.size();
    }

    @Override
    public CompletableFuture<List<RetrievedChunk>> retrieve(Query query) {
        return CompletableFuture.supplyAsync(() -> {
            Set<String> qTokens = TextTokenizer.tokenize(query.text());
            if (qTokens.isEmpty()) return List.<RetrievedChunk>of();

            List<ScoredDoc> scored = new ArrayList<>();
            for (Doc doc : docs) {
                // Apply metadata filters
                if (!matchesFilters(doc, query.filters())) continue;

                int overlap = TextTokenizer.intersectionSize(qTokens, doc.tokens());
                if (overlap == 0) continue;

                // Score = overlap / queryTokens → fraction of query covered
                double score = (double) overlap / qTokens.size();
                scored.add(new ScoredDoc(doc, score));
            }

            scored.sort((a, b) -> Double.compare(b.score(), a.score()));
            return scored.stream()
                    .limit(query.topK())
                    .map(s -> new RetrievedChunk(s.doc().text(), s.score(),
                            s.doc().source(), s.doc().metadata()))
                    .toList();
        });
    }

    private static boolean matchesFilters(Doc doc, Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) return true;
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            Object docVal = doc.metadata().get(entry.getKey());
            if (!Objects.equals(docVal, entry.getValue())) return false;
        }
        return true;
    }

    private record Doc(String text, String source, Map<String, Object> metadata, Set<String> tokens) {}
    private record ScoredDoc(Doc doc, double score) {}
}
