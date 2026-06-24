package io.agentcore.llm.anthropic;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentcore.model.Content;
import io.agentcore.model.Message;
import io.agentcore.llm.ProviderUtils;
import io.agentcore.llm.ModelInfo;
import io.agentcore.llm.ModelProvider;
import io.agentcore.llm.ProviderAuth;
import io.agentcore.llm.StreamEvent;
import io.agentcore.llm.StreamEvent.*;

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
import java.util.stream.Collectors;

/**
 * Anthropic provider with thinking budgets and content-block streaming.
 *
 * <p>Mirrors Python {@code agent_core/providers/anthropic_provider.py}.
 */
public class AnthropicProvider implements ModelProvider {

    private static final ObjectMapper MAPPER = ProviderUtils.mapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private static final Map<String, Integer> THINKING_BUDGETS = Map.of(
            "minimal", 256,
            "low", 1024,
            "medium", 4096,
            "high", 10000,
            "xhigh", 20000
    );

    private final String providerName;
    private final String baseUrl;
    private final List<ModelInfo> models;
    private final Duration timeout;
    private final HttpClient httpClient;

    public AnthropicProvider(String baseUrl, String providerName, List<ModelInfo> models, Duration timeout) {
        this.providerName = providerName;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.models = models != null ? List.copyOf(models) : defaultModels(providerName);
        this.timeout = timeout != null ? timeout : Duration.ofSeconds(120);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .build();
    }

    public AnthropicProvider() {
        this("https://api.anthropic.com/v1", "anthropic", null, null);
    }

    @Override
    public String name() {
        return providerName;
    }

    @Override
    public List<ModelInfo> listModels() {
        return models;
    }

    /**
     * Create a message converter that produces Anthropic-native messages.
     */
    public AnthropicMessageConverter createMessageConverter() {
        return createMessageConverter(4000);
    }

    public AnthropicMessageConverter createMessageConverter(int toolResultMaxChars) {
        return new AnthropicMessageConverter(toolResultMaxChars);
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

        // Detect if messages are already in Anthropic format
        List<Map<String, Object>> anthropicMsgs;
        String system;
        if (isAnthropicFormat(messages)) {
            var result = mergeAnthropicMessages(messages, systemPrompt);
            anthropicMsgs = result.messages;
            system = result.system;
        } else {
            var result = convertMessages(messages, systemPrompt);
            anthropicMsgs = result.messages;
            system = result.system;
        }

        Map<String, Object> payload = buildPayload(model, anthropicMsgs, system, tools,
                thinkingLevel, temperature, maxTokens);

        LinkedBlockingQueue<StreamEvent> queue = new LinkedBlockingQueue<>();
        AtomicBoolean done = new AtomicBoolean(false);

        Thread.ofVirtual().name("anthropic-sse-" + System.nanoTime()).start(() -> {
            try {
                doStreamRequest(payload, auth, abortSignal, queue, done);
            } catch (Exception e) {
                queue.offer(new StreamError("Stream failed: " + e.getMessage(), true, false));
            } finally {
                done.set(true);
                queue.offer(null);
            }
        });

        return new ProviderUtils.QueueBackedIterator<>(queue, done);
    }

    // ── Payload building ─────────────────────────────────────

    private Map<String, Object> buildPayload(
            ModelInfo model,
            List<Map<String, Object>> messages,
            String system,
            List<Map<String, Object>> tools,
            String thinkingLevel,
            Double temperature,
            Integer maxTokens) {

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model.id());
        payload.put("messages", messages);
        payload.put("max_tokens", maxTokens != null ? maxTokens : model.maxOutputTokens());
        payload.put("stream", true);

