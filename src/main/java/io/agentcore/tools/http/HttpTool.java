package io.agentcore.tools.http;

import io.agentcore.core.content.TextContent;
import io.agentcore.tools.base.*;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

public class HttpTool implements Tool, AutoCloseable {
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    private final String name;
    private final String description;
    private final String method;
    private final String url;
    private final Map<String, Object> parameters;
    private final BearerAuth auth;
    private final Map<String, String> headers;
    private final double timeout;
    private final HttpClient httpClient;

    public HttpTool(String name, String description, String method, String url,
                    Map<String, Object> parameters, BearerAuth auth,
                    Map<String, String> headers, double timeout) {
        this.name = name;
        this.description = description;
        this.method = method;
        this.url = url;
        this.parameters = parameters;
        this.auth = auth;
        this.headers = headers;
        this.timeout = timeout > 0 ? timeout : DEFAULT_TIMEOUT_SECONDS;
        int timeoutSec = (int) this.timeout;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSec))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public void close() {
        httpClient.close();
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(name, description, parameters);
    }

    @Override
    public CompletableFuture<ToolResult> execute(String toolCallId, Map<String, Object> params, ToolContext ctx) {
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        return CompletableFuture.supplyAsync(() -> {
            try {
                String resolvedUrl = resolveUrl(url, params);
                validateUrl(resolvedUrl);

                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(resolvedUrl))
                        .timeout(Duration.ofSeconds((int) timeout));

                if (auth != null) {
                    String token = auth.resolve();
                    if (token != null) builder.header("Authorization", "Bearer " + token);
                }
                if (headers != null) headers.forEach(builder::header);

                if ("GET".equalsIgnoreCase(method)) {
                    builder.GET();
                } else {
                    builder.header("Content-Type", "application/json");
                    builder.method(method.toUpperCase(),
                            HttpRequest.BodyPublishers.ofString(toJson(params)));
                }

                HttpResponse<String> response = httpClient.send(builder.build(),
                        HttpResponse.BodyHandlers.ofString());

                return new ToolResult(List.of(new TextContent(response.body())));
            } catch (SecurityException e) {
                return new ToolResult(List.of(new TextContent("Request blocked: " + e.getMessage())));
            } catch (Exception e) {
                return new ToolResult(List.of(new TextContent("HTTP error: " + e.getMessage())));
            }
        }, executor).whenComplete((result, error) -> executor.close());
    }

    /**
     * Validate URL to prevent SSRF attacks.
     * Blocks non-http(s) schemes, private IPs, and cloud metadata endpoints.
     */
    private static void validateUrl(String url) {
        URI uri = URI.create(url);
        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
            throw new SecurityException("Only http and https schemes are allowed, got: " + scheme);
        }

        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            throw new SecurityException("URL must have a valid host");
        }

        // Block well-known metadata and loopback hosts (string-level check)
        String hostLower = host.toLowerCase();
        if (hostLower.equals("localhost")
                || hostLower.equals("metadata.google.internal")
                || hostLower.equals("[::1]")
                || hostLower.contains("169.254.169.254")
                || hostLower.endsWith(".internal")
                || hostLower.endsWith(".local")) {
            throw new SecurityException("Access to metadata services and internal hosts is blocked");
        }

        // Resolve hostname and check for private/reserved IPs
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (addr.isLoopbackAddress() || addr.isLinkLocalAddress()
                        || addr.isSiteLocalAddress() || addr.isAnyLocalAddress()
                        || addr.isMulticastAddress()) {
                    throw new SecurityException("Access to private/internal network addresses is blocked");
                }
                // Additional check for cloud metadata IPs (169.254.169.254, fd00:ec2::254)
                byte[] raw = addr.getAddress();
                if (raw.length == 4) {
                    // IPv4: block 169.254.0.0/16 (link-local range)
                    if ((raw[0] & 0xFF) == 169 && (raw[1] & 0xFF) == 254) {
                        throw new SecurityException("Access to link-local addresses is blocked");
                    }
                    // Block 100.64.0.0/10 (carrier-grade NAT, often used internally)
                    if ((raw[0] & 0xFF) == 100 && ((raw[1] & 0xFF) & 0xC0) == 64) {
                        throw new SecurityException("Access to reserved address ranges is blocked");
                    }
                }
            }
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new SecurityException("Unable to resolve host: " + host);
        }
    }

    private static String resolveUrl(String url, Map<String, Object> params) {
        String result = url;
        for (var entry : params.entrySet()) {
            String encoded = java.net.URLEncoder.encode(String.valueOf(entry.getValue()),
                    java.nio.charset.StandardCharsets.UTF_8);
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

    public static class BearerAuth {
        private final String envVar;
        private final String token;

        public BearerAuth(String envVar, String token) {
            this.envVar = envVar;
            this.token = token;
        }

        public String resolve() {
            if (token != null) return token;
            if (envVar != null) return System.getenv(envVar);
            return null;
        }
    }
}
