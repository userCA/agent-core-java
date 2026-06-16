package io.agentcore.providers.openai;

import io.agentcore.providers.base.JsonUtils;
import io.agentcore.providers.base.ModelProvider;
import io.agentcore.providers.base.StreamRequest;
import io.agentcore.providers.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.stream.Stream;

/**
 * OpenAI / OpenAI-compatible provider.
 * Uses java.net.http.HttpClient for SSE streaming.
 */
public class OpenAIProvider implements ModelProvider {
    private static final Logger log = LoggerFactory.getLogger(OpenAIProvider.class);

    private final String baseUrl;
    private final String providerName;
    private final List<Model> models;
    private final double timeout;
    private final HttpClient httpClient;

    public OpenAIProvider() {
        this("https://api.openai.com/v1", "openai", defaultModels(), 60.0, null);
    }

    public OpenAIProvider(String baseUrl, String providerName, List<Model> models,
                          double timeout, HttpClient httpClient) {
        this.baseUrl = baseUrl;
        this.providerName = providerName;
        this.models = models != null ? models : defaultModels();
        this.timeout = timeout;
        this.httpClient = httpClient != null ? httpClient : HttpClient.newHttpClient();
    }

    private static List<Model> defaultModels() {
        return List.of(
                new Model("openai", "gpt-4o", 128000, 4096),
                new Model("openai", "gpt-4o-mini", 128000, 16384)
        );
    }

    @Override
    public String name() { return providerName; }

    @Override
    public List<Model> listModels() { return models; }

    @Override
    public void close() {
        httpClient.close();
    }

    @Override
    public Flow.Publisher<StreamEvent> stream(StreamRequest request) {
        return subscriber -> {
            Thread.ofVirtual().name("openai-stream").start(() -> {
                try {
                    doStream(request, subscriber);
                } catch (Exception e) {
                    subscriber.onError(e);
                }
            });
        };
    }

    private void doStream(StreamRequest request, Flow.Subscriber<? super StreamEvent> subscriber) throws Exception {
        var publisher = new SubmissionPublisher<StreamEvent>();
        publisher.subscribe(subscriber);

        try {
            if (request.auth() == null) {
                throw new IllegalArgumentException("Auth credentials are required");
            }

            Map<String, Object> payload = buildPayload(request);
            String jsonBody = JsonUtils.toJson(payload);

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + request.auth().apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

            if (request.auth().extraHeaders() != null) {
                request.auth().extraHeaders().forEach(reqBuilder::header);
            }

            HttpResponse<Stream<String>> response = httpClient.send(
                    reqBuilder.build(),
                    HttpResponse.BodyHandlers.ofLines()
            );

            try (Stream<String> body = response.body()) {
                if (response.statusCode() != 200) {
                    String bodyStr = body.collect(java.util.stream.Collectors.joining());
                    boolean overflow = isContextOverflow(response.statusCode(), bodyStr);
                    publisher.submit(new StreamEvent.StreamError(
                            "HTTP " + response.statusCode() + ": " + bodyStr,
                            response.statusCode() >= 500 || overflow,
                            overflow
                    ));
                    publisher.submit(new StreamEvent.StreamMessageEnd("error", 0, 0));
                    return;
                }

                parseSse(body, publisher, request.signal() != null ? request.signal() : new java.util.concurrent.atomic.AtomicBoolean(false));
            }

        } catch (Exception e) {
            publisher.submit(new StreamEvent.StreamError(e.getMessage(), true, false));
            publisher.submit(new StreamEvent.StreamMessageEnd("error", 0, 0));
        } finally {
            publisher.close();
        }
    }

    private Map<String, Object> buildPayload(StreamRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", request.model().id());
        payload.put("stream", true);
        payload.put("stream_options", Map.of("include_usage", true));

        List<Map<String, Object>> messages = new ArrayList<>();
        if (request.systemPrompt() != null && !request.systemPrompt().isEmpty()) {
            messages.add(Map.of("role", "system", "content", request.systemPrompt()));
        }
        messages.addAll(request.messages());
        payload.put("messages", messages);

        if (request.tools() != null && !request.tools().isEmpty()) {
            payload.put("tools", request.tools());
        }
        if (request.temperature() != null) {
            payload.put("temperature", request.temperature());
        }
        if (request.maxTokens() != null) {
            payload.put("max_tokens", request.maxTokens());
        }
        return payload;
    }

