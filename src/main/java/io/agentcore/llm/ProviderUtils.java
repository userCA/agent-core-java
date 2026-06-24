package io.agentcore.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Shared utilities for provider implementations.
 */
public final class ProviderUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private ProviderUtils() {}

    /**
     * Parse a JSON string into a Map.
     */
    public static Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return MAPPER.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            return Map.of();
        }
    }

    /**
     * Safely convert an Object to int.
     */
    public static int toInt(Object obj) {
        if (obj instanceof Number n) return n.intValue();
        return 0;
    }

    /**
     * Detect context window overflow from HTTP error responses.
     */
    public static boolean isContextOverflow(String body) {
        if (body == null) return false;
        String lower = body.toLowerCase();
        return lower.contains("context_length_exceeded")
                || lower.contains("maximum context length")
                || lower.contains("reduce the length of the messages")
                || lower.contains("too long")
                || lower.contains("token limit")
                || lower.contains("prompt is too long")
                || lower.contains("exceeds the maximum");
    }

    private static final Set<Integer> RETRYABLE_STATUS_CODES = Set.of(429, 500, 502, 503, 504);

    /**
     * Build a {@link StreamEvent.StreamError} from an HTTP error response.
     *
     * @param statusCode HTTP status code
     * @param bodyText   response body text
     * @return a StreamError with retryable and overflow flags set
     */
    public static StreamEvent.StreamError httpError(int statusCode, String bodyText) {
        boolean retryable = RETRYABLE_STATUS_CODES.contains(statusCode);
        boolean overflow = isContextOverflow(bodyText);
        return new StreamEvent.StreamError(
                "HTTP " + statusCode + ": " + bodyText, retryable, overflow);
    }

    /**
     * Serialize a Map to JSON string.
     */
    public static String toJsonString(Map<String, Object> map) {
        try {
            return MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * Shared ObjectMapper instance.
     */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /**
     * Extract plain text from an OpenAI-format content value.
     *
     * <p>Handles:
     * <ul>
     *   <li>{@code String} — returned as-is</li>
     *   <li>{@code List<Map>} — concatenates all "text" type blocks</li>
     *   <li>Other — returns empty string</li>
     * </ul>
     */
    public static String extractText(Object content) {
        if (content instanceof String s) return s;
        if (content instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object c : list) {
                if (c instanceof Map<?, ?> map) {
                    Object type = map.get("type");
                    if ("text".equals(type)) {
                        Object textVal = map.get("text");
                        if (textVal != null) sb.append(textVal);
                    }
                } else if (c instanceof String s) {
                    sb.append(s);
                }
            }
            return sb.toString();
        }
        return "";
    }

    /**
     * Queue-backed iterator that bridges async SSE reading to sync iteration.
     */
    public static final class QueueBackedIterator<T> implements Iterator<T> {
        private final LinkedBlockingQueue<T> queue;
        private final AtomicBoolean done;
        private T next;
        private boolean hasNext;
        private boolean consumed = true;

        public QueueBackedIterator(LinkedBlockingQueue<T> queue, AtomicBoolean done) {
            this.queue = queue;
            this.done = done;
        }

        @Override
        public boolean hasNext() {
            if (!consumed) return true;
            if (hasNext) return true;
            try {
                while (true) {
                    T item = queue.poll(100, TimeUnit.MILLISECONDS);
                    if (item != null) {
                        next = item;
                        hasNext = true;
                        consumed = false;
                        return true;
                    }
                    if (done.get() && queue.isEmpty()) {
                        hasNext = false;
                        return false;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                hasNext = false;
                return false;
            }
        }

        @Override
        public T next() {
            if (!hasNext()) throw new NoSuchElementException();
            T item = next;
            next = null;
            hasNext = false;
            consumed = true;
            return item;
        }
    }
}
