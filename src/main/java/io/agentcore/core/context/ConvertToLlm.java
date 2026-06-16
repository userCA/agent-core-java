package io.agentcore.core.context;

import io.agentcore.core.messages.AgentMessage;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Converts internal AgentMessages to provider-format dicts (e.g. OpenAI chat format).
 */
@FunctionalInterface
public interface ConvertToLlm {
    CompletableFuture<List<Map<String, Object>>> convert(List<AgentMessage> messages);
}
