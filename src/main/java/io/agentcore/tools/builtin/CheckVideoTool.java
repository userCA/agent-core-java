package io.agentcore.tools.builtin;

import io.agentcore.model.Content.TextContent;
import io.agentcore.tools.external.AgnesApiConfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
import io.agentcore.model.ToolResult;
import io.agentcore.tools.Tool;
import io.agentcore.tools.ToolContext;
import io.agentcore.tools.ToolDefinition;

/**
 * Query the status of a previously submitted video generation task.
 *
 * <p>Mirrors Python {@code agent_core/tools/agnes_video_tool.py} CheckVideoTool.
 */
public class CheckVideoTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(CheckVideoTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int REQUEST_TIMEOUT = 30;

    private static final ToolDefinition DEF = new ToolDefinition(
            "check_video_status",
            "查询视频生成任务的进度。传入 task_id，返回当前状态和视频URL（如已完成）。",
            Map.of("type", "object", "properties", Map.of(
                    "task_id", Map.of("type", "string",
                            "description", "视频任务ID，由 generate_video 返回")
            ), "required", List.of("task_id")),
            "check_video_status $ARGUMENTS — 查询视频任务进度",
            List.of(
                    "当用户问'视频好了吗'、'检查进度'时，调用此工具查询。",
                    "只调用一次。如果视频已完成，把URL展示给用户。"
            ),
            15.0
    );

    @Override
    public ToolDefinition definition() {
        return DEF;
    }

    @Override
    public ToolResult execute(String toolCallId, Map<String, Object> params, ToolContext ctx) throws Exception {
        String taskId = (String) params.getOrDefault("task_id", "");
        if (taskId.isEmpty()) {
            return ToolResult.error("missing_param", "请提供 task_id");
        }

        String apiKey = AgnesApiConfig.getApiKey();
        if (apiKey == null) {
            return ToolResult.error("config", "未设置 AGNES_API_KEY");
        }

        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(REQUEST_TIMEOUT)).build()) {

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(AgnesApiConfig.url("/videos/" + taskId)))
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                return ToolResult.error("api_error", "查询失败 (HTTP " + response.statusCode() + ")");
            }

            JsonNode data = MAPPER.readTree(response.body());
            String status = data.has("status") ? data.get("status").asText("?") : "?";
            int progress = data.has("progress") ? data.get("progress").asInt(0) : 0;

            if ("completed".equals(status)) {
                String videoUrl = "";
                if (data.has("remixed_from_video_id") && !data.get("remixed_from_video_id").isNull()) {
                    videoUrl = data.get("remixed_from_video_id").asText("");
                }
                if (videoUrl.isEmpty() && data.has("video_url")) {
                    videoUrl = data.get("video_url").asText("");
                }

                if (!videoUrl.isEmpty()) {
                    String sizeStr = data.has("size") ? data.get("size").asText("?") : "?";
                    String seconds = data.has("seconds") ? data.get("seconds").asText("?") : "?";

                    Map<String, Object> details = new LinkedHashMap<>();
                    details.put("video_url", videoUrl);
                    details.put("task_id", taskId);
                    details.put("size", sizeStr);
                    details.put("seconds", seconds);

                    return new ToolResult(
                            List.of(new TextContent("视频已生成！\n**视频URL**: " + videoUrl
                                    + "\n**分辨率**: " + sizeStr + " | **时长**: " + seconds + "s")),
                            details, null);
                }
                return ToolResult.error("no_url", "视频已完成但未返回URL。任务ID: " + taskId);
            } else if ("failed".equals(status)) {
                String error = data.has("error") ? data.get("error").asText("") : "";
                return ToolResult.error("video_failed", "视频生成失败。任务ID: " + taskId + "，错误: " + error);
            } else {
                return new ToolResult("视频仍在生成中。状态: " + status + " (" + progress + "%)，任务ID: " + taskId);
            }
        } catch (Exception e) {
            log.debug("Check video status failed", e);
            return ToolResult.error("query_failed", e.getMessage());
        }
    }
}
