package io.agentcore.http;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for the {@code POST /chat} endpoint.
 */
public class ChatRequest {

    @JsonProperty("message")
    public String message;

    @JsonProperty("sessionId")
    public String sessionId;

    @JsonProperty("provider")
    public String provider;

    @JsonProperty("model")
    public String model;

    public ChatRequest() {
    }

    public ChatRequest(String message) {
        this.message = message;
    }

    public ChatRequest(String message, String sessionId) {
        this.message = message;
        this.sessionId = sessionId;
    }
}
