package io.agentcore.providers.base;

import io.agentcore.providers.types.Model;
import io.agentcore.providers.types.ProviderAuth;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bundled parameters for a model provider stream call.
 */
public record StreamRequest(
        Model model,
        List<Map<String, Object>> messages,
        List<Map<String, Object>> tools,
        String systemPrompt,
        String thinkingLevel,
        Double temperature,
        Integer maxTokens,
        AtomicBoolean signal,
        ProviderAuth auth
) {}
