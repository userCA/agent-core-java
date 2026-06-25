package io.agentcore.agent;

import io.agentcore.model.Message.*;
import io.agentcore.model.AgentEvent.*;
import io.agentcore.llm.ProviderAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import io.agentcore.model.Message;
import io.agentcore.model.AgentEvent;

/**
 * Core agent loop — drives the LLM streaming → tool execution → repeat cycle.
 *
 * <p>Uses a dual-layer loop structure:
 * <ul>
 *   <li>Outer loop: handles follow-up messages after agent would stop</li>
 *   <li>Inner loop: processes tool calls and steering messages</li>
 * </ul>
 *
 * Designed to run on virtual threads (blocking I/O is expected).
 */
public class AgentLoop {

    private static final Logger log = LoggerFactory.getLogger(AgentLoop.class);

    private final AgentLoopConfig config;
    private final AgentContext context;
    private final StreamAccumulator streamAccumulator;
    private final ToolRunner toolRunner;

    public AgentLoop(AgentLoopConfig config, AgentContext context, ToolRunner toolRunner) {
        this(config, context, toolRunner,
                new StreamAccumulator(
                        config.streamFn(), config.model(),
                        config.thinkingLevel().value(), config.temperature(), config.maxTokens()));
    }

    /**
     * Full constructor with shared StreamAccumulator for reuse across runs.
     */
    public AgentLoop(AgentLoopConfig config, AgentContext context,
                     ToolRunner toolRunner, StreamAccumulator streamAccumulator) {
        this.config = config;
        this.context = context;
        this.streamAccumulator = streamAccumulator;
        this.toolRunner = toolRunner;
    }

    /**
     * Convenience constructor that creates a ToolRunner from config (for tests).
     * Production code should use the 3-arg constructor with a shared ToolRunner.
     */
    public AgentLoop(AgentLoopConfig config, AgentContext context) {
        this(config, context, config.toolRegistry() != null
                ? new ToolRunner(config.toolRegistry(), config.toolConfig(),
                        config.beforeToolCall(), config.afterToolCall(),
                        config.humanInputGate())
                : null);
    }

    /**
     * Run the agent loop, yielding AgentEvents through the consumer.
     *
     * @param newMessages  new user messages to process
     * @param signal       abort signal (nullable)
     * @param onEvent      event consumer for streaming events
     */
    public void run(List<Message> newMessages, AtomicBoolean signal, Consumer<AgentEvent> onEvent) {
        log.debug("Agent loop starting");
        onEvent.accept(new AgentStart());

        // Add new messages to context and emit them
        addMessagesToContext(newMessages, onEvent);

        // Track ALL new messages produced in this run (user + assistant + tool results)
        List<Message> newMessagesProduced = new ArrayList<>();
        if (newMessages != null) newMessagesProduced.addAll(newMessages);

        int turnCount = 0;
        List<Message> pendingMessages = drainMessages(config.getSteeringMessages());

        // Outer loop: continues when follow-up messages arrive after agent would stop
        while (true) {
            boolean hasMoreToolCalls = true;

            // Inner loop: process tool calls and steering messages
            while (hasMoreToolCalls || !pendingMessages.isEmpty()) {
                if (isAborted(signal)) {
                    log.debug("Agent loop aborted by signal");
                    onEvent.accept(new AgentEnd(newMessagesProduced));
                    return;
                }
                if (config.maxTurns() != null && turnCount >= config.maxTurns()) {
                    log.debug("Max turns ({}) reached", config.maxTurns());
                    onEvent.accept(new AgentEnd(newMessagesProduced));
                    return;
                }

                // Execute one turn — returns outcome with continue/moreToolCalls flags
                TurnOutcome outcome = executeTurn(
                        signal, onEvent, newMessagesProduced, pendingMessages, turnCount);
                turnCount++;

                if (!outcome.shouldContinue()) {
                    onEvent.accept(new AgentEnd(newMessagesProduced));
                    return;
                }

                hasMoreToolCalls = outcome.hasMoreToolCalls();

                // Check for steering messages for next turn
                pendingMessages = drainMessages(config.getSteeringMessages());
            }

            // Outer loop: check for follow-up messages after agent would stop
            List<Message> followUps = drainMessages(config.getFollowUpMessages());
            if (!followUps.isEmpty()) {
                log.debug("Follow-up messages injected, continuing outer loop");
                pendingMessages = followUps;
                continue;
            }

            break;
        }

        log.debug("Agent loop completed after {} turns", turnCount);
        onEvent.accept(new AgentEnd(newMessagesProduced));
    }

