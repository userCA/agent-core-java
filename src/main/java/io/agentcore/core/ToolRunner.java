package io.agentcore.core;

import io.agentcore.core.Content.TextContent;
import io.agentcore.core.Content.ToolCallContent;
import io.agentcore.core.Message.AssistantMessage;
import io.agentcore.core.Message.ToolResultMessage;
import io.agentcore.tools.Tool;
import io.agentcore.tools.ToolContext;
import io.agentcore.tools.ToolRegistry;
import io.agentcore.tools.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Tool execution engine — dispatches tool calls, manages hooks and error handling.
 *
 * <p>Mirrors Python {@code agent_core/core/tool_runner.py}.
 * Supports sequential and parallel execution modes.
 */
public class ToolRunner {

    private static final Logger log = LoggerFactory.getLogger(ToolRunner.class);

    /**
     * Result of executing one tool call.
     */
    public record ToolCallResult(
            String toolCallId,
            String toolName,
            ToolResult result,
            boolean isError
    ) {}

    private final ToolRegistry registry;
    private final Double defaultTimeout;

    // Hooks
    private Function<Map<String, Object>, Map<String, Object>> beforeToolCall;
    private Function<Map<String, Object>, Map<String, Object>> afterToolCall;

    public ToolRunner(ToolRegistry registry, Double defaultTimeout) {
        this.registry = registry;
        this.defaultTimeout = defaultTimeout;
    }

    public ToolRunner(ToolRegistry registry) {
        this(registry, null);
    }

    public void setBeforeToolCall(Function<Map<String, Object>, Map<String, Object>> hook) {
        this.beforeToolCall = hook;
    }

    public void setAfterToolCall(Function<Map<String, Object>, Map<String, Object>> hook) {
        this.afterToolCall = hook;
    }

    /**
     * Execute all tool calls from an assistant message sequentially.
     *
     * @param assistant  the assistant message containing tool calls
     * @param signal     abort signal (nullable)
     * @param onEvent    callback for emitting events (ToolExecutionStart, End, etc.)
     * @return list of tool result messages
     */
    public List<ToolResultMessage> executeSequential(
            AssistantMessage assistant,
            AtomicBoolean signal,
            Consumer<AgentEvent> onEvent) {

        List<ToolCallContent> calls = assistant.toolCalls();
        if (calls.isEmpty()) return List.of();

        List<ToolResultMessage> results = new ArrayList<>();

        for (ToolCallContent tc : calls) {
            // Emit start event
            if (onEvent != null) {
                onEvent.accept(new AgentEvent.ToolExecutionStart(
                        tc.id(), tc.name(), tc.arguments()));
            }

            ToolCallResult callResult = runSingleTool(tc, signal, null);

            // Emit end event
            if (onEvent != null) {
                onEvent.accept(new AgentEvent.ToolExecutionEnd(
                        tc.id(), tc.name(), callResult.result(), callResult.isError()));
            }

            // Create tool result message
            ToolResultMessage msg = new ToolResultMessage(
                    tc.id(),
                    tc.name(),
                    callResult.result().content(),
                    callResult.isError(),
                    System.currentTimeMillis() / 1000.0
            );
            results.add(msg);
        }

        return results;
    }

