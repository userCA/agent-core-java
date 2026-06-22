package io.agentcore.extensions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs registered extensions in order with error isolation.
 *
 * <p>Each hook iterates over all extensions and merges their results.
 * If any extension throws, the error is logged and the next extension continues.
 */
public final class ExtensionRunner {

    private static final Logger log = LoggerFactory.getLogger(ExtensionRunner.class);

    private final List<Extension> extensions;

    public ExtensionRunner(List<Extension> extensions) {
        this.extensions = extensions != null ? List.copyOf(extensions) : List.of();
    }

    /**
     * Before tool call: merge results from all extensions.
     * Returns early with "block" if any extension blocks.
     */
    public Map<String, Object> beforeToolCall(Map<String, Object> toolCall) {
        if (extensions.isEmpty()) return null;

        Map<String, Object> mergedMetadata = new LinkedHashMap<>();
        Map<String, Object> mergedArgs = new LinkedHashMap<>();

        for (Extension ext : extensions) {
            try {
                Map<String, Object> result = ext.beforeToolCall(toolCall);
                if (result != null) {
                    if (Boolean.TRUE.equals(result.get("block"))) {
                        return result; // early return on block
                    }
                    if (result.get("inject_metadata") instanceof Map<?, ?> m) {
                        m.forEach((k, v) -> mergedMetadata.put((String) k, v));
                    }
                    if (result.get("mutated_args") instanceof Map<?, ?> m) {
                        m.forEach((k, v) -> mergedArgs.put((String) k, v));
                    }
                }
            } catch (Exception e) {
                log.warn("Extension {} beforeToolCall failed: {}", ext.name(), e.getMessage());
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        if (!mergedMetadata.isEmpty()) out.put("inject_metadata", mergedMetadata);
        if (!mergedArgs.isEmpty()) out.put("mutated_args", mergedArgs);
        return out.isEmpty() ? null : out;
    }

    /**
     * After tool call: merge results from all extensions.
     */
    public Map<String, Object> afterToolCall(
            Map<String, Object> toolCall, Map<String, Object> result, boolean isError) {
        if (extensions.isEmpty()) return null;

        Map<String, Object> mutated = null;
        for (Extension ext : extensions) {
            try {
                Map<String, Object> hook = ext.afterToolCall(toolCall);
                if (hook != null && hook.containsKey("result")) {
                    mutated = hook;
                }
            } catch (Exception e) {
                log.warn("Extension {} afterToolCall failed: {}", ext.name(), e.getMessage());
            }
        }
        return mutated;
    }

    /**
     * Transform context messages before LLM call.
     */
    public List<Map<String, Object>> transformContext(
            List<Map<String, Object>> messages, AtomicBoolean signal) {
        List<Map<String, Object>> result = messages;
        for (Extension ext : extensions) {
            try {
                result = ext.transformContext(result, signal);
            } catch (Exception e) {
                log.warn("Extension {} transformContext failed: {}", ext.name(), e.getMessage());
            }
        }
        return result;
    }

    /**
     * Forward an agent event to all extensions.
     */
    public void onEvent(io.agentcore.core.AgentEvent evt) {
        for (Extension ext : extensions) {
            try {
                ext.onEvent(evt);
            } catch (Exception e) {
                log.warn("Extension {} onEvent failed: {}", ext.name(), e.getMessage());
            }
        }
    }
}
