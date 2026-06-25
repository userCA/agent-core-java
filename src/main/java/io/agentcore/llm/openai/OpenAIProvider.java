package io.agentcore.llm.openai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentcore.llm.ModelInfo;
import io.agentcore.llm.ModelProvider;
import io.agentcore.llm.ProviderAuth;
import io.agentcore.llm.ProviderUtils;
import io.agentcore.llm.StreamEvent;
import io.agentcore.llm.StreamEvent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OpenAI / OpenAI-compatible provider with SSE streaming.
 *
 * <p>Mirrors Python {@code agent_core/providers/openai_provider.py}.
 * Uses {@link java.net.http.HttpClient} for HTTP and manual SSE line parsing.
 */
public class OpenAIProvider implements ModelProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAIProvider.class);
    private static final ObjectMapper MAPPER = ProviderUtils.mapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final String providerName;
    private final String baseUrl;
    private final List<ModelInfo> models;
    private final Duration timeout;
    private final HttpClient httpClient;

    public OpenAIProvider(String baseUrl, String providerName, List<ModelInfo> models, Duration timeout) {
        this.providerName = providerName;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.models = models != null ? List.copyOf(models) : defaultModels(providerName);
        this.timeout = timeout != null ? timeout : Duration.ofSeconds(60);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .build();
    }

    public OpenAIProvider() {
        this("https://api.openai.com/v1", "openai", null, null);
    }

    public OpenAIProvider(String baseUrl, String providerName) {
        this(baseUrl, providerName, null, null);
    }

    @Override
    public String name() {
        return providerName;
    }

    @Override
    public List<ModelInfo> listModels() {
        return models;
    }

    @Override
    public Iterator<StreamEvent> stream(
            ModelInfo model,
            List<Map<String, Object>> messages,
            List<Map<String, Object>> tools,
            String systemPrompt,
            String thinkingLevel,
            Double temperature,
            Integer maxTokens,
            AtomicBoolean abortSignal,
            ProviderAuth auth) {

        Map<String, Object> payload = buildPayload(model, messages, tools, systemPrompt,
                thinkingLevel, temperature, maxTokens);

        // Use a queue to bridge SSE lines from HTTP thread to the consumer iterator
        LinkedBlockingQueue<StreamEvent> queue = new LinkedBlockingQueue<>(10_000);
        AtomicBoolean done = new AtomicBoolean(false);

        Thread.ofVirtual().name("openai-sse-" + System.nanoTime()).start(() -> {
            try {
                doStreamRequest(payload, auth, abortSignal, queue, done);
            } catch (Exception e) {
                queue.offer(new StreamError("Stream failed: " + e.getMessage(), true, false));
            } finally {
                done.set(true);
                // No null sentinel needed — QueueBackedIterator terminates via done flag
            }
        });

        return new ProviderUtils.QueueBackedIterator<>(queue, done);
    }

    // ── Payload building ─────────────────────────────────────

    private Map<String, Object> buildPayload(
            ModelInfo model,
            List<Map<String, Object>> messages,
            List<Map<String, Object>> tools,
            String systemPrompt,
            String thinkingLevel,
            Double temperature,
            Integer maxTokens) {

        List<Map<String, Object>> msgs = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            msgs.add(Map.of("role", "system", "content", systemPrompt));
        }
        msgs.addAll(messages);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model.id());
        payload.put("messages", msgs);
        payload.put("stream", true);
        payload.put("stream_options", Map.of("include_usage", true));

        if (tools != null && !tools.isEmpty()) {
            payload.put("tools", tools);
        }
        if (temperature != null) {
            payload.put("temperature", temperature);
        }
        if (maxTokens != null) {
            payload.put("max_tokens", maxTokens);
        }
        if (thinkingLevel != null && !"off".equals(thinkingLevel) && model.supportsReasoning()) {
            payload.put("reasoning_effort", thinkingLevel);
        }
        return payload;
    }

    // ── HTTP + SSE parsing ───────────────────────────────────

    private void doStreamRequest(
            Map<String, Object> payload,
            ProviderAuth auth,
            AtomicBoolean abortSignal,
            LinkedBlockingQueue<StreamEvent> queue,
            AtomicBoolean done) throws Exception {

        String url = baseUrl + "/chat/completions";
        String jsonBody = MAPPER.writeValueAsString(payload);

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .header("Authorization", "Bearer " + auth.apiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));

        if (auth.extraHeaders() != null) {
            auth.extraHeaders().forEach(reqBuilder::header);
        }

        HttpResponse<java.io.InputStream> response = httpClient.send(
                reqBuilder.build(),
                HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() >= 400) {
            try (var body = response.body()) {
                String bodyText = new String(body.readAllBytes(), StandardCharsets.UTF_8);
                var error = ProviderUtils.httpError(response.statusCode(), bodyText);
                log.warn("OpenAI HTTP {}: retryable={}, overflow={}", response.statusCode(), error.retryable(), error.overflow());
                queue.offer(error);
            }
            return;
        }

        parseSse(response.body(), abortSignal, queue);
    }

    private void parseSse(
            java.io.InputStream inputStream,
            AtomicBoolean abortSignal,
            LinkedBlockingQueue<StreamEvent> queue) throws Exception {

        Map<Integer, Map<String, Object>> toolCalls = new LinkedHashMap<>();
        Set<String> startedTools = new HashSet<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String rawLine;
            while ((rawLine = reader.readLine()) != null) {
                if (abortSignal != null && abortSignal.get()) break;

                String line = rawLine.strip();
                if (line.isEmpty() || !line.startsWith("data:")) continue;

                String data = line.substring(5).strip();
                if ("[DONE]".equals(data)) return;

                Map<String, Object> event;
                try {
                    event = MAPPER.readValue(data, MAP_TYPE);
                } catch (Exception e) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) event.get("choices");
                if (choices == null || choices.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> usage = (Map<String, Object>) event.get("usage");
                    if (usage != null) {
                        queue.offer(new StreamMessageEnd("stop",
                                ProviderUtils.toInt(usage.get("prompt_tokens")),
                                ProviderUtils.toInt(usage.get("completion_tokens"))));
                    }
                    continue;
                }

                Map<String, Object> choice = choices.get(0);
                @SuppressWarnings("unchecked")
                Map<String, Object> delta = (Map<String, Object>) choice.get("delta");
                if (delta == null) delta = Map.of();

                // Text delta
                Object textObj = delta.get("content");
                if (textObj instanceof String text && !text.isEmpty()) {
                    queue.offer(new StreamTextDelta(text));
                }

                // Tool calls
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> tcList = (List<Map<String, Object>>) delta.get("tool_calls");
                if (tcList != null) {
                    for (Map<String, Object> tc : tcList) {
                        int idx = ProviderUtils.toInt(tc.get("index"));
                        Map<String, Object> slot = toolCalls.computeIfAbsent(idx, k -> {
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("id", null);
                            m.put("name", null);
                            m.put("args", new StringBuilder());
                            return m;
                        });
                        Object id = tc.get("id");
                        if (id != null) slot.put("id", id.toString());
                        @SuppressWarnings("unchecked")
                        Map<String, Object> fn = (Map<String, Object>) tc.get("function");
                        if (fn != null) {
                            Object name = fn.get("name");
                            if (name != null) slot.put("name", name.toString());
                            Object args = fn.get("arguments");
                            if (args != null) {
                                ((StringBuilder) slot.get("args")).append(args);
                            }
                        }
                        String slotId = (String) slot.get("id");
                        String slotName = (String) slot.get("name");
                        if (slotId != null && slotName != null && !startedTools.contains(slotId)) {
                            startedTools.add(slotId);
                            queue.offer(new StreamToolCallStart(slotId, slotName));
                        }
                    }
                }

                // Finish reason
                Object finish = choice.get("finish_reason");
                if (finish != null) {
                    for (Map<String, Object> slot : toolCalls.values()) {
                        String slotId = (String) slot.get("id");
                        String slotName = (String) slot.get("name");
                        if (slotId == null || slotName == null) {
                            // Incomplete tool call from fragmented streaming — skip defensively
                            log.debug("Skipping incomplete tool call slot: id={}, name={}", slotId, slotName);
                            continue;
                        }
                        Map<String, Object> args = ProviderUtils.parseJsonMap(slot.get("args").toString());
                        queue.offer(new StreamToolCallEnd(slotId, args));
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> usage = (Map<String, Object>) event.get("usage");
                    if (usage == null) usage = Map.of();
                    queue.offer(new StreamMessageEnd(
                            finish.toString(),
                            ProviderUtils.toInt(usage.get("prompt_tokens")),
                            ProviderUtils.toInt(usage.get("completion_tokens"))));
                }
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────

    static List<ModelInfo> defaultModels(String providerName) {
        return List.of(
                new ModelInfo(providerName, "gpt-4o", 128_000, 4096),
                new ModelInfo(providerName, "gpt-4o-mini", 128_000, 16_384)
        );
    }

}
