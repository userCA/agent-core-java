package io.agentcore.app.http;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for the {@code POST /api/chat} endpoint.
 *
 * <p>All fields except {@code message} are optional.
 * If {@code sessionId} is omitted, the server generates a new one.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatRequest {

    @JsonProperty("message")
    public String message;

    @JsonProperty("sessionId")
    public String sessionId;

    @JsonProperty("provider")
    public String provider;

    @JsonProperty("model")
    public String model;

    @JsonProperty("thinkingLevel")
    public String thinkingLevel;

    @JsonProperty("temperature")
    public Double temperature;

    @JsonProperty("maxTokens")
    public Integer maxTokens;

    @JsonProperty("systemPrompt")
    public String systemPrompt;

    public ChatRequest() {}

    public ChatRequest(String message) {
        this.message = message;
    }

    public ChatRequest(String message, String sessionId) {
        this.message = message;
        this.sessionId = sessionId;
    }
}
