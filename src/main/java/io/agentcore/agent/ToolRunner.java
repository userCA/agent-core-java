package io.agentcore.agent;

import io.agentcore.model.Content.ToolCallContent;
import io.agentcore.model.Message.AssistantMessage;
import io.agentcore.model.Message.ToolResultMessage;
import io.agentcore.extensions.HookTypes.*;
import io.agentcore.tools.Tool;
import io.agentcore.tools.ToolContext;
import io.agentcore.tools.ToolRegistry;
import io.agentcore.model.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.concurrent.StructuredTaskScope;
import io.agentcore.model.AgentEvent;
import io.agentcore.model.Content;
import io.agentcore.model.HumanInputGate;
import io.agentcore.model.Message;

/**
 * Tool execution engine — dispatches tool calls, manages hooks and error handling.
 *
 * <p>Supports sequential and parallel execution modes.
 * Hooks are resolved dynamically via {@link #updateHooks} to allow
 * runtime hook registration (e.g. extensions added after construction).
 */
public class ToolRunner implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ToolRunner.class);
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 5;

    /** Shared ToolResult for interrupted tool executions (avoids per-call allocation). */
    private static final ToolResult INTERRUPTED_RESULT = new ToolResult("Tool execution interrupted");

    /**
     * Result of executing one tool call.
     *
     * @param terminate true if this tool requested loop termination
     *                  (via {@link ToolContext#requestTerminate()} or after-hook override)
     */
    public record ToolCallResult(
            String toolCallId,
            String toolName,
            ToolResult result,
            boolean isError,
            boolean terminate
    ) {}

    /**
     * Result of executing a batch of tool calls.
     *
     * <p>{@code terminate} is true when <b>all</b> tool results in the batch
     * have their individual {@code terminate} flag set. This "unanimous consent"
     * strategy prevents a single tool from accidentally stopping the loop when
     * other tools expect to continue.
     *
     * @param messages  tool result messages
     * @param terminate true if ALL tool results requested termination
     */
    public record ToolBatchResult(
            List<ToolResultMessage> messages,
            boolean terminate
    ) {}

    private final ToolRegistry registry;
    private final Double defaultTimeout;
    private final int toolResultMaxChars;
    private final ExecutorService executor;
    private final Semaphore parallelSemaphore;

    // Volatile hooks — resolved at execution time, not construction time.
    // This allows Agent to update hooks dynamically (e.g. after adding extensions).
    private volatile AgentLoopConfig.BeforeToolCallHook beforeToolCall;
    private volatile AgentLoopConfig.AfterToolCallHook afterToolCall;

    // Human-in-the-loop gate — allows tools to pause for user input.
    // Injected once at construction time; immutable for the runner's lifetime.
    private final HumanInputGate humanInputGate;

    /**
     * Create a ToolRunner from a ToolConfig sub-config (preferred).
     */
    public ToolRunner(ToolRegistry registry, AgentLoopConfig.ToolConfig toolConfig,
                      AgentLoopConfig.BeforeToolCallHook beforeToolCall,
                      AgentLoopConfig.AfterToolCallHook afterToolCall) {
        this(registry, toolConfig, beforeToolCall, afterToolCall, null);
    }

    /**
     * Create a ToolRunner with HITL (Human-in-the-Loop) support.
     *
     * @param humanInputGate gate for pausing/resuming on human input (nullable)
     */
    public ToolRunner(ToolRegistry registry, AgentLoopConfig.ToolConfig toolConfig,
                      AgentLoopConfig.BeforeToolCallHook beforeToolCall,
                      AgentLoopConfig.AfterToolCallHook afterToolCall,
                      HumanInputGate humanInputGate) {
        this.registry = registry;
        this.defaultTimeout = toolConfig != null ? toolConfig.timeout() : null;
        this.toolResultMaxChars = toolConfig != null ? toolConfig.resultMaxChars() : 4000;
        int maxParallel = toolConfig != null ? toolConfig.maxParallelTools() : 10;
        this.parallelSemaphore = new Semaphore(maxParallel);
        this.beforeToolCall = beforeToolCall;
        this.afterToolCall = afterToolCall;
        this.humanInputGate = humanInputGate;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Update hooks dynamically. Called by Agent before each runLoop iteration
     * to ensure the latest extension hooks are resolved.
     */
    public void updateHooks(AgentLoopConfig.BeforeToolCallHook beforeToolCall,
                            AgentLoopConfig.AfterToolCallHook afterToolCall) {
        this.beforeToolCall = beforeToolCall;
        this.afterToolCall = afterToolCall;
    }

    /**
     * Execute all tool calls from an assistant message sequentially.
     */
    public ToolBatchResult executeSequential(
            AssistantMessage assistant,
            AtomicBoolean signal,
            Consumer<AgentEvent> onEvent) {

        List<ToolCallContent> calls = assistant.toolCalls();
        if (calls.isEmpty()) return new ToolBatchResult(List.of(), false);

        List<ToolResultMessage> results = new ArrayList<>();
        boolean allTerminate = true;

        for (ToolCallContent tc : calls) {
            // Check abort signal before each tool
            if (signal != null && signal.get()) {
                log.debug("Sequential execution aborted by signal");
                break;
            }

            if (onEvent != null) {
                onEvent.accept(new AgentEvent.ToolExecutionStart(
                        tc.id(), tc.name(), tc.arguments()));
            }

            ToolCallResult callResult = runSingleTool(tc, signal, null, onEvent);

            if (onEvent != null) {
                onEvent.accept(new AgentEvent.ToolExecutionEnd(
                        tc.id(), tc.name(), callResult.result(), callResult.isError()));
            }

            results.add(new ToolResultMessage(
                    tc.id(), tc.name(),
                    truncateContent(callResult.result().content()),
                    callResult.isError(),
                    Message.nowEpochSeconds()
            ));
            if (!callResult.terminate()) allTerminate = false;

            // Early-terminate: stop executing remaining tools
            if (callResult.terminate()) {
                log.debug("Sequential execution stopped early: tool '{}' requested terminate", tc.name());
                break;
            }
        }

        return new ToolBatchResult(results, !results.isEmpty() && allTerminate);
    }

    /**
     * Execute all tool calls in parallel using StructuredTaskScope (Java 21+).
     */
    public ToolBatchResult executeParallel(
            AssistantMessage assistant,
            AtomicBoolean signal,
            Consumer<AgentEvent> onEvent) {

        List<ToolCallContent> calls = assistant.toolCalls();
        if (calls.isEmpty()) return new ToolBatchResult(List.of(), false);

        // Emit all start events first
        if (onEvent != null) {
            for (ToolCallContent tc : calls) {
                onEvent.accept(new AgentEvent.ToolExecutionStart(
                        tc.id(), tc.name(), tc.arguments()));
            }
        }

        // Run in parallel using StructuredTaskScope (plain — no ShutdownOnFailure).
        // Each tool handles its own errors via runSingleTool, so individual tool
        // failures do NOT cancel other running tools.
        ToolCallResult[] results = new ToolCallResult[calls.size()];
        try (var scope = new StructuredTaskScope()) {
            List<StructuredTaskScope.Subtask<Integer>> subtasks = new ArrayList<>(calls.size());
            for (int i = 0; i < calls.size(); i++) {
                final int idx = i;
                final ToolCallContent tc = calls.get(i);
                subtasks.add(scope.fork(() -> {
                    parallelSemaphore.acquire();
                    try {
                        results[idx] = runSingleTool(tc, signal, null, onEvent);
                    } finally {
                        parallelSemaphore.release();
                    }
                    return idx;
                }));
            }
            scope.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("Parallel tool execution interrupted");
        } catch (Exception e) {
            log.warn("Parallel tool execution failed", e);
        }

        // Emit end events and build messages
        List<ToolResultMessage> messages = new ArrayList<>();
        boolean allTerminate = true;
        for (int i = 0; i < calls.size(); i++) {
            ToolCallContent tc = calls.get(i);
            ToolCallResult cr = results[i];
            if (cr == null) {
                cr = new ToolCallResult(tc.id(), tc.name(), INTERRUPTED_RESULT, true, false);
            }

            if (onEvent != null) {
                onEvent.accept(new AgentEvent.ToolExecutionEnd(
                        tc.id(), tc.name(), cr.result(), cr.isError()));
            }

            messages.add(new ToolResultMessage(
                    tc.id(), tc.name(), truncateContent(cr.result().content()), cr.isError(),
                    Message.nowEpochSeconds()));
            if (!cr.terminate()) allTerminate = false;
        }

        return new ToolBatchResult(messages, !messages.isEmpty() && allTerminate);
    }

    // ── Three-stage tool pipeline ──────────────────────────

    /**
     * Result of the prepare stage: resolved tool, possibly mutated args, or a block.
     */
    private record PreparedToolCall(
            Tool tool,
            ToolCallContent toolCall,
            Map<String, Object> arguments,
            Double effectiveTimeout,
            Map<String, Object> metadata,
            ToolCallResult blockedResult  // non-null if the call was blocked by before-hook
    ) {
        boolean isBlocked() { return blockedResult != null; }
    }

    /**
     * Result of the execute stage: raw tool result, error flag, and terminate signal.
     */
    private record RawToolResult(
            ToolResult result,
            boolean isError,
            boolean terminate
    ) {}

    /**
     * Stage 1: Prepare — resolve tool, apply prepareArguments + before-hook, compute timeout.
     */
    private PreparedToolCall prepareToolCall(ToolCallContent tc) {
        Tool tool = registry.get(tc.name());
        if (tool == null) {
            return new PreparedToolCall(null, tc, null, null, null,
                    new ToolCallResult(tc.id(), tc.name(),
                            new ToolResult("Tool '" + tc.name() + "' not found."), true, false));
        }

        Map<String, Object> args = tc.arguments();
        Map<String, Object> metadata = null;

        // prepareArguments hook on the tool itself
        try {
            args = tool.prepareArguments(args);
        } catch (Exception e) {
            log.warn("prepareArguments hook failed for '{}', using raw args", tc.name(), e);
        }

        // Before hook (typed)
        AgentLoopConfig.BeforeToolCallHook hook = this.beforeToolCall;
        if (hook != null) {
            try {
                ToolCallContext hookCtx = new ToolCallContext(tc, args);
                ToolCallHookResult hookResult = hook.apply(hookCtx);
                if (hookResult != null) {
                    switch (hookResult) {
                        case ToolCallHookResult.Block b -> {
                            return new PreparedToolCall(null, tc, null, null, null,
                                    new ToolCallResult(tc.id(), tc.name(),
                                            new ToolResult(b.reason()), true, false));
                        }
                        case ToolCallHookResult.Proceed p -> {
                            if (p.mutatedArguments() != null) {
                                args = p.mutatedArguments();
                            }
                            if (p.metadata() != null) {
                                metadata = p.metadata();
                            }
                        }
                        case ToolCallHookResult.InjectMetadata im -> {
                            metadata = im.metadata();
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Before-tool-call hook failure", e);
            }
        }

        Double effectiveTimeout = tool.definition().timeoutSeconds();
        if (effectiveTimeout == null) effectiveTimeout = defaultTimeout;

        return new PreparedToolCall(tool, tc, args, effectiveTimeout, metadata, null);
    }

    /**
     * Stage 2: Execute — run the tool with optional timeout, capture terminate signal.
     */
    private RawToolResult executePreparedToolCall(
            PreparedToolCall prepared,
            AtomicBoolean signal,
            Consumer<ToolResult> onUpdate) {
        return executePreparedToolCall(prepared, signal, onUpdate, null);
    }

    /**
     * Stage 2: Execute — run the tool with optional timeout and user input.
     */
    private RawToolResult executePreparedToolCall(
            PreparedToolCall prepared,
            AtomicBoolean signal,
            Consumer<ToolResult> onUpdate,
            Map<String, Object> userInput) {

        Tool tool = prepared.tool();
        ToolCallContent tc = prepared.toolCall();
        Map<String, Object> args = prepared.arguments();

        // Create terminate signal channel for the tool to use
        AtomicBoolean terminateSignal = new AtomicBoolean(false);
        Map<String, Object> ctxMetadata = prepared.metadata() != null ? prepared.metadata() : Map.of();
        ToolContext ctx = new ToolContext(signal, onUpdate, ctxMetadata, terminateSignal, userInput);

        ToolResult result;
        boolean isError;

        Double effectiveTimeout = prepared.effectiveTimeout();
        if (effectiveTimeout != null && effectiveTimeout > 0) {
            try {
                result = executeWithTimeout(tool, tc.id(), args, ctx, effectiveTimeout);
                isError = false;
            } catch (TimeoutException e) {
                result = new ToolResult("Tool '" + tc.name() + "' timed out after "
                        + Math.round(effectiveTimeout) + "s");
                isError = true;
            } catch (Exception e) {
                result = new ToolResult(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                isError = true;
            }
        } else {
            try {
                result = tool.execute(tc.id(), args, ctx);
                isError = false;
            } catch (Exception e) {
                result = new ToolResult(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                isError = true;
            }
        }

        // Read terminate signal from the tool's context
        boolean terminate = terminateSignal.get();
        return new RawToolResult(result, isError, terminate);
    }

    /**
     * Stage 3: Finalize — apply after-hook (may override terminate), produce final result.
     */
    private ToolCallResult finalizeToolCall(
            PreparedToolCall prepared,
            RawToolResult rawResult) {

        ToolCallContent tc = prepared.toolCall();
        Map<String, Object> args = prepared.arguments();
        ToolResult result = rawResult.result();
        boolean isError = rawResult.isError();
        boolean terminate = rawResult.terminate();

        // After hook (typed) — may override content, details, isError, and terminate
        AgentLoopConfig.AfterToolCallHook hook = this.afterToolCall;
        if (hook != null) {
            try {
                AfterToolCallContext hookCtx = new AfterToolCallContext(tc, args, result, isError);
                AfterToolCallHookResult hookResult = hook.apply(hookCtx);
                if (hookResult instanceof AfterToolCallHookResult.ModifyResult mr) {
                    var newContent = mr.content() != null ? mr.content() : result.content();
                    var newDetails = mr.details() != null ? mr.details() : result.details();
                    result = new ToolResult(newContent, newDetails, result.display());
                    if (mr.isError() != null) isError = mr.isError();
                    if (mr.terminate() != null) terminate = mr.terminate();
                }
            } catch (Exception e) {
                log.warn("After-tool-call hook failure", e);
            }
        }

        return new ToolCallResult(tc.id(), tc.name(), result, isError, terminate);
    }

    /**
     * Run the full three-stage pipeline for a single tool call.
     * Handles HITL (Human-in-the-Loop) by checking ToolResult.requiresInput().
     */
    private ToolCallResult runSingleTool(
            ToolCallContent tc,
            AtomicBoolean signal,
            Consumer<ToolResult> onUpdate) {
        return runSingleTool(tc, signal, onUpdate, null);
    }

    /**
     * Run the full three-stage pipeline for a single tool call, with event consumer for HITL.
     */
    private ToolCallResult runSingleTool(
            ToolCallContent tc,
            AtomicBoolean signal,
            Consumer<ToolResult> onUpdate,
            Consumer<AgentEvent> onEvent) {

        // Stage 1: Prepare
        PreparedToolCall prepared = prepareToolCall(tc);
        if (prepared.isBlocked()) return prepared.blockedResult();

        // Stage 2: Execute
        RawToolResult rawResult = executePreparedToolCall(prepared, signal, onUpdate);

        // Check HITL: if the tool returned requiresInput, pause and wait for user
        if (rawResult.result().requiresInput()) {
            return handleHumanInput(prepared, signal, onUpdate, onEvent, rawResult.result());
        }

        // Stage 3: Finalize
        return finalizeToolCall(prepared, rawResult);
    }

    /**
     * Handle HITL: emit event, block on gate, re-execute with user input via ToolContext.
     */
    private ToolCallResult handleHumanInput(
            PreparedToolCall prepared,
            AtomicBoolean signal,
            Consumer<ToolResult> onUpdate,
            Consumer<AgentEvent> onEvent,
            ToolResult requiresInputResult) {

        HumanInputGate gate = this.humanInputGate;
        ToolCallContent tc = prepared.toolCall();

        if (gate == null) {
            log.warn("Tool returned requiresInput but no HumanInputGate configured");
            return new ToolCallResult(tc.id(), tc.name(),
                    new ToolResult("Human input required but no input channel configured"), true, false);
        }

        // Emit HumanInputRequired event
        if (onEvent != null) {
            onEvent.accept(new AgentEvent.HumanInputRequired(
                    tc.id(), requiresInputResult.inputPrompt(), requiresInputResult.inputSchema()));
        }

        // Block until input arrives (virtual thread friendly)
        try {
            CompletableFuture<Map<String, Object>> future = gate.requireInput(tc.id());
            Map<String, Object> userInput = future.get();
            log.debug("Human input received for tool call {}", tc.id());

            // Re-execute only Stage 2 with user input injected into ToolContext
            RawToolResult rawResult = executePreparedToolCall(prepared, signal, onUpdate, userInput);
            return finalizeToolCall(prepared, rawResult);
        } catch (CancellationException ce) {
            return new ToolCallResult(tc.id(), tc.name(),
                    new ToolResult("Human input request was cancelled"), true, false);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return new ToolCallResult(tc.id(), tc.name(), INTERRUPTED_RESULT, true, false);
        } catch (Exception ex) {
            log.warn("Human input handling failed for tool call {}", tc.id(), ex);
            return new ToolCallResult(tc.id(), tc.name(),
                    new ToolResult("Human input handling failed: " + ex.getMessage()), true, false);
        }
    }

    /**
     * Execute a tool with a timeout using the shared virtual thread executor.
     */
    private ToolResult executeWithTimeout(Tool tool, String callId,
                                           Map<String, Object> args, ToolContext ctx,
                                           double timeoutSeconds) throws Exception {
        Future<ToolResult> future = executor.submit(() -> tool.execute(callId, args, ctx));
        try {
            return future.get((long) (timeoutSeconds * 1000), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            future.cancel(true);
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) throw ex;
            throw new RuntimeException(cause);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Tool execution interrupted", e);
        }
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("ToolRunner executor did not terminate within {}s, forcing shutdown",
                        SHUTDOWN_TIMEOUT_SECONDS);
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    /**
     * Truncate tool result content to stay within {@code toolResultMaxChars}.
     * Only truncates TextContent blocks; other content types pass through unchanged.
     */
    private List<Content> truncateContent(List<Content> content) {
        if (toolResultMaxChars <= 0 || content == null) return content;

        int totalChars = 0;
        boolean needsTruncation = false;
        for (var c : content) {
            if (c instanceof Content.TextContent tc) {
                totalChars += tc.text().length();
                if (totalChars > toolResultMaxChars) { needsTruncation = true; break; }
            }
        }
        if (!needsTruncation) return content;

        List<Content> truncated = new ArrayList<>();
        int used = 0;
        for (var c : content) {
            if (c instanceof Content.TextContent tc) {
                int remaining = toolResultMaxChars - used;
                if (remaining <= 0) {
                    truncated.add(new Content.TextContent("[truncated]"));
                    break;
                }
                String text = tc.text();
                if (text.length() > remaining) {
                    truncated.add(new Content.TextContent(
                            text.substring(0, remaining) + "\n[truncated " + (text.length() - remaining) + " chars]"));
                    break;
                }
                truncated.add(c);
                used += text.length();
            } else {
                truncated.add(c);
            }
        }
        return truncated;
    }
}