    // ── Turn execution ─────────────────────────────────────────

    /**
     * Outcome of a single turn, returned by executeTurn().
     * Eliminates side-channel state via instance fields.
     */
    private record TurnOutcome(boolean shouldContinue, boolean hasMoreToolCalls) {}

    /**
     * Execute a single turn: LLM call → tool execution → termination checks.
     *
     * @return TurnOutcome indicating whether to continue and if more tool calls exist
     */
    private TurnOutcome executeTurn(
            AtomicBoolean signal,
            Consumer<AgentEvent> onEvent,
            List<Message> newMessagesProduced,
            List<Message> pendingMessages,
            int turnCount) {

        onEvent.accept(new TurnStart());
        log.debug("Turn {} starting", turnCount + 1);

        // Inject pending steering messages before assistant response
        if (!pendingMessages.isEmpty()) {
            addMessagesToContext(pendingMessages, onEvent);
            newMessagesProduced.addAll(pendingMessages);
            pendingMessages.clear();
        }

        // Prepare LLM call context
        List<Map<String, Object>> llmMessages = prepareLlmMessages(signal);
        var auth = config.authResolver().apply(config.model().provider());
        List<Map<String, Object>> toolDefs = getToolDefs();

        // Execute LLM call with retry logic — streaming events emitted in real-time
        AssistantMessage assistant = executeLlmWithRetry(llmMessages, toolDefs, auth, signal, onEvent);

        // Note: MessageStart is emitted by StreamAccumulator BEFORE streaming begins.
        // MessageEnd is emitted here AFTER the full response is accumulated.
        onEvent.accept(new MessageEnd(assistant));
        context.addMessage(assistant);
        newMessagesProduced.add(assistant);

        // Execute tools if present
        var toolBatch = executeTools(assistant, signal, onEvent);
        List<ToolResultMessage> toolResults = toolBatch.messages();
        for (ToolResultMessage tr : toolResults) {
            newMessagesProduced.add(tr);
        }

        @SuppressWarnings("unchecked") // ToolResultMessage implements Message; list is local & unmodified
        List<Message> toolResultsAsMessages = (List<Message>) (List<? extends Message>) toolResults;
        onEvent.accept(new TurnEnd(assistant, toolResultsAsMessages));

        // Check termination: error or abort
        if (assistant.stopReason() == StopReason.ERROR
                || assistant.stopReason() == StopReason.ABORTED) {
            log.debug("Loop terminating due to stop reason: {}", assistant.stopReason());
            return new TurnOutcome(false, false);
        }

        AgentLoopConfig.TurnContext turnContext = new AgentLoopConfig.TurnContext(
                assistant, toolResults, context.messagesSnapshot(), newMessagesProduced);

        // shouldStopAfterTurn: business-level termination
        if (config.shouldStopAfterTurn() != null
                && config.shouldStopAfterTurn().apply(turnContext)) {
            log.debug("Loop stopped by shouldStopAfterTurn callback");
            return new TurnOutcome(false, false);
        }

        boolean hasMoreToolCalls = !toolResults.isEmpty();

        // Tool batch termination: if all tools request terminate, stop
        if (toolBatch.terminate()) {
            log.debug("Loop terminating due to tool batch terminate flag");
            return new TurnOutcome(false, false);
        }

        return new TurnOutcome(true, hasMoreToolCalls);
    }

    // ── Helper Methods ─────────────────────────────────────────

    private void addMessagesToContext(List<Message> messages, Consumer<AgentEvent> onEvent) {
        if (messages == null) return;
        for (Message msg : messages) {
            context.addMessage(msg);
            onEvent.accept(new MessageStart(msg));
            onEvent.accept(new MessageEnd(msg));
        }
    }

    private boolean isAborted(AtomicBoolean signal) {
        return signal != null && signal.get();
    }

    private List<Map<String, Object>> prepareLlmMessages(AtomicBoolean signal) {
        return config.convertToLlm().convert(context.messagesSnapshot());
    }

