package io.agentcore.providers;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * LLM provider interface — all model adapters implement this.
 *
 * <p>Mirrors Python {@code agent_core/providers/base.py} ModelProvider Protocol.
 * Uses blocking {@link Iterator} instead of async generator — designed for virtual threads.
 */
public interface ModelProvider {

    /**
     * Provider name (e.g. "openai", "anthropic").
     */
    String name();

    /**
     * List available models from this provider.
     */
    List<ModelInfo> listModels();

    /**
     * Stream a chat completion response.
     *
     * @param model         the model to use
     * @param messages      conversation messages in OpenAI dict format
     * @param tools         tool definitions in OpenAI function-calling format
     * @param systemPrompt  the system prompt
     * @param thinkingLevel thinking effort level ("off", "low", "medium", "high", "xhigh")
     * @param temperature   sampling temperature (null for default)
     * @param maxTokens     max output tokens (null for model default)
     * @param abortSignal   signal to abort the stream (nullable)
     * @param auth          provider credentials
     * @return blocking iterator of stream events (consume on virtual thread)
     */
    Iterator<StreamEvent> stream(
            ModelInfo model,
            List<Map<String, Object>> messages,
            List<Map<String, Object>> tools,
            String systemPrompt,
            String thinkingLevel,
            Double temperature,
            Integer maxTokens,
            AtomicBoolean abortSignal,
            ProviderAuth auth
    );

    /**
     * Simplified stream call with defaults.
     */
    default Iterator<StreamEvent> stream(
            ModelInfo model,
            List<Map<String, Object>> messages,
            List<Map<String, Object>> tools,
            String systemPrompt,
            ProviderAuth auth) {
        return stream(model, messages, tools, systemPrompt, "off", null, null, null, auth);
    }

    /**
     * Abort signal helper — a simple wrapper around AtomicBoolean.
     */
    static AtomicBoolean createAbortSignal() {
        return new AtomicBoolean(false);
    }
}
