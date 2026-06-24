package io.agentcore.util;

import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Shared text-tokenization and set-intersection utilities.
 *
 * <p>Used by {@link io.agentcore.memory.InMemoryMemoryStore} and
 * {@link io.agentcore.retrieval.InMemoryRetriever} for keyword-overlap scoring.
 */
public final class TextTokenizer {

    private TextTokenizer() {}

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\w+");

    /**
     * Tokenize text into lowercase word tokens.
     *
     * @param text input text (null or blank → empty set)
     * @return set of lowercase word tokens
     */
    public static Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) return Set.of();
        return TOKEN_PATTERN.matcher(text.toLowerCase())
                .results()
                .map(mr -> mr.group())
                .collect(Collectors.toSet());
    }

    /**
     * Compute the size of the intersection of two sets.
     * Iterates over the smaller set for efficiency.
     *
     * @param a first set
     * @param b second set
     * @param <T> element type
     * @return number of elements present in both sets
     */
    public static <T> int intersectionSize(Set<T> a, Set<T> b) {
        Set<T> smaller = a.size() <= b.size() ? a : b;
        Set<T> larger = a.size() <= b.size() ? b : a;
        int count = 0;
        for (T token : smaller) {
            if (larger.contains(token)) count++;
        }
        return count;
    }
}
