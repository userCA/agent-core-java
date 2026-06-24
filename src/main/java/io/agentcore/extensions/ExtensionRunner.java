package io.agentcore.extensions;

import io.agentcore.model.Content;
import io.agentcore.extensions.HookTypes.*;
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

    public boolean hasExtensions() {
        return !extensions.isEmpty();
    }

    /**
     * Before agent start: merge results from all extensions.
     */
    public BeforeAgentStartResult onBeforeAgentStart(String prompt, String systemPrompt) {
        if (extensions.isEmpty()) return null;

        String currentSysPrompt = systemPrompt;
        for (Extension ext : extensions) {
            try {
                BeforeAgentStartResult result = ext.onBeforeAgentStart(prompt, currentSysPrompt);
                if (result instanceof BeforeAgentStartResult.ModifySystemPrompt msp) {
                    currentSysPrompt = msp.systemPrompt();
                }
            } catch (Exception e) {
                log.warn("Extension {} onBeforeAgentStart failed: {}", ext.name(), e.getMessage());
            }
        }
        if (currentSysPrompt != null && !currentSysPrompt.equals(systemPrompt)) {
            return new BeforeAgentStartResult.ModifySystemPrompt(currentSysPrompt);
        }
        return null;
    }

    /**
     * Before tool call: iterate extensions and merge typed results.
     * Returns early with Block if any extension blocks.
     */
    public ToolCallHookResult beforeToolCall(ToolCallContext context) {
        if (extensions.isEmpty()) return null;

        Map<String, Object> mergedMetadata = new LinkedHashMap<>();
        Map<String, Object> mergedArgs = null;

        for (Extension ext : extensions) {
            try {
                ToolCallHookResult result = ext.beforeToolCall(context);
                if (result == null) continue;

                switch (result) {
                    case ToolCallHookResult.Block b -> {
                        return b; // early return on block
                    }
                    case ToolCallHookResult.Proceed p -> {
                        if (p.mutatedArguments() != null) {
                            if (mergedArgs == null) mergedArgs = new LinkedHashMap<>(context.arguments());
                            mergedArgs.putAll(p.mutatedArguments());
                        }
                    }
                    case ToolCallHookResult.InjectMetadata m -> {
                        mergedMetadata.putAll(m.metadata());
                    }
                }
            } catch (Exception e) {
                log.warn("Extension {} beforeToolCall failed: {}", ext.name(), e.getMessage());
            }
        }

        // Merge: Proceed carries args; InjectMetadata is preserved in Proceed.metadata
        if (mergedArgs != null) {
            return new ToolCallHookResult.Proceed(
                    mergedArgs,
                    mergedMetadata.isEmpty() ? null : mergedMetadata
            );
        }
        if (!mergedMetadata.isEmpty()) {
            return new ToolCallHookResult.InjectMetadata(mergedMetadata);
        }
        return null;
    }

    /**
     * After tool call: iterate extensions and accumulate typed results.
     * Multiple ModifyResult responses are merged — each extension's non-null
     * fields override the previous accumulation (not the original).
     */
    public AfterToolCallHookResult afterToolCall(AfterToolCallContext context) {
        if (extensions.isEmpty()) return null;

        // Accumulated modifications (null fields = "no extension modified this yet")
        List<Content> accContent = null;
        Map<String, Object> accDetails = null;
        Boolean accIsError = null;
        Boolean accTerminate = null;
        boolean hasModification = false;

        for (Extension ext : extensions) {
            try {
                AfterToolCallHookResult result = ext.afterToolCall(context);
                if (result instanceof AfterToolCallHookResult.ModifyResult mr) {
                    hasModification = true;
                    // Merge: later non-null fields override earlier ones
                    if (mr.content() != null) accContent = mr.content();
                    if (mr.details() != null) accDetails = mr.details();
                    if (mr.isError() != null) accIsError = mr.isError();
                    if (mr.terminate() != null) accTerminate = mr.terminate();
                }
            } catch (Exception e) {
                log.warn("Extension {} afterToolCall failed: {}", ext.name(), e.getMessage());
            }
        }

        if (!hasModification) return null;
        return new AfterToolCallHookResult.ModifyResult(accContent, accDetails, accIsError, accTerminate);
    }

    /**
     * Transform context messages before LLM call.
     */
    public List<Map<String, Object>> transformContext(
            List<Map<String, Object>> messages, AtomicBoolean signal) {
        if (extensions.isEmpty()) return messages;

        List<Map<String, Object>> result = messages;
        for (Extension ext : extensions) {
            try {
                List<Map<String, Object>> transformed = ext.transformContext(result, signal);
                if (transformed != null) result = transformed;
            } catch (Exception e) {
                log.warn("Extension {} transformContext failed: {}", ext.name(), e.getMessage());
            }
        }
        return result;
    }

    /**
     * Forward an agent event to all extensions.
     */
    public void onEvent(io.agentcore.model.AgentEvent evt) {
        for (Extension ext : extensions) {
            try {
                ext.onEvent(evt);
            } catch (Exception e) {
                log.warn("Extension {} onEvent failed: {}", ext.name(), e.getMessage());
            }
        }
    }
}
