package io.agentcore.providers.anthropic;

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
 * Anthropic provider — uses java.net.http.HttpClient for SSE streaming.
 */
public class AnthropicProvider implements ModelProvider {
    private static final Logger log = LoggerFactory.getLogger(AnthropicProvider.class);
    private static final String API_VERSION = "2023-06-01";

    private static final Map<String, Integer> THINKING_BUDGETS = Map.of(
            "minimal", 128, "low", 512, "medium", 1024, "high", 2048, "xhigh", 4096
    );

    private final String baseUrl;
    private final String providerName;
    private final List<Model> models;
    private final double timeout;
    private final HttpClient httpClient;

    public AnthropicProvider() {
        this("https://api.anthropic.com/v1", "anthropic", defaultModels(), 120.0, null);
    }

    public AnthropicProvider(String baseUrl, String providerName, List<Model> models,
                             double timeout, HttpClient httpClient) {
        this.baseUrl = baseUrl;
        this.providerName = providerName;
        this.models = models != null ? models : defaultModels();
        this.timeout = timeout;
        this.httpClient = httpClient != null ? httpClient : HttpClient.newHttpClient();
    }

    private static List<Model> defaultModels() {
        return List.of(
                new Model("anthropic", "claude-sonnet-4-6", 200000, 8192, true, true),
                new Model("anthropic", "claude-opus-4-7", 200000, 16384, true, true)
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

    /**
     * Anthropic uses a different tool format:
     * {"name": "...", "description": "...", "input_schema": {...}}
     */
    @Override
    public List<Map<String, Object>> toolsToProviderFormat(List<io.agentcore.tools.base.ToolDefinition> tools) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (var tool : tools) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("name", tool.name());
            t.put("description", tool.description());
            t.put("input_schema", tool.parameters());
            result.add(t);
        }
        return result;
    }

    @Override
    public Flow.Publisher<StreamEvent> stream(StreamRequest request) {
        return subscriber -> {
            Thread.ofVirtual().name("anthropic-stream").start(() -> {
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
                    .uri(URI.create(baseUrl + "/messages"))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", request.auth().apiKey())
                    .header("anthropic-version", API_VERSION)
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

                parseSse(body, publisher,
                        request.signal() != null ? request.signal() : new java.util.concurrent.atomic.AtomicBoolean(false));
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
        payload.put("max_tokens", request.maxTokens() != null ? request.maxTokens() : 4096);
        payload.put("stream", true);

        // System prompt
        if (request.systemPrompt() != null && !request.systemPrompt().isEmpty()) {
            payload.put("system", request.systemPrompt());
        }

        // Messages
        payload.put("messages", request.messages());

        // Tools
        if (request.tools() != null && !request.tools().isEmpty()) {
            payload.put("tools", request.tools());
        }

        // Thinking
        String thinking = request.thinkingLevel();
        if (thinking != null && !"off".equals(thinking)) {
            Integer budget = THINKING_BUDGETS.get(thinking);
            if (budget != null) {
                payload.put("thinking", Map.of("type", "enabled", "budget_tokens", budget));
            }
        }

        if (request.temperature() != null) {
            payload.put("temperature", request.temperature());
        }

        return payload;
    }

    private void parseSse(Stream<String> lines, SubmissionPublisher<StreamEvent> publisher,
                          java.util.concurrent.atomic.AtomicBoolean signal) {
        List<Map<String, Object>> toolCallBuffers = new ArrayList<>();
        int inputTokens = 0;
        int outputTokens = 0;
        String stopReason = "stop";

        try (lines) {
            String currentEvent = null;
            for (var line : (Iterable<String>) lines::iterator) {
                if (signal.get()) break;
                if (line.startsWith("event: ")) {
                    currentEvent = line.substring(7).trim();
                    continue;
                }
                if (!line.startsWith("data: ")) continue;
                String data = line.substring(6).trim();
                if (data.isEmpty()) continue;

                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> event = JsonUtils.parseJson(data);
                    String type = (String) event.get("type");

                    if ("message_start".equals(type)) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> message = (Map<String, Object>) event.get("message");
                        if (message != null) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> usage = (Map<String, Object>) message.get("usage");
                            if (usage != null) inputTokens = JsonUtils.toInt(usage.get("input_tokens"));
                        }

                    } else if ("content_block_start".equals(type)) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> block = (Map<String, Object>) event.get("content_block");
                        if (block != null && "tool_use".equals(block.get("type"))) {
                            Map<String, Object> buffer = new LinkedHashMap<>();
                            buffer.put("id", block.get("id"));
                            buffer.put("name", block.get("name"));
                            buffer.put("args_str", new StringBuilder());
                            toolCallBuffers.add(buffer);
                            publisher.submit(new StreamEvent.StreamToolCallStart(
                                    (String) block.get("id"), (String) block.get("name")));
                        }

                    } else if ("content_block_delta".equals(type)) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> delta = (Map<String, Object>) event.get("delta");
                        if (delta != null) {
                            String deltaType = (String) delta.get("type");
                            if ("text_delta".equals(deltaType)) {
                                publisher.submit(new StreamEvent.StreamTextDelta((String) delta.get("text")));
                            } else if ("thinking_delta".equals(deltaType)) {
                                publisher.submit(new StreamEvent.StreamThinkingDelta((String) delta.get("thinking")));
                            } else if ("input_json_delta".equals(deltaType)) {
                                int index = JsonUtils.toInt(event.get("index"));
                                if (index >= 0 && index < toolCallBuffers.size()) {
                                    @SuppressWarnings("unchecked")
                                    StringBuilder sb = (StringBuilder) toolCallBuffers.get(index).get("args_str");
                                    sb.append(delta.get("partial_json"));
                                }
                            }
                        }

                    } else if ("message_delta".equals(type)) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> delta = (Map<String, Object>) event.get("delta");
                        if (delta != null && delta.get("stop_reason") != null) {
                            stopReason = (String) delta.get("stop_reason");
                        }
                        @SuppressWarnings("unchecked")
                        Map<String, Object> usage = (Map<String, Object>) event.get("usage");
                        if (usage != null) outputTokens = JsonUtils.toInt(usage.get("output_tokens"));

                    } else if ("message_stop".equals(type)) {
                        for (var buffer : toolCallBuffers) {
                            String argsStr = ((StringBuilder) buffer.get("args_str")).toString();
                            Map<String, Object> args;
                            try {
                                args = JsonUtils.parseJson(argsStr);
                            } catch (Exception e) {
                                args = Map.of();
                            }
                            publisher.submit(new StreamEvent.StreamToolCallEnd((String) buffer.get("id"), args));
                        }
                        publisher.submit(new StreamEvent.StreamMessageEnd(stopReason, inputTokens, outputTokens));
                    }

                } catch (Exception e) {
                    log.debug("Failed to parse Anthropic SSE: {}", data, e);
                }
            }
        }
    }

    /**
     * Detect context overflow errors from Anthropic API.
     */
    public static boolean isContextOverflow(int statusCode, String body) {
        if (statusCode == 400) {
            String lower = body.toLowerCase();
            return lower.contains("prompt is too long")
                    || lower.contains("max_tokens")
                    || lower.contains("context limit")
                    || lower.contains("too many tokens")
                    || lower.contains("input too long");
        }
        return false;
    }
}
