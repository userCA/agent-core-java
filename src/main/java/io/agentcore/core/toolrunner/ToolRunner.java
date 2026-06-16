package io.agentcore.core.toolrunner;

import io.agentcore.core.content.TextContent;
import io.agentcore.core.content.ToolCallContent;
import io.agentcore.core.context.AgentContext;
import io.agentcore.core.context.AgentLoopConfig;
import io.agentcore.core.context.AfterToolCallResult;
import io.agentcore.core.context.BeforeToolCallResult;
import io.agentcore.core.context.ToolExecutionMode;
import io.agentcore.core.events.*;
import io.agentcore.core.humaninput.HumanInputGate;
import io.agentcore.core.humaninput.RequiresHumanInput;
import io.agentcore.core.messages.ToolResultMessage;
import io.agentcore.tools.base.*;
import io.agentcore.tools.mutation.FileMutationQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Tool execution engine — dispatches tool calls in parallel or sequential mode,
 * handles hooks, HITL integration, and timeout management.
 */
public final class ToolRunner {
    private static final Logger log = LoggerFactory.getLogger(ToolRunner.class);
    private static final int MAX_CONCURRENT = 8;

    private ToolRunner() {}

    /**
     * Execute all tool calls from the assistant message, emitting events via the emit callback.
     * Results are appended to context.messages and toolResultsOut.
     */
    public static void executeTools(
            List<ToolCallContent> calls,
            AgentLoopConfig config,
            AgentContext context,
            AtomicBoolean signal,
            List<ToolResultMessage> toolResultsOut,
            HumanInputGate humanInputGate,
            FileMutationQueue mutationQueue,
            Consumer<AgentEvent> emit
    ) {
        ToolRegistry registry = config.toolRegistry();
        if (registry == null || calls.isEmpty()) return;

        if (config.toolExecution() == ToolExecutionMode.PARALLEL) {
            executeParallel(calls, registry, config, context, signal, toolResultsOut,
                    humanInputGate, mutationQueue, emit);
        } else {
            executeSequential(calls, registry, config, context, signal, toolResultsOut,
                    humanInputGate, mutationQueue, emit);
        }
    }