    private void parseSse(Stream<String> lines, SubmissionPublisher<StreamEvent> publisher,
                          java.util.concurrent.atomic.AtomicBoolean signal) {
        Map<Integer, Map<String, Object>> toolCallBuffers = new LinkedHashMap<>();

        try (lines) {
            for (var line : (Iterable<String>) lines::iterator) {
                if (signal.get()) break;
                if (!line.startsWith("data: ")) continue;
                String data = line.substring(6).trim();
                if ("[DONE]".equals(data)) break;

                try {
                    Map<String, Object> chunk = JsonUtils.parseJson(data);
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) chunk.get("choices");
                    if (choices == null || choices.isEmpty()) {
                        // Check for usage
                        @SuppressWarnings("unchecked")
                        Map<String, Object> usage = (Map<String, Object>) chunk.get("usage");
                        if (usage != null) {
                            int inputTokens = JsonUtils.toInt(usage.get("prompt_tokens"));
                            int outputTokens = JsonUtils.toInt(usage.get("completion_tokens"));
                            publisher.submit(new StreamEvent.StreamMessageEnd("stop", inputTokens, outputTokens));
                        }
                        continue;
                    }

                    Map<String, Object> choice = choices.get(0);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> delta = (Map<String, Object>) choice.get("delta");
                    if (delta == null) delta = Map.of();

                    String finishReason = (String) choice.get("finish_reason");

                    // Text content
                    String content = (String) delta.get("content");
                    if (content != null && !content.isEmpty()) {
                        publisher.submit(new StreamEvent.StreamTextDelta(content));
                    }

                    // Tool calls
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) delta.get("tool_calls");
                    if (toolCalls != null) {
                        for (var tc : toolCalls) {
                            int index = JsonUtils.toInt(tc.get("index"));
                            String id = (String) tc.get("id");
                            @SuppressWarnings("unchecked")
                            Map<String, Object> function = (Map<String, Object>) tc.get("function");

                            var buffer = toolCallBuffers.computeIfAbsent(index, i -> new LinkedHashMap<>());

                            if (id != null) {
                                buffer.put("id", id);
                                if (function != null && function.get("name") != null) {
                                    buffer.put("name", function.get("name"));
                                    publisher.submit(new StreamEvent.StreamToolCallStart(id, (String) function.get("name")));
                                }
                            }

                            if (function != null && function.get("arguments") != null) {
                                String argsDelta = (String) function.get("arguments");
                                buffer.merge("args_str", argsDelta, (a, b) -> (String) a + (String) b);
                                if (buffer.get("id") != null) {
                                    publisher.submit(new StreamEvent.StreamToolCallDelta(
                                            (String) buffer.get("id"), argsDelta));
                                }
                            }
                        }
                    }

                    // Finish reason
                    if (finishReason != null) {
                        // Emit tool call ends
                        for (var entry : toolCallBuffers.values()) {
                            String tcId = (String) entry.get("id");
                            String argsStr = (String) entry.getOrDefault("args_str", "{}");
                            Map<String, Object> args;
                            try {
                                args = JsonUtils.parseJson(argsStr);
                            } catch (Exception e) {
                                args = Map.of();
                            }
                            publisher.submit(new StreamEvent.StreamToolCallEnd(tcId, args));
                        }

                        // Check for usage in the same chunk
                        @SuppressWarnings("unchecked")
                        Map<String, Object> usage = (Map<String, Object>) chunk.get("usage");
                        int inputTokens = usage != null ? JsonUtils.toInt(usage.get("prompt_tokens")) : 0;
                        int outputTokens = usage != null ? JsonUtils.toInt(usage.get("completion_tokens")) : 0;
                        publisher.submit(new StreamEvent.StreamMessageEnd(finishReason, inputTokens, outputTokens));
                    }

                } catch (Exception e) {
                    log.debug("Failed to parse SSE line: {}", data, e);
                }
            }
        }
    }

    public static boolean isContextOverflow(int statusCode, String body) {
        if (statusCode == 400) {
            String lower = body.toLowerCase();
            return lower.contains("context_length_exceeded")
                    || lower.contains("maximum context length")
                    || lower.contains("too long")
                    || lower.contains("too many tokens");
        }
        return false;
    }
}