        if (system != null && !system.isEmpty()) {
            payload.put("system", system);
        }
        if (tools != null && !tools.isEmpty()) {
            payload.put("tools", tools.stream().map(this::convertToolDef).toList());
        }
        if (temperature != null) {
            payload.put("temperature", temperature);
        }
        if (thinkingLevel != null && !"off".equals(thinkingLevel) && model.supportsReasoning()) {
            int budget = THINKING_BUDGETS.getOrDefault(thinkingLevel, 1024);
            payload.put("thinking", Map.of("type", "enabled", "budget_tokens", budget));
        }
        return payload;
    }

    private Map<String, Object> convertToolDef(Map<String, Object> tool) {
        @SuppressWarnings("unchecked")
        Map<String, Object> fn = (Map<String, Object>) tool.getOrDefault("function", tool);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", fn.getOrDefault("name", tool.getOrDefault("name", "")));
        out.put("description", fn.getOrDefault("description", tool.getOrDefault("description", "")));
        out.put("input_schema", fn.getOrDefault("parameters", tool.getOrDefault("parameters", Map.of())));
        return out;
    }

    // ── HTTP + SSE parsing ───────────────────────────────────

    private void doStreamRequest(
            Map<String, Object> payload,
            ProviderAuth auth,
            AtomicBoolean abortSignal,
            LinkedBlockingQueue<StreamEvent> queue,
            AtomicBoolean done) throws Exception {

        String url = baseUrl + "/messages";
        String jsonBody = MAPPER.writeValueAsString(payload);

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .header("x-api-key", auth.apiKey())
                .header("anthropic-version", "2023-06-01")
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
                boolean retryable = Set.of(429, 500, 502, 503, 504).contains(response.statusCode());
                boolean overflow = ProviderUtils.isContextOverflow(bodyText);
                queue.offer(new StreamError("HTTP " + response.statusCode() + ": " + bodyText, retryable, overflow));
            }
            return;
        }

        parseSse(response.body(), abortSignal, queue);
    }

    private void parseSse(
            java.io.InputStream inputStream,
            AtomicBoolean abortSignal,
            LinkedBlockingQueue<StreamEvent> queue) throws Exception {

        Map<String, Map<String, Object>> toolBuffers = new LinkedHashMap<>();
        Set<String> startedTools = new HashSet<>();
        Map<Integer, String> indexToTool = new LinkedHashMap<>();
        // Buffer message_end info to emit AFTER tool call ends (correct event ordering)
        String[] pendingEnd = new String[2]; // [stopReason, outputTokens]

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

                String eventType = (String) event.get("type");
                if (eventType == null) continue;

                switch (eventType) {
                    case "content_block_start" -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> block = (Map<String, Object>) event.get("content_block");
                        int idx = ProviderUtils.toInt(event.get("index"));
                        if (block != null && "tool_use".equals(block.get("type"))) {
                            String tid = (String) block.getOrDefault("id", "");
                            String name = (String) block.getOrDefault("name", "");
                            Map<String, Object> buf = new LinkedHashMap<>();
                            buf.put("id", tid);
                            buf.put("name", name);
                            buf.put("args", "");
                            toolBuffers.put(tid, buf);
                            indexToTool.put(idx, tid);
                            if (!startedTools.contains(tid)) {
                                startedTools.add(tid);
                                queue.offer(new StreamToolCallStart(tid, name));
                            }
                        }
                    }
                    case "content_block_delta" -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> delta = (Map<String, Object>) event.get("delta");
                        if (delta != null) {
                            String dType = (String) delta.get("type");
                            if ("text_delta".equals(dType)) {
                                queue.offer(new StreamTextDelta((String) delta.getOrDefault("text", "")));
                            } else if ("thinking_delta".equals(dType)) {
                                queue.offer(new StreamThinkingDelta((String) delta.getOrDefault("thinking", "")));
                            } else if ("input_json_delta".equals(dType)) {
                                int idx = ProviderUtils.toInt(event.get("index"));
                                String tid = indexToTool.get(idx);
                                if (tid != null && toolBuffers.containsKey(tid)) {
                                    String partial = (String) delta.getOrDefault("partial_json", "");
                                    Map<String, Object> buf = toolBuffers.get(tid);
                                    buf.put("args", (String) buf.get("args") + partial);
                                }
                            }
                        }
                    }
                    case "message_delta" -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> delta = (Map<String, Object>) event.get("delta");
                        @SuppressWarnings("unchecked")
                        Map<String, Object> usage = (Map<String, Object>) event.get("usage");
                        if (usage == null) usage = Map.of();
                        // Buffer for emission after tool call ends in message_stop
                        pendingEnd[0] = delta != null
                                ? (String) delta.getOrDefault("stop_reason", "stop")
                                : "stop";
                        pendingEnd[1] = String.valueOf(ProviderUtils.toInt(usage.get("output_tokens")));
                    }
                    case "message_stop" -> {
                        // Emit tool call ends first, then message end (correct ordering)
                        for (Map<String, Object> buf : toolBuffers.values()) {
                            String tid = (String) buf.get("id");
                            String name = (String) buf.get("name");
                            if (tid != null && name != null) {
                                Map<String, Object> args = ProviderUtils.parseJsonMap((String) buf.get("args"));
                                queue.offer(new StreamToolCallEnd(tid, args));
                            }
                        }
                        if (pendingEnd[0] != null) {
                            queue.offer(new StreamMessageEnd(
                                    pendingEnd[0],
                                    0,
                                    Integer.parseInt(pendingEnd[1])));
                        }
                    }
                }
            }
        }
    }

    // ── Anthropic message conversion ─────────────────────────

    private record ConvertedMessages(List<Map<String, Object>> messages, String system) {}

    private static boolean isAnthropicFormat(List<Map<String, Object>> messages) {
        for (Map<String, Object> m : messages) {
            String role = (String) m.get("role");
            if ("user".equals(role) || "assistant".equals(role)) {
                Object content = m.get("content");
                if (content instanceof List<?> list && !list.isEmpty()) {
                    Object first = list.getFirst();
                    if (first instanceof Map<?, ?> map) {
                        Object type = map.get("type");
                        if (type != null && Set.of("text", "image", "tool_use", "tool_result").contains(type.toString())) {
                            return true;
                        }
                    }
                }
                return false;
            }
        }
        return false;
    }

    private static ConvertedMessages convertMessages(List<Map<String, Object>> messages, String systemPrompt) {
        String system = systemPrompt;
        List<Map<String, Object>> out = new ArrayList<>();

        for (Map<String, Object> msg : messages) {
            String role = (String) msg.get("role");
            if ("system".equals(role)) {
                String text = extractText(msg.get("content"));
                if (!text.isEmpty()) system = text;
                continue;
            }
            if ("user".equals(role)) {
                out.add(Map.of("role", "user", "content", toAnthropicContent(msg.get("content"))));
            } else if ("assistant".equals(role)) {
                out.add(Map.of("role", "assistant", "content", assistantToAnthropic(msg)));
            } else if ("tool".equals(role)) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("type", "tool_result");
                result.put("tool_use_id", msg.getOrDefault("tool_call_id", ""));
                result.put("content", extractText(msg.get("content")));
                if (!out.isEmpty() && "user".equals(out.getLast().get("role"))) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> content = (List<Map<String, Object>>) out.getLast().get("content");
                    content.add(result);
                } else {
                    out.add(Map.of("role", "user", "content", new ArrayList<>(List.of(result))));
                }
            }
        }

        return new ConvertedMessages(mergeConsecutive(out), system);
    }

    private static ConvertedMessages mergeAnthropicMessages(List<Map<String, Object>> messages, String systemPrompt) {
        String system = systemPrompt;
        List<Map<String, Object>> out = new ArrayList<>();

        for (Map<String, Object> msg : messages) {
            String role = (String) msg.get("role");
            if ("system".equals(role)) {
                String text = extractText(msg.get("content"));
                if (!text.isEmpty()) system = text;
                continue;
            }
            if ("user".equals(role) || "assistant".equals(role)) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> content = (List<Map<String, Object>>) msg.getOrDefault("content", List.of());
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("role", role);
                m.put("content", new ArrayList<>(content));
                out.add(m);
            }
        }

        return new ConvertedMessages(mergeConsecutive(out), system);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mergeConsecutive(List<Map<String, Object>> messages) {
        List<Map<String, Object>> merged = new ArrayList<>();
        for (Map<String, Object> m : messages) {
            if (!merged.isEmpty() && merged.getLast().get("role").equals(m.get("role"))) {
                List<Map<String, Object>> prevContent = (List<Map<String, Object>>) merged.getLast().get("content");
                List<Map<String, Object>> curContent = (List<Map<String, Object>>) m.get("content");
                prevContent.addAll(curContent);
            } else {
                merged.add(m);
            }
        }
        return merged;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> toAnthropicContent(Object content) {
        if (content instanceof String s) {
            return new ArrayList<>(List.of(Map.of("type", "text", "text", s)));
        }
        if (content instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object c : list) {
                if (c instanceof Map<?, ?> map) {
                    Object type = map.get("type");
                    if ("text".equals(type)) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("type", "text");
                        Object textVal = ((Map<String, Object>) map).getOrDefault("text", "");
                        m.put("text", textVal);
                        out.add(m);
                    } else if ("image_url".equals(type)) {
                        Map<String, Object> imageUrlMap = (Map<String, Object>) ((Map<String, Object>) map).getOrDefault("image_url", Map.of());
                        String url = (String) imageUrlMap.getOrDefault("url", "");
                        if (url.startsWith("data:")) {
                            String[] parts = splitDataUrl(url);
                            Map<String, Object> img = new LinkedHashMap<>();
                            img.put("type", "image");
                            img.put("source", Map.of(
                                    "type", "base64",
                                    "media_type", parts[0],
                                    "data", parts[1]
                            ));
                            out.add(img);
                        }
                    } else {
                        out.add((Map<String, Object>) map);
                    }
                } else if (c instanceof String s) {
                    out.add(Map.of("type", "text", "text", s));
                }
            }
            if (out.isEmpty()) {
                out.add(Map.of("type", "text", "text", ""));
            }
            return out;
        }
        return new ArrayList<>(List.of(Map.of("type", "text", "text", String.valueOf(content))));
    }

    private static List<Map<String, Object>> assistantToAnthropic(Map<String, Object> msg) {
        List<Map<String, Object>> out = new ArrayList<>();
        String text = extractText(msg.get("content"));
        if (!text.isEmpty()) {
            out.add(new LinkedHashMap<>(Map.of("type", "text", "text", text)));
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) msg.get("tool_calls");
        if (toolCalls != null) {
            for (Map<String, Object> tc : toolCalls) {
                @SuppressWarnings("unchecked")
                Map<String, Object> fn = (Map<String, Object>) tc.getOrDefault("function", Map.of());
                Map<String, Object> args = ProviderUtils.parseJsonMap((String) fn.getOrDefault("arguments", "{}"));
                Map<String, Object> tu = new LinkedHashMap<>();
                tu.put("type", "tool_use");
                tu.put("id", tc.getOrDefault("id", ""));
                tu.put("name", fn.getOrDefault("name", ""));
                tu.put("input", args);
                out.add(tu);
            }
        }
        if (out.isEmpty()) {
            out.add(Map.of("type", "text", "text", ""));
        }
        return out;
    }

    private static String extractText(Object content) {
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

    private static String[] splitDataUrl(String url) {
        // data:image/png;base64,ABC...
        String afterPrefix = url.substring(5); // strip "data:"
        int semiIdx = afterPrefix.indexOf(';');
        if (semiIdx < 0) throw new IllegalArgumentException("Invalid data URL: missing encoding");
        int commaIdx = afterPrefix.indexOf(',', semiIdx);
        if (commaIdx < 0) throw new IllegalArgumentException("Invalid data URL: missing data");
        String mediaType = afterPrefix.substring(0, semiIdx);
        String b64 = afterPrefix.substring(commaIdx + 1);
        return new String[]{mediaType, b64};
    }

    static List<ModelInfo> defaultModels(String providerName) {
        return List.of(
                new ModelInfo(providerName, "claude-sonnet-4-6", 200_000, 8192, true, true),
                new ModelInfo(providerName, "claude-opus-4-7", 200_000, 16384, true, true)
        );
    }

    // ── Anthropic-native message converter ───────────────────

    /**
     * Converts domain {@link Message} objects to Anthropic-native format.
     */
    public static class AnthropicMessageConverter {
        private final int toolResultMaxChars;

        public AnthropicMessageConverter(int toolResultMaxChars) {
            this.toolResultMaxChars = toolResultMaxChars;
        }

        public List<Map<String, Object>> convert(List<Message> messages) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Message m : messages) {
                switch (m) {
                    case Message.UserMessage um -> {
                        List<Map<String, Object>> blocks = new ArrayList<>();
                        for (Content c : um.content()) {
                            if (c instanceof Content.TextContent tc) {
                                blocks.add(new LinkedHashMap<>(Map.of("type", "text", "text", tc.text())));
                            } else if (c instanceof Content.ImageContent ic) {
                                Map<String, Object> img = new LinkedHashMap<>();
                                img.put("type", "image");
                                img.put("source", Map.of(
                                        "type", "base64",
                                        "media_type", ic.mimeType(),
                                        "data", ic.data()
                                ));
                                blocks.add(img);
                            }
                        }
                        if (blocks.isEmpty()) {
                            blocks.add(Map.of("type", "text", "text", ""));
                        }
                        Map<String, Object> msg = new LinkedHashMap<>();
                        msg.put("role", "user");
                        msg.put("content", blocks);
                        out.add(msg);
                    }
                    case Message.AssistantMessage am -> {
                        List<Map<String, Object>> blocks = new ArrayList<>();
                        String text = am.content().stream()
                                .filter(c -> c instanceof Content.TextContent)
                                .map(c -> ((Content.TextContent) c).text())
                                .collect(Collectors.joining());
                        if (!text.isEmpty()) {
                            blocks.add(new LinkedHashMap<>(Map.of("type", "text", "text", text)));
                        }
                        for (Content tc : am.content()) {
                            if (tc instanceof Content.ToolCallContent tcc) {
                                Map<String, Object> tu = new LinkedHashMap<>();
                                tu.put("type", "tool_use");
                                tu.put("id", tcc.id());
                                tu.put("name", tcc.name());
                                tu.put("input", tcc.arguments());
                                blocks.add(tu);
                            }
                        }
                        if (blocks.isEmpty()) {
                            blocks.add(Map.of("type", "text", "text", ""));
                        }
                        Map<String, Object> msg = new LinkedHashMap<>();
                        msg.put("role", "assistant");
                        msg.put("content", blocks);
                        out.add(msg);
                    }
                    case Message.ToolResultMessage trm -> {
                        String text = trm.content().stream()
                                .filter(c -> c instanceof Content.TextContent)
                                .map(c -> ((Content.TextContent) c).text())
                                .collect(Collectors.joining());
                        if (text.length() > toolResultMaxChars) {
                            text = text.substring(0, toolResultMaxChars)
                                    + "\n...[truncated, " + text.length() + " chars total]";
                        }
                        Map<String, Object> resultBlock = new LinkedHashMap<>();
                        resultBlock.put("type", "tool_result");
                        resultBlock.put("tool_use_id", trm.toolCallId());
                        resultBlock.put("content", text);

                        if (!out.isEmpty() && "user".equals(out.getLast().get("role"))) {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> content = (List<Map<String, Object>>) out.getLast().get("content");
                            content.add(resultBlock);
                        } else {
                            Map<String, Object> msg = new LinkedHashMap<>();
                            msg.put("role", "user");
                            msg.put("content", new ArrayList<>(List.of(resultBlock)));
                            out.add(msg);
                        }
                    }
                    case Message.CustomMessage cm -> {
                        if ("compaction_summary".equals(cm.customType())) {
                            String text = cm.content() != null ? cm.content().asText() : "";
                            Map<String, Object> msg = new LinkedHashMap<>();
                            msg.put("role", "user");
                            msg.put("content", new ArrayList<>(List.of(
                                    Map.of("type", "text", "text", "[Earlier conversation summary]\n" + text)
                            )));
                            out.add(msg);
                        }
                    }
                }
            }
            return out;
        }
    }
}