    private static void executeParallel(
            List<ToolCallContent> calls,
            ToolRegistry registry,
            AgentLoopConfig config,
            AgentContext context,
            AtomicBoolean signal,
            List<ToolResultMessage> toolResultsOut,
            HumanInputGate humanInputGate,
            FileMutationQueue mutationQueue,
            Consumer<AgentEvent> emit
    ) {
        // Emit ToolExecutionStart for all calls upfront
        for (var tc : calls) {
            emit.accept(new ToolExecutionStart(tc.id(), tc.name(), tc.arguments()));
        }

        // Run all tools in parallel using Virtual Threads
        Semaphore sem = new Semaphore(MAX_CONCURRENT);
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<CompletableFuture<ToolExecResult>> futures = new ArrayList<>();

            for (var tc : calls) {
                // Provide an onUpdate callback that emits progress events directly
                Consumer<ToolResult> parallelOnUpdate = partial ->
                        emit.accept(new ToolExecutionUpdate(tc.id(), tc.name(), tc.arguments(), partial));
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        sem.acquire();
                        try {
                            return runSingleTool(tc, registry, config, signal, mutationQueue, parallelOnUpdate);
                        } finally {
                            sem.release();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return new ToolExecResult(tc, new ToolResult(List.of(new TextContent("Interrupted"))), true);
                    }
                }, executor));
            }

            // Wait for all to complete
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

            // Emit results, handling RequiresHumanInput exceptions
            for (int i = 0; i < futures.size(); i++) {
                var future = futures.get(i);
                ToolExecResult result;
                try {
                    result = future.join();
                } catch (CompletionException e) {
                    if (e.getCause() instanceof RequiresHumanInput hitl) {
                        result = handleHumanInput(calls.get(i), hitl, humanInputGate, emit);
                    } else {
                        // Convert to error result instead of crashing the agent loop
                        Throwable cause = e.getCause();
                        String msg = cause != null ? cause.getMessage() : e.getMessage();
                        log.warn("Tool '{}' execution failed in parallel mode: {}", calls.get(i).name(), msg);
                        result = new ToolExecResult(calls.get(i),
                                new ToolResult(List.of(new TextContent(msg))), true);
                    }
                }
                var toolResultMsg = new ToolResultMessage(
                        result.toolCall().id(), result.toolCall().name(),
                        result.result().content(), result.isError(),
                        System.currentTimeMillis() / 1000.0
                );
                emit.accept(new ToolExecutionEnd(
                        result.toolCall().id(), result.toolCall().name(), toolResultMsg, result.isError()));
                context.messages().add(toolResultMsg);
                toolResultsOut.add(toolResultMsg);
            }
        } finally {
            executor.close();
        }
    }

    private static void executeSequential(
            List<ToolCallContent> calls,
            ToolRegistry registry,
            AgentLoopConfig config,
            AgentContext context,
            AtomicBoolean signal,
            List<ToolResultMessage> toolResultsOut,
            HumanInputGate humanInputGate,
            FileMutationQueue mutationQueue,
            Consumer<AgentEvent> emit
    ) {
        // Create single executor for all sequential tool calls, close when done
        var executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
        try {
        for (var tc : calls) {
            emit.accept(new ToolExecutionStart(tc.id(), tc.name(), tc.arguments()));

            // Create update queue for intermediate results
            BlockingQueue<ToolResult> updateQueue = new LinkedBlockingQueue<>();
            Consumer<ToolResult> onUpdate = updateQueue::offer;

            // Start tool on Virtual Thread
            CompletableFuture<ToolExecResult> toolFuture = CompletableFuture.supplyAsync(
                    () -> runSingleTool(tc, registry, config, signal, mutationQueue, onUpdate),
                    executor
            );

            // Poll update queue while tool runs
            while (!toolFuture.isDone()) {
                try {
                    ToolResult partial = updateQueue.poll(500, TimeUnit.MILLISECONDS);
                    if (partial != null) {
                        emit.accept(new ToolExecutionUpdate(tc.id(), tc.name(), tc.arguments(), partial));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            // Drain remaining updates
            ToolResult remaining;
            while ((remaining = updateQueue.poll()) != null) {
                emit.accept(new ToolExecutionUpdate(tc.id(), tc.name(), tc.arguments(), remaining));
            }

            ToolExecResult execResult;
            try {
                execResult = toolFuture.get();
            } catch (ExecutionException e) {
                if (e.getCause() instanceof RequiresHumanInput hitl) {
                    execResult = handleHumanInput(tc, hitl, humanInputGate, emit);
                } else {
                    Throwable cause = e.getCause();
                    String msg = cause != null ? cause.getMessage() : e.getMessage();
                    execResult = new ToolExecResult(tc,
                            new ToolResult(List.of(new TextContent(msg))), true);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                execResult = new ToolExecResult(tc,
                        new ToolResult(List.of(new TextContent("Interrupted"))), true);
            }

            var toolResultMsg = new ToolResultMessage(
                    execResult.toolCall().id(), execResult.toolCall().name(),
                    execResult.result().content(), execResult.isError(),
                    System.currentTimeMillis() / 1000.0
            );
            emit.accept(new ToolExecutionEnd(
                    execResult.toolCall().id(), execResult.toolCall().name(), toolResultMsg, execResult.isError()));
            context.messages().add(toolResultMsg);
            toolResultsOut.add(toolResultMsg);
        }
        } finally {
            executor.close();
        }
    }

    private static ToolExecResult handleHumanInput(
            ToolCallContent tc,
            RequiresHumanInput hitl,
            HumanInputGate gate,
            Consumer<AgentEvent> emit
    ) {
        if (gate == null) {
            return new ToolExecResult(tc,
                    new ToolResult(List.of(new TextContent("Human input required but HITL is not configured."))),
                    true);
        }

        var future = gate.requireInput(tc.id());
        emit.accept(new HumanInputRequired(tc.id(), hitl.prompt(), hitl.inputSchema()));

        try {
            Map<String, Object> values = future.get(); // blocks Virtual Thread
            // Truncate long base64 data
            Map<String, Object> displayValues = new LinkedHashMap<>();
            for (var entry : values.entrySet()) {
                Object v = entry.getValue();
                if (v instanceof String s && s.startsWith("data:") && s.length() > 500) {
                    String mime = s.contains(";") ? s.substring(s.indexOf(':') + 1, s.indexOf(';')) : "unknown";
                    displayValues.put(entry.getKey(), "[binary " + mime + " data, " + s.length() + " chars]");
                } else {
                    displayValues.put(entry.getKey(), v);
                }
            }
            return new ToolExecResult(tc,
                    new ToolResult(List.of(new TextContent(displayValues.toString()))), false);
        } catch (Exception e) {
            return new ToolExecResult(tc,
                    new ToolResult(List.of(new TextContent("HITL input failed: " + e.getMessage()))), true);
        }
    }

    /**
     * Run a single tool with hooks, validation, timeout, and error handling.
     */
    static ToolExecResult runSingleTool(
            ToolCallContent toolCall,
            ToolRegistry registry,
            AgentLoopConfig config,
            AtomicBoolean signal,
            FileMutationQueue mutationQueue,
            Consumer<ToolResult> onUpdate
    ) {
        Tool tool = registry.get(toolCall.name());
        if (tool == null) {
            return new ToolExecResult(toolCall,
                    new ToolResult(List.of(new TextContent("Tool '" + toolCall.name() + "' not found."))), true);
        }

        Map<String, Object> args = new LinkedHashMap<>(toolCall.arguments());

        // Before hook
        if (config.beforeToolCall() != null) {
            try {
                Map<String, Object> callCtx = new LinkedHashMap<>();
                callCtx.put("tool_call", toolCall);
                callCtx.put("args", args);
                BeforeToolCallResult hookResult = config.beforeToolCall().apply(callCtx).get();
                if (hookResult != null) {
                    if (hookResult.block()) {
                        String reason = hookResult.reason() != null ? hookResult.reason() : "Blocked by before_tool_call hook.";
                        return new ToolExecResult(toolCall,
                                new ToolResult(List.of(new TextContent(reason))), true);
                    }
                    if (hookResult.mutatedArgs() != null) {
                        args = new LinkedHashMap<>(hookResult.mutatedArgs());
                    }
                }
            } catch (Exception exc) {
                log.warn("before_tool_call hook failed: {}", exc.getMessage());
            }
        }

        // Execute tool
        AtomicBoolean abortSignal = signal != null ? signal : new AtomicBoolean(false);
        ToolContext ctx = new ToolContext(abortSignal, onUpdate, Map.of(), mutationQueue);

        ToolResult result;
        boolean isError;
        CompletableFuture<ToolResult> execFuture = null;
        try {
            execFuture = tool.execute(toolCall.id(), args, ctx);
            Double timeout = config.toolTimeout();
            if (tool.definition().timeoutSeconds() != null) {
                timeout = tool.definition().timeoutSeconds();
            }
            if (timeout != null) {
                result = execFuture.get((long) (timeout * 1000), TimeUnit.MILLISECONDS);
            } else {
                result = execFuture.get();
            }
            isError = false;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RequiresHumanInput) {
                throw new CompletionException(e.getCause());
            }
            log.warn("Tool '{}' execution failed: {}", toolCall.name(), e.getMessage());
            result = new ToolResult(List.of(new TextContent(e.getCause() != null ? e.getCause().getMessage() : e.getMessage())));
            isError = true;
        } catch (TimeoutException e) {
            if (execFuture != null) {
                execFuture.cancel(true);
            }
            double t = config.toolTimeout() != null ? config.toolTimeout() : 120.0;
            result = new ToolResult(List.of(new TextContent(
                    "Tool '" + toolCall.name() + "' timed out after " + (int) t + "s")));
            isError = true;
        } catch (InterruptedException e) {
            if (execFuture != null) {
                execFuture.cancel(true);
            }
            Thread.currentThread().interrupt();
            result = new ToolResult(List.of(new TextContent("Interrupted")));
            isError = true;
        }

        // After hook
        if (config.afterToolCall() != null) {
            try {
                Map<String, Object> callCtx = new LinkedHashMap<>();
                callCtx.put("tool_call", toolCall);
                callCtx.put("args", args);
                callCtx.put("result", result);
                callCtx.put("is_error", isError);
                AfterToolCallResult hookResult = config.afterToolCall().apply(callCtx).get();
                if (hookResult != null && hookResult.resultOverride() != null) {
                    result = hookResult.resultOverride();
                }
            } catch (Exception exc) {
                log.warn("after_tool_call hook failed: {}", exc.getMessage());
            }
        }

        return new ToolExecResult(toolCall, result, isError);
    }

    /**
     * Internal result tuple for tool execution.
     */
    public record ToolExecResult(
            ToolCallContent toolCall,
            ToolResult result,
            boolean isError
    ) {}
}
