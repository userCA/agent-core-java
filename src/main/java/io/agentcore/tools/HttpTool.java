package io.agentcore.tools;

import io.agentcore.tools.util.SecurityUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * HTTP request tool with SSRF protection.
 *
 * <p>Includes SSRF protection: blocks non-http(s) schemes, localhost,
 * metadata services, private IPs, and internal hosts.
 * Redirects are disabled to prevent redirect-based SSRF bypasses.
 */
public class HttpTool implements Tool, AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    private final String toolName;
    private final String toolDescription;
    private final String method;
    private final String urlTemplate;
    private final Map<String, Object> inputSchema;
    private final Map<String, String> headers;
    private final String bearerToken;
    private final int timeoutSeconds;
    private final HttpClient httpClient;

    /**
     * Create an HTTP tool with full configuration.
     */
    public HttpTool(String name, String description, String method,
                    String urlTemplate, Map<String, Object> inputSchema,
                    Map<String, String> headers, String bearerToken,
                    int timeoutSeconds) {
        this.toolName = name;
        this.toolDescription = description;
        this.method = method;
        this.urlTemplate = urlTemplate;
        this.inputSchema = inputSchema != null ? inputSchema : Map.of(
                "type", "object", "properties", Map.of());
        this.headers = headers;
        this.bearerToken = bearerToken;
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(this.timeoutSeconds))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** Simplified constructor for a generic GET/POST tool. */
    public HttpTool(String method, String urlTemplate) {
        this("http", "Make HTTP requests to external APIs.",
                method, urlTemplate, null, null, null, DEFAULT_TIMEOUT_SECONDS);
    }

    @Override
    public void close() {
        httpClient.close();
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(toolName, toolDescription, inputSchema, null, null, null);
    }

    @Override
    public ToolResult execute(String toolCallId, Map<String, Object> params, ToolContext ctx) throws Exception {
        try {
            String resolvedUrl = resolveUrl(urlTemplate, params);
            SecurityUtils.validateUrl(resolvedUrl);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(resolvedUrl))
                    .timeout(Duration.ofSeconds(timeoutSeconds));

            if (bearerToken != null) {
                String token = bearerToken.startsWith("env:")
                        ? System.getenv(bearerToken.substring(4))
                        : bearerToken;
                if (token != null) {
                    builder.header("Authorization", "Bearer " + token);
                }
            }
            if (headers != null) {
                headers.forEach(builder::header);
            }

            if ("GET".equalsIgnoreCase(method)) {
                builder.GET();
            } else {
                builder.header("Content-Type", "application/json");
                builder.method(method.toUpperCase(),
                        HttpRequest.BodyPublishers.ofString(toJson(params)));
            }

            HttpResponse<String> response = httpClient.send(
                    builder.build(), HttpResponse.BodyHandlers.ofString());

            return new ToolResult("[status_code=" + response.statusCode() + "]\n" + response.body());
        } catch (SecurityException e) {
            return new ToolResult("Request blocked: " + e.getMessage());
        } catch (Exception e) {
            return new ToolResult("HTTP error: " + e.getMessage());
        }
    }

    private static String resolveUrl(String url, Map<String, Object> params) {
        String result = url;
        for (var entry : params.entrySet()) {
            String encoded = URLEncoder.encode(
                    String.valueOf(entry.getValue()), StandardCharsets.UTF_8);
            result = result.replace("{" + entry.getKey() + "}", encoded);
        }
        return result;
    }

    private static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
