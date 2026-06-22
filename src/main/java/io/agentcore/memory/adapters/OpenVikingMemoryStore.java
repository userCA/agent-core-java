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
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * {@link MemoryStore} adapter for the OpenViking memory service.
 *
 * <p>Mirrors Python {@code agent_core/memory/adapters/openviking_adapter.py}.
 * Communicates with an OpenViking-compatible REST API.
 *
 * <p>Features:
 * <ul>
 *   <li>Per-session API key resolution</li>
 *   <li>Configurable agent ID</li>
 *   <li>Session commit after remember (ensures memory persistence)</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 *   OpenVikingMemoryStore store = OpenVikingMemoryStore.builder()
 *       .url("http://localhost:1933")
 *       .apiKey("sk-xxx")
 *       .build();
 *   store.remember("session-1", "User prefers dark mode", null).join();
 *   List<MemoryRecord> records = store.recall("session-1", "UI preference", 5).join();
 * }</pre>
 */
public class OpenVikingMemoryStore implements MemoryStore {

    private static final Logger log = LoggerFactory.getLogger(OpenVikingMemoryStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final String baseUrl;
    private final String apiKey;
    private final Map<String, String> apiKeys;
    private final Function<String, String> resolveApiKeyFn;
    private final String agentId;
    private final HttpClient httpClient;

    private OpenVikingMemoryStore(Builder builder) {
        this.baseUrl = builder.url
                .replaceAll("/api/v1/?$", "")
                .replaceAll("/$", "");
        this.apiKey = builder.apiKey;
        this.apiKeys = builder.apiKeys != null ? Map.copyOf(builder.apiKeys) : Map.of();
        this.resolveApiKeyFn = builder.resolveApiKeyFn;
        this.agentId = builder.agentId != null ? builder.agentId : "";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
    }

    public OpenVikingMemoryStore() {
        this(builder().url("http://localhost:1933"));
    }

    public OpenVikingMemoryStore(String url, String apiKey) {
        this(builder().url(url).apiKey(apiKey));
    }

    public static Builder builder() {
        return new Builder();
    }

    // ── Builder ──

    public static final class Builder {
        private String url = "http://localhost:1933";
        private String apiKey = "";
        private Map<String, String> apiKeys;
        private Function<String, String> resolveApiKeyFn;
        private String agentId;

        public Builder url(String url) { this.url = url; return this; }
        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        public Builder apiKeys(Map<String, String> apiKeys) { this.apiKeys = apiKeys; return this; }
        public Builder resolveApiKey(Function<String, String> fn) { this.resolveApiKeyFn = fn; return this; }
        public Builder agentId(String agentId) { this.agentId = agentId; return this; }

        public OpenVikingMemoryStore build() {
            return new OpenVikingMemoryStore(this);
        }
    }

    // ── API Key Resolution ──

    String resolveApiKey(String sessionId) {
        if (resolveApiKeyFn != null) {
            return resolveApiKeyFn.apply(sessionId);
        }
        if (apiKeys.containsKey(sessionId)) {
            return apiKeys.get(sessionId);
        }
        // Try extracting user_id from session path (e.g. "user123/session456")
        if (sessionId.contains("/")) {
            String userId = sessionId.split("/")[0];
            if (apiKeys.containsKey(userId)) {
                return apiKeys.get(userId);
            }
        }
        return apiKey;
    }

    // ── MemoryStore ──

    @Override
    public CompletableFuture<Void> remember(String sessionId, String text, Map<String, Object> metadata) {
        return CompletableFuture.runAsync(() -> {
            try {
                String payload = text;
                if (metadata != null && !metadata.isEmpty()) {
                    payload = text + "\n[metadata: " + MAPPER.writeValueAsString(metadata) + "]";
                }

                // Add message
                Map<String, Object> addBody = new LinkedHashMap<>();
                addBody.put("session_id", sessionId);
                addBody.put("role", "user");
                addBody.put("content", payload);
                if (!agentId.isEmpty()) addBody.put("agent_id", agentId);

                String resolvedKey = resolveApiKey(sessionId);
                HttpRequest addReq = buildPostRequest("/api/v1/messages", addBody, resolvedKey);
                HttpResponse<String> addResp = httpClient.send(addReq, HttpResponse.BodyHandlers.ofString());

                if (addResp.statusCode() >= 400) {
                    log.warn("OpenViking remember (add) failed: {} {}", addResp.statusCode(), addResp.body());
                    return;
                }

                // Commit session
                Map<String, Object> commitBody = Map.of("session_id", sessionId);
                HttpRequest commitReq = buildPostRequest("/api/v1/sessions/commit", commitBody, resolvedKey);
                HttpResponse<String> commitResp = httpClient.send(commitReq, HttpResponse.BodyHandlers.ofString());

                if (commitResp.statusCode() >= 400) {
                    log.warn("OpenViking remember (commit) failed: {} {}", commitResp.statusCode(), commitResp.body());
                }
            } catch (Exception e) {
                log.warn("OpenViking remember error: {}", e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<List<MemoryRecord>> recall(String sessionId, String query, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("query", query);
                body.put("target_uri", "viking://user/memories");
                body.put("node_limit", limit);

                String resolvedKey = resolveApiKey(sessionId);
                HttpRequest request = buildPostRequest("/api/v1/search", body, resolvedKey);
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 400) {
                    log.warn("OpenViking recall failed: {} {}", response.statusCode(), response.body());
                    return List.of();
                }

                return parseRecallResponse(response.body(), sessionId);
            } catch (Exception e) {
                log.warn("OpenViking recall error: {}", e.getMessage(), e);
                return List.of();
            }
        });
    }

    @Override
    public CompletableFuture<Void> forget(String sessionId) {
        return CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> body = Map.of("session_id", sessionId);
                String resolvedKey = resolveApiKey(sessionId);
                HttpRequest request = buildPostRequest("/api/v1/sessions/delete", body, resolvedKey);
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 400) {
                    log.warn("OpenViking forget failed: {} {}", response.statusCode(), response.body());
                }
            } catch (Exception e) {
                log.warn("OpenViking forget error: {}", e.getMessage(), e);
            }
        });
    }

    // ── Helpers ──

    private HttpRequest buildPostRequest(String path, Map<String, Object> body, String resolvedApiKey)
            throws Exception {
        String json = MAPPER.writeValueAsString(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (resolvedApiKey != null && !resolvedApiKey.isEmpty()) {
            builder.header("Authorization", "Bearer " + resolvedApiKey);
        }
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private List<MemoryRecord> parseRecallResponse(String responseBody, String sessionId) {
        try {
            Map<String, Object> data = MAPPER.readValue(responseBody,
                    new TypeReference<Map<String, Object>>() {});
            List<Map<String, Object>> memories = (List<Map<String, Object>>)
                    data.getOrDefault("memories", data.getOrDefault("results", List.of()));

            List<MemoryRecord> records = new ArrayList<>();
            for (Map<String, Object> item : memories) {
                String text = (String) item.getOrDefault("abstract",
                        item.getOrDefault("text", item.getOrDefault("content", "")));
                Map<String, Object> meta = new LinkedHashMap<>();
                if (item.containsKey("uri")) meta.put("uri", item.get("uri"));
                if (item.containsKey("score")) meta.put("score", item.get("score"));
                if (item.containsKey("context_type")) meta.put("context_type", String.valueOf(item.get("context_type")));
                if (item.containsKey("level")) meta.put("level", item.get("level"));

                records.add(new MemoryRecord(text, sessionId, Instant.now(), meta));
            }
            return records;
        } catch (Exception e) {
            log.warn("OpenViking parse error: {}", e.getMessage(), e);
            return List.of();
        }
    }
}
