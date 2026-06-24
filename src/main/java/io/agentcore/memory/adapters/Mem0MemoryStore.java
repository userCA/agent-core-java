package io.agentcore.memory.adapters;

import io.agentcore.memory.MemoryRecord;
import io.agentcore.memory.MemoryStore;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * {@link MemoryStore} adapter for the Mem0 memory service.
 *
 * <p>Mirrors Python {@code agent_core/memory/adapters/mem0_adapter.py}.
 * Communicates with a Mem0-compatible REST API.
 *
 * <p>Usage:
 * <pre>{@code
 *   Mem0MemoryStore store = new Mem0MemoryStore("http://localhost:8081", "");
 *   store.remember("session-1", "User likes Java", null);
 *   List<MemoryRecord> records = store.recall("session-1", "programming", 5);
 * }</pre>
 */
public class Mem0MemoryStore implements MemoryStore {

    private static final Logger log = LoggerFactory.getLogger(Mem0MemoryStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final String baseUrl;
    private final String apiKey;
    private final HttpClient httpClient;

    /**
     * Create a Mem0 adapter with a custom base URL and API key.
     *
     * @param baseUrl the Mem0 service URL (e.g. "http://localhost:8081")
     * @param apiKey  optional API key (empty string if not required)
     */
    public Mem0MemoryStore(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.apiKey = apiKey != null ? apiKey : "";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
    }

    /**
     * Create a Mem0 adapter with default localhost URL.
     */
    public Mem0MemoryStore() {
        this("http://localhost:8081", "");
    }

    @Override
    public void remember(String sessionId, String text, Map<String, Object> metadata) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("messages", List.of(Map.of("role", "user", "content", text)));
            body.put("user_id", sessionId);
            body.put("metadata", metadata != null ? metadata : Map.of());

            String json = MAPPER.writeValueAsString(body);
            HttpRequest request = buildPostRequest("/v1/memories/", json);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                log.warn("Mem0 remember failed: {} {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("Mem0 remember error: {}", e.getMessage(), e);
        }
    }

    @Override
    public List<MemoryRecord> recall(String sessionId, String query, int limit) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("query", query);
            body.put("filters", Map.of("user_id", sessionId));
            body.put("top_k", limit);

            String json = MAPPER.writeValueAsString(body);
            HttpRequest request = buildPostRequest("/v1/memories/search/", json);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                log.warn("Mem0 recall failed: {} {}", response.statusCode(), response.body());
                return List.of();
            }

            return parseRecallResponse(response.body(), sessionId);
        } catch (Exception e) {
            log.warn("Mem0 recall error: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public void forget(String sessionId) {
        try {
            Map<String, Object> body = Map.of("user_id", sessionId);
            String json = MAPPER.writeValueAsString(body);
            HttpRequest request = buildPostRequest("/v1/memories/delete/", json);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                log.warn("Mem0 forget failed: {} {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("Mem0 forget error: {}", e.getMessage(), e);
        }
    }

    // ── Helpers ──

    private HttpRequest buildPostRequest(String path, String json) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (!apiKey.isEmpty()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private List<MemoryRecord> parseRecallResponse(String responseBody, String sessionId) {
        try {
            Map<String, Object> data = MAPPER.readValue(responseBody,
                    new TypeReference<Map<String, Object>>() {});
            List<Map<String, Object>> results = (List<Map<String, Object>>)
                    data.getOrDefault("results", List.of());

            List<MemoryRecord> records = new ArrayList<>();
            for (Map<String, Object> item : results) {
                String text = (String) item.getOrDefault("memory",
                        item.getOrDefault("text", item.getOrDefault("content", "")));
                Map<String, Object> meta = (Map<String, Object>)
                        item.getOrDefault("metadata", Map.of());
                records.add(new MemoryRecord(text, sessionId, Instant.now(), meta));
            }
            return records;
        } catch (Exception e) {
            log.warn("Mem0 parse error: {}", e.getMessage(), e);
            return List.of();
        }
    }
}
