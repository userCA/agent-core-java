package io.agentcore.tools.external;

import io.agentcore.model.Content.TextContent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import io.agentcore.model.ToolResult;
import io.agentcore.tools.Tool;
import io.agentcore.tools.ToolContext;
import io.agentcore.tools.ToolDefinition;

/**
 * Agnes Video generation tool — text-to-video, image-to-video, multi-image, keyframes.
 *
 * <p>Mirrors Python {@code agent_core/tools/agnes_video_tool.py}.
 * Generates videos using Agnes-Video-V2.0 with async polling for completion.
 */
public class AgnesVideoTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(AgnesVideoTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int REQUEST_TIMEOUT = 30;
    private static final int QUICK_POLL_SECONDS = 30;
    private static final int QUICK_POLL_INTERVAL = 6;

    private static final Set<Integer> VALID_FRAMES = Set.of(
            81, 121, 161, 201, 241, 281, 321, 361, 401, 441
    );

    private static final ToolDefinition DEF = new ToolDefinition(
            "generate_video",
            "使用 Agnes-Video-V2.0 模型生成视频。"
                    + "支持文字转视频、图片转视频、多图视频、关键帧动画。"
                    + "视频生成需要等待，请耐心。",
            Map.of("type", "object", "properties", Map.of(
                    "prompt", Map.of("type", "string",
                            "description", "视频描述。文字转视频：描述主体、动作、场景、镜头、灯光、风格。"),
                    "image", Map.of("type", "string",
                            "description", "输入图片URL（可选）。用于图片转视频"),
                    "images", Map.of("type", "array",
                            "description", "多张输入图片URL列表（可选）。用于多图视频或关键帧动画"),
                    "mode", Map.of("type", "string",
                            "description", "设为 keyframes 进行关键帧动画（需配合 images 参数）"),
                    "width", Map.of("type", "integer",
                            "description", "视频宽度，默认 1152"),
                    "height", Map.of("type", "integer",
                            "description", "视频高度，默认 768"),
                    "num_frames", Map.of("type", "integer",
                            "description", "帧数，必须为 8n+1 格式且 ≤441"),
                    "frame_rate", Map.of("type", "integer",
                            "description", "帧率，默认 24")
            ), "required", List.of("prompt")),
            "当用户要求生成视频、制作视频时，必须调用 generate_video 工具。只调用一次，等待结果。",
            List.of(
                    "用户说'生成视频'时必须调用 generate_video，不要只描述而不调用。",
                    "只调用一次！返回结果后不要再调用第二次。",
                    "如果返回了视频URL，在回复中展示给用户。",
                    "如果返回了 task_id（视频仍在生成中），告诉用户'视频正在后台生成，预计2-5分钟'。"
            ),
            (double) (QUICK_POLL_SECONDS + 10)
    );

    @Override
    public ToolDefinition definition() {
        return DEF;
    }

    @Override
    public ToolResult execute(String toolCallId, Map<String, Object> params, ToolContext ctx) throws Exception {
        String prompt = (String) params.getOrDefault("prompt", "");
        String image = (String) params.get("image");
        String mode = (String) params.get("mode");
        Object wObj = params.get("width");
        int width = wObj instanceof Number n ? n.intValue() : 1152;
        Object hObj = params.get("height");
        int height = hObj instanceof Number n ? n.intValue() : 768;
        Object nfObj = params.get("num_frames");
        int numFrames = nfObj instanceof Number n ? n.intValue() : 121;
        Object frObj = params.get("frame_rate");
        int frameRate = frObj instanceof Number n ? n.intValue() : 24;

        String apiKey = AgnesApiConfig.getApiKey();
        if (apiKey == null) {
            return new ToolResult("错误：未设置 AGNES_API_KEY 环境变量");
        }

        // Validate keyframes mode
        if ("keyframes".equals(mode) && params.get("images") == null && image == null) {
            return new ToolResult("错误：关键帧模式（keyframes）必须提供 images 参数（至少2张图片URL）。");
        }

        // Validate num_frames
        numFrames = validateFrames(numFrames);

        // Build request body
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", "agnes-video-v2.0");
        body.put("prompt", prompt);
        body.put("width", width);
        body.put("height", height);
        body.put("num_frames", numFrames);
        body.put("frame_rate", frameRate);
        if (image != null) body.put("image", image);

        @SuppressWarnings("unchecked")
        List<Object> imagesRaw = params.get("images") instanceof List<?> l ? (List<Object>) l : null;
        if (imagesRaw != null || mode != null) {
            ObjectNode extra = MAPPER.createObjectNode();
            if (imagesRaw != null) {
                var arr = MAPPER.createArrayNode();
                for (Object o : imagesRaw) arr.add(String.valueOf(o));
                extra.set("image", arr);
            }
            if (mode != null) extra.put("mode", mode);
            body.set("extra_body", extra);
        }

        // Submit video task
        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(REQUEST_TIMEOUT)).build()) {

            HttpRequest submitReq = HttpRequest.newBuilder()
                    .uri(URI.create(AgnesApiConfig.url("/videos")))
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                    .build();

            HttpResponse<String> submitResp = client.send(submitReq, HttpResponse.BodyHandlers.ofString());
            if (submitResp.statusCode() >= 400) {
                String errorText = submitResp.body().length() > 500
                        ? submitResp.body().substring(0, 500) : submitResp.body();
                return new ToolResult("API 错误 (" + submitResp.statusCode() + "): " + errorText);
            }

            JsonNode data = MAPPER.readTree(submitResp.body());
            String taskId = data.has("id") ? data.get("id").asText("") : "";
            if (taskId.isEmpty()) {
                return new ToolResult("创建视频任务失败: " + submitResp.body());
            }

            // Quick poll
            long startTime = System.currentTimeMillis();
            String lastStatus = "";

            while (true) {
                long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                if (elapsed > QUICK_POLL_SECONDS) {
                    return new ToolResult(
                            "视频任务已创建，正在后台生成中。任务ID: " + taskId
                                    + "，当前状态: " + (lastStatus.isEmpty() ? "queued" : lastStatus)
                                    + "。通常需要2-5分钟，请告诉用户稍后询问进度。不要重复调用。");
                }

                Thread.sleep(QUICK_POLL_INTERVAL * 1000L);

                HttpRequest pollReq = HttpRequest.newBuilder()
                        .uri(URI.create(AgnesApiConfig.url("/videos/" + taskId)))
                        .timeout(Duration.ofSeconds(REQUEST_TIMEOUT))
                        .header("Authorization", "Bearer " + apiKey)
                        .GET()
                        .build();

                HttpResponse<String> pollResp = client.send(pollReq, HttpResponse.BodyHandlers.ofString());
                if (pollResp.statusCode() >= 400) continue;

                JsonNode pollData = MAPPER.readTree(pollResp.body());
                String status = pollData.has("status") ? pollData.get("status").asText("") : "";
                lastStatus = status;

                if ("completed".equals(status)) {
                    String videoUrl = "";
                    if (pollData.has("remixed_from_video_id") && !pollData.get("remixed_from_video_id").isNull()) {
                        videoUrl = pollData.get("remixed_from_video_id").asText("");
                    }
                    if (videoUrl.isEmpty() && pollData.has("video_url")) {
                        videoUrl = pollData.get("video_url").asText("");
                    }
                    if (videoUrl.isEmpty()) {
                        return new ToolResult("视频生成完成但未返回视频URL。任务ID: " + taskId);
                    }

                    String seconds = pollData.has("seconds") ? pollData.get("seconds").asText("?") : "?";
                    String sizeStr = pollData.has("size") ? pollData.get("size").asText("?") : "?";

                    Map<String, Object> details = new LinkedHashMap<>();
                    details.put("video_url", videoUrl);
                    details.put("task_id", taskId);
                    details.put("size", sizeStr);
                    details.put("seconds", seconds);

                    return new ToolResult(
                            List.of(new TextContent("视频已生成！\n**视频URL**: " + videoUrl
                                    + "\n**分辨率**: " + sizeStr + " | **时长**: " + seconds + "s")),
                            details, null);
                } else if ("failed".equals(status)) {
                    String error = pollData.has("error") ? pollData.get("error").asText("") : "unknown";
                    return new ToolResult("视频生成失败。任务ID: " + taskId + "，错误: " + error);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ToolResult("视频生成被中断");
        } catch (Exception e) {
            log.debug("Agnes video generation failed", e);
            return new ToolResult("生成失败: " + e.getMessage());
        }
    }

    public static int validateFrames(int numFrames) {
        if (numFrames > 441) numFrames = 121;
        if (!VALID_FRAMES.contains(numFrames)) {
            int[] sorted = VALID_FRAMES.stream().mapToInt(Integer::intValue).sorted().toArray();
            for (int f : sorted) {
                if (f >= numFrames) return f;
            }
            return 121;
        }
        return numFrames;
    }
}
