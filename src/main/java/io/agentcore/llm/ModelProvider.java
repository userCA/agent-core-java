package io.agentcore.llm;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

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
     * Create a message converter for this provider's native format.
     *
     * <p>Default implementation returns the OpenAI-format {@link MessageConverter}.
     * Providers with a native format (e.g. Anthropic) should override this.
     *
     * @return a function that converts domain {@link io.agentcore.model.Message} lists
     *         to provider-native dict lists
     */
    default Function<List<io.agentcore.model.Message>, List<Map<String, Object>>> createMessageConverter() {
        MessageConverter converter = new MessageConverter();
        return converter::convert;
    }

    /**
     * Abort signal helper — a simple wrapper around AtomicBoolean.
     */
    static AtomicBoolean createAbortSignal() {
        return new AtomicBoolean(false);
    }
}
