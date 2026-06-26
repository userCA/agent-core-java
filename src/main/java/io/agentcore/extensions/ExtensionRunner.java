package io.agentcore.extensions;

import io.agentcore.model.Content;
import io.agentcore.extensions.HookTypes.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs registered extensions in order with error isolation.
 *
 * <p>Each hook iterates over all extensions and merges their results.
 * If any extension throws, the error is logged and the next extension continues.
 */
public final class ExtensionRunner {

    private static final Logger log = LoggerFactory.getLogger(ExtensionRunner.class);

    private volatile List<Extension> extensions;

    public ExtensionRunner(List<Extension> extensions) {
        if (extensions == null || extensions.isEmpty()) {
            this.extensions = List.of();
        } else {
            // Sort by order() — lower values run first (higher priority)
            this.extensions = extensions.stream()
                    .sorted(Comparator.comparingInt(Extension::order))
                    .toList();
        }
    }

    public boolean hasExtensions() {
        return !extensions.isEmpty();
    }

    /**
     * Return a snapshot of current extensions (unmodifiable).
     */
    public List<Extension> extensions() {
        return this.extensions;
    }

    // ── Runtime mutation (synchronized for write safety) ─────
    // Hook methods (onBeforeToolCall, etc.) are NOT synchronized — they only
    // read the volatile reference and iterate an immutable List, so parallel
    // tool executions are not serialized.

    /**
     * Add a single extension at runtime.
     */
    public synchronized void addExtension(Extension ext) {
        addExtensions(List.of(ext));
    }

    /**
     * Add multiple extensions at runtime, re-sorting by order().
     */
    public synchronized void addExtensions(List<Extension> additional) {
        if (additional == null || additional.isEmpty()) return;
        List<Extension> merged = new ArrayList<>(this.extensions);
        merged.addAll(additional);
        merged.sort(Comparator.comparingInt(Extension::order));
        this.extensions = List.copyOf(merged);
    }

    /**
     * Remove an extension at runtime (by identity).
     */
    public synchronized void removeExtension(Extension ext) {
        List<Extension> filtered = new ArrayList<>(this.extensions);
        filtered.remove(ext);
        this.extensions = List.copyOf(filtered);
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
    public ToolCallHookResult onBeforeToolCall(ToolCallContext context) {
        if (extensions.isEmpty()) return null;

        Map<String, Object> mergedMetadata = new LinkedHashMap<>();
        Map<String, Object> mergedArgs = null;

        for (Extension ext : extensions) {
            try {
                ToolCallHookResult result = ext.onBeforeToolCall(context);
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
                log.warn("Extension {} onBeforeToolCall failed: {}", ext.name(), e.getMessage());
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
    public AfterToolCallHookResult onAfterToolCall(AfterToolCallContext context) {
        if (extensions.isEmpty()) return null;

        // Accumulated modifications (null fields = "no extension modified this yet")
        List<Content> accContent = null;
        Map<String, Object> accDetails = null;
        Boolean accIsError = null;
        Boolean accShouldTerminate = null;
        boolean hasModification = false;

        for (Extension ext : extensions) {
            try {
                AfterToolCallHookResult result = ext.onAfterToolCall(context);
                if (result instanceof AfterToolCallHookResult.ModifyResult mr) {
                    hasModification = true;
                    // Merge: later non-null fields override earlier ones
                    if (mr.content() != null) accContent = mr.content();
                    if (mr.details() != null) accDetails = mr.details();
                    if (mr.isError() != null) accIsError = mr.isError();
                    if (mr.shouldTerminate() != null) accShouldTerminate = mr.shouldTerminate();
                }
            } catch (Exception e) {
                log.warn("Extension {} onAfterToolCall failed: {}", ext.name(), e.getMessage());
            }
        }

        if (!hasModification) return null;
        return new AfterToolCallHookResult.ModifyResult(accContent, accDetails, accIsError, accShouldTerminate);
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