    private AssistantMessage executeLlmWithRetry(
            List<Map<String, Object>> llmMessages,
            List<Map<String, Object>> toolDefs,
            ProviderAuth auth,
            AtomicBoolean signal,
            Consumer<AgentEvent> onEvent) {
        
        int maxRetries = config.retryConfig().maxRetries();
        int retryCount = 0;

        while (true) {
            // First attempt streams in real-time; retries suppress streaming
            Consumer<AgentEvent> sink = (retryCount == 0) ? onEvent : null;
            if (retryCount > 0) {
                log.info("Retrying LLM call (attempt {}/{})", retryCount + 1, maxRetries + 1);
            }

            AssistantMessage assistant;
            try {
                var result = streamAccumulator.accumulate(
                        llmMessages, toolDefs, auth,
                        context.systemPrompt(), signal, sink);
                assistant = result.message();
            } catch (Exception e) {
                // If streaming itself threw, synthesize an error message
                log.warn("LLM stream failed on attempt {}", retryCount + 1, e);
                assistant = AssistantMessage.builder()
                        .stopReason(StopReason.ERROR)
                        .errorMessage(e.getMessage())
                        .retryableError(true)
                        .provider(config.model().provider())
                        .model(config.model().id())
                        .build();
            }

            RetryDecision decision = evaluateRetry(assistant, retryCount, maxRetries, llmMessages, signal);
            
            if (decision.shouldRetry()) {
                retryCount = decision.newRetryCount();
                llmMessages = decision.updatedMessages();
                if (decision.delaySeconds() > 0) {
                    sleepWithInterruptCheck(decision.delaySeconds(), signal);
                    // If sleep was interrupted, propagate abort immediately
                    if (isAborted(signal)) {
                        log.debug("Retry loop aborted due to interrupt signal");
                        return assistant;
                    }
                }
                continue;
            }
            
            return assistant;
        }
    }

    private RetryDecision evaluateRetry(
            AssistantMessage assistant,
            int retryCount,
            int maxRetries,
            List<Map<String, Object>> llmMessages,
            AtomicBoolean signal) {
        
        if (assistant.stopReason() == StopReason.ERROR
                && assistant.retryableError()
                && retryCount < maxRetries) {
            double delay = calculateBackoffDelay(retryCount);
            log.debug("Retryable error detected, backing off for {}s", String.format("%.2f", delay));
            return new RetryDecision(true, retryCount + 1, delay, llmMessages);
        }
        
        if (assistant.overflowError()
                && config.compactCallback() != null
                && retryCount < maxRetries) {
            log.info("Context overflow detected, triggering compaction");
            boolean compacted = config.compactCallback().compact(context.messagesSnapshot());
            if (compacted) {
                List<Map<String, Object>> newMessages = prepareLlmMessages(signal);
                return new RetryDecision(true, retryCount + 1, 0, newMessages);
            }
        }
        
        return new RetryDecision(false, retryCount, 0, llmMessages);
    }

    private double calculateBackoffDelay(int retryCount) {
        double delay = Math.min(
                config.retryConfig().baseDelay() * Math.pow(2, retryCount),
                config.retryConfig().maxDelay());
        return delay * (0.5 + ThreadLocalRandom.current().nextDouble());
    }

    private void sleepWithInterruptCheck(double seconds, AtomicBoolean signal) {
        try {
            Thread.sleep((long) (seconds * 1000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (signal != null) {
                signal.set(true);
            }
            log.debug("Sleep interrupted, setting abort signal");
        }
    }

    private ToolRunner.ToolBatchResult executeTools(
            AssistantMessage assistant,
            AtomicBoolean signal,
            Consumer<AgentEvent> onEvent) {
        
        if (!assistant.hasToolCalls() || toolRunner == null) {
            return new ToolRunner.ToolBatchResult(List.of(), false);
        }

        log.debug("Executing {} tool calls in {} mode", 
                assistant.toolCalls().size(), config.toolConfig().execution());

        ToolRunner.ToolBatchResult batch;
        if (config.toolConfig().execution() == AgentLoopConfig.ToolExecutionMode.PARALLEL) {
            batch = toolRunner.executeParallel(assistant, signal, onEvent);
        } else {
            batch = toolRunner.executeSequential(assistant, signal, onEvent);
        }

        context.addMessages(batch.messages());
        for (ToolResultMessage trm : batch.messages()) {
            onEvent.accept(new MessageStart(trm));
            onEvent.accept(new MessageEnd(trm));
        }

        return batch;
    }

    private List<Message> drainMessages(Supplier<List<Message>> supplier) {
        if (supplier == null) return List.of();
        List<Message> messages = supplier.get();
        return (messages == null || messages.isEmpty()) ? List.of() : messages;
    }

    private List<Map<String, Object>> getToolDefs() {
        if (config.toolRegistry() != null) {
            return config.toolRegistry().toProviderFormat();
        }
        return List.of();
    }

    private record RetryDecision(
            boolean shouldRetry,
            int newRetryCount,
            double delaySeconds,
            List<Map<String, Object>> updatedMessages
    ) {}
}