    /**
     * Execute all tool calls in parallel using virtual threads.
     *
     * @param assistant  the assistant message containing tool calls
     * @param signal     abort signal (nullable)
     * @param onEvent    callback for emitting events
     * @return list of tool result messages
     */
    public List<ToolResultMessage> executeParallel(
            AssistantMessage assistant,
            AtomicBoolean signal,
            Consumer<AgentEvent> onEvent) {

        List<ToolCallContent> calls = assistant.toolCalls();
        if (calls.isEmpty()) return List.of();

        // Emit all start events first
        if (onEvent != null) {
            for (ToolCallContent tc : calls) {
                onEvent.accept(new AgentEvent.ToolExecutionStart(
                        tc.id(), tc.name(), tc.arguments()));
            }
        }

        // Run in parallel using virtual threads
        @SuppressWarnings("unchecked")
        ToolCallResult[] results = new ToolCallResult[calls.size()];
        Thread[] threads = new Thread[calls.size()];

        for (int i = 0; i < calls.size(); i++) {
            final int idx = i;
            final ToolCallContent tc = calls.get(i);
            threads[i] = Thread.ofVirtual().name("tool-" + tc.name() + "-" + idx).start(() -> {
                results[idx] = runSingleTool(tc, signal, null);
            });
        }

        // Wait for all threads
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Emit end events and build messages
        List<ToolResultMessage> messages = new ArrayList<>();
        for (int i = 0; i < calls.size(); i++) {
            ToolCallContent tc = calls.get(i);
            ToolCallResult cr = results[i];
            if (cr == null) {
                cr = new ToolCallResult(tc.id(), tc.name(),
                        new ToolResult("Tool execution interrupted"), true);
            }

            if (onEvent != null) {
                onEvent.accept(new AgentEvent.ToolExecutionEnd(
                        tc.id(), tc.name(), cr.result(), cr.isError()));
            }

            messages.add(new ToolResultMessage(
                    tc.id(), tc.name(), cr.result().content(), cr.isError(),
                    System.currentTimeMillis() / 1000.0));
        }

        return messages;
    }

    // ── Single tool execution ────────────────────────────────

    private ToolCallResult runSingleTool(
            ToolCallContent tc,
            AtomicBoolean signal,
            Consumer<ToolResult> onUpdate) {

        Tool tool = registry.get(tc.name());
        if (tool == null) {
            return new ToolCallResult(tc.id(), tc.name(),
                    new ToolResult("Tool '" + tc.name() + "' not found."), true);
        }

        // Before hook
        Map<String, Object> args = tc.arguments();
        if (beforeToolCall != null) {
            try {
                Map<String, Object> hookResult = beforeToolCall.apply(
                        Map.of("tool_call", tc, "args", args));
                if (hookResult != null) {
                    if (Boolean.TRUE.equals(hookResult.get("block"))) {
                        String reason = (String) hookResult.getOrDefault("reason", "Blocked by hook.");
                        return new ToolCallResult(tc.id(), tc.name(),
                                new ToolResult(reason), true);
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> mutated = (Map<String, Object>) hookResult.get("mutated_args");
                    if (mutated != null) {
                        args = mutated;
                    }
                }
            } catch (Exception e) {
                log.warn("Before-tool-call hook failure", e);
            }
        }

        // Determine effective timeout
        Double effectiveTimeout = tool.definition().timeoutSeconds();
        if (effectiveTimeout == null) effectiveTimeout = defaultTimeout;

        // Execute with optional timeout enforcement
        ToolContext ctx = new ToolContext(signal, onUpdate, Map.of(), null);
        ToolResult result;
        boolean isError;

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

        // After hook
        if (afterToolCall != null) {
            try {
                afterToolCall.apply(Map.of(
                        "tool_call", tc, "args", args,
                        "result", result, "is_error", isError));
            } catch (Exception e) {
                log.warn("After-tool-call hook failure", e);
            }
        }

        return new ToolCallResult(tc.id(), tc.name(), result, isError);
    }

    /**
     * Execute a tool with a timeout using a virtual thread + Future.
     */
    private ToolResult executeWithTimeout(Tool tool, String callId,
                                           Map<String, Object> args, ToolContext ctx,
                                           double timeoutSeconds) throws Exception {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        Future<ToolResult> future = null;
        try {
            future = executor.submit(() -> tool.execute(callId, args, ctx));
            return future.get((long) (timeoutSeconds * 1000), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            if (future != null) future.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            if (future != null) future.cancel(true);
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) throw ex;
            throw new RuntimeException(cause);
        } catch (InterruptedException e) {
            if (future != null) future.cancel(true);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Tool execution interrupted", e);
        } finally {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    log.warn("Tool executor did not terminate within 2s for call {}", callId);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
