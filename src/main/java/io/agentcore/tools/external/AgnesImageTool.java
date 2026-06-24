package io.agentcore.tools.external;

import io.agentcore.model.Content.TextContent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import io.agentcore.model.ToolResult;
import io.agentcore.tools.Tool;
import io.agentcore.tools.ToolContext;
import io.agentcore.tools.ToolDefinition;

/**
 * Agnes Image generation tool — text-to-image and image-to-image editing.
 *
 * <p>Mirrors Python {@code agent_core/tools/agnes_image_tool.py}.
 * Generates or edits images using Agnes-Image-2.0-Flash model via HTTP API.
 */
public class AgnesImageTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(AgnesImageTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int TIMEOUT = 120;

    private static final ToolDefinition DEF = new ToolDefinition(
            "generate_image",
            "使用 Agnes-Image-2.0-Flash 模型生成或编辑图片。"
                    + "支持文字生成图片和图片编辑（需提供输入图片URL）。"
                    + "返回生成的图片URL。",
            Map.of("type", "object", "properties", Map.of(
                    "prompt", Map.of("type", "string",
                            "description", "图片描述或编辑指令。描述你想要的图片内容、风格、场景。"),
                    "size", Map.of("type", "string",
                            "description", "输出图片尺寸，默认 1024x1024"),
                    "input_images", Map.of("type", "array",
                            "description", "输入图片URL列表（可选）。用于图片编辑或多图合成")
            ), "required", List.of("prompt")),
            "generate_image $ARGUMENTS — 生成或编辑图片，返回图片URL",
            List.of(
                    "生成图片后，必须在最终回复中包含图片的markdown语法 `![描述](URL)`，以便用户直接在对话中看到图片。",
                    "不要只描述图片内容而不展示图片链接。工具返回的图片URL必须原样保留在回复中。"
            ),
            (double) TIMEOUT
    );

    @Override
    public ToolDefinition definition() {
        return DEF;
    }

    @Override
    public ToolResult execute(String toolCallId, Map<String, Object> params, ToolContext ctx) throws Exception {
        String prompt = (String) params.getOrDefault("prompt", "");
        String size = (String) params.getOrDefault("size", "1024x1024");
        Object inputImagesObj = params.get("input_images");

        String apiKey = AgnesApiConfig.getApiKey();
        if (apiKey == null) {
            return ToolResult.error("config", "未设置 AGNES_API_KEY 环境变量");
        }

        // Build request body
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", "agnes-image-2.0-flash");
        body.put("prompt", prompt);
        body.put("size", size);

        // Handle input images for img2img
        @SuppressWarnings("unchecked")
        List<String> inputImages = inputImagesObj instanceof List<?> list
                ? list.stream().filter(o -> o instanceof String).map(o -> (String) o).toList()
                : List.of();

        if (!inputImages.isEmpty()) {
            body.set("tags", MAPPER.createArrayNode().add("img2img"));
            ObjectNode extraBody = MAPPER.createObjectNode();
            ArrayNode imageArray = MAPPER.createArrayNode();
            inputImages.forEach(imageArray::add);
            extraBody.set("image", imageArray);
            extraBody.put("response_format", "url");
            body.set("extra_body", extraBody);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(AgnesApiConfig.url("/images/generations")))
                .timeout(Duration.ofSeconds(TIMEOUT))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .build();

        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT))
                .build()) {

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                String errorText = response.body().length() > 500
                        ? response.body().substring(0, 500) : response.body();
                return ToolResult.error("api_error", "API 错误 (" + response.statusCode() + "): " + errorText);
            }

            JsonNode data = MAPPER.readTree(response.body());
            JsonNode results = data.get("data");
            if (results == null || !results.isArray() || results.isEmpty()) {
                return ToolResult.error("no_result", "未生成图片: " + response.body());
            }

            List<String> urls = new ArrayList<>();
            for (JsonNode item : results) {
                JsonNode urlNode = item.get("url");
                if (urlNode != null && !urlNode.isNull() && !urlNode.asText().isEmpty()) {
                    urls.add(urlNode.asText());
                }
            }

            if (urls.isEmpty()) {
                return ToolResult.error("no_url", "生成失败：未返回图片URL");
            }

            // Output image URLs wrapped in markdown
            StringBuilder md = new StringBuilder();
            for (int i = 0; i < urls.size(); i++) {
                if (i > 0) md.append("\n\n");
                md.append("![生成图片](").append(urls.get(i)).append(")");
            }

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("urls", urls);
            details.put("size", size);
            JsonNode usage = data.get("usage");
            if (usage != null) {
                details.put("usage", MAPPER.convertValue(usage, Map.class));
            }

            return new ToolResult(
                    List.of(new TextContent(md.toString())),
                    details, null);
        } catch (Exception e) {
            log.debug("Agnes image generation failed", e);
            return ToolResult.error("image_failed", e.getMessage());
        }
    }
}
