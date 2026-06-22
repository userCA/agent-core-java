package io.agentcore.core;

import io.agentcore.core.Content.TextContent;
import io.agentcore.core.Content.ToolCallContent;
import io.agentcore.core.Message.*;
import io.agentcore.core.AgentEvent.*;
import io.agentcore.extensions.HookTypes.*;
import io.agentcore.providers.ProviderAuth;
import io.agentcore.providers.StreamEvent;
import io.agentcore.providers.StreamEvent.*;
import io.agentcore.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Core agent loop — drives the LLM streaming → tool execution → repeat cycle.
 *
 * <p>Mirrors Python {@code agent_core/core/loop.py} agent_loop.
 * Designed to run on virtual threads (blocking I/O is expected).
 */
public class AgentLoop {

    private static final Logger log = LoggerFactory.getLogger(AgentLoop.class);

    private final AgentLoopConfig config;
    private final AgentContext context;

    public AgentLoop(AgentLoopConfig config, AgentContext context) {
        this.config = config;
        this.context = context;
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

        List<Message> newAssistantMessages = new ArrayList<>();
        int turnCount = 0;

        while (true) {
            if (isAborted(signal)) {
                log.debug("Agent loop aborted by signal");
                break;
            }
            if (config.maxTurns() != null && turnCount >= config.maxTurns()) {
                log.debug("Max turns ({}) reached", config.maxTurns());
                break;
            }

            onEvent.accept(new TurnStart());
            turnCount++;
            log.debug("Turn {} starting", turnCount);

            // Prepare LLM call context
            List<Map<String, Object>> llmMessages = prepareLlmMessages(signal);
            var auth = config.authResolver().resolve(config.model().provider());
            List<Map<String, Object>> toolDefs = getToolDefs();

            // Execute LLM call with retry logic
            AssistantMessage assistant = executeLlmWithRetry(llmMessages, toolDefs, auth, signal, onEvent);
            
            onEvent.accept(new MessageEnd(assistant));
            context.messages().add(assistant);
            newAssistantMessages.add(assistant);

            // Execute tools if present
            List<ToolResultMessage> toolResultMessages = executeTools(assistant, signal, onEvent);
            onEvent.accept(new TurnEnd(assistant, new ArrayList<>(toolResultMessages)));

            // Check termination conditions
            if (shouldTerminateLoop(assistant, toolResultMessages)) {
                break;
            }

            // Check for steering/follow-up messages
            if (drainAndEmitMessages(config.getSteeringMessages(), onEvent)) {
                log.debug("Steering messages injected, continuing loop");
                continue;
            }
            if (drainAndEmitMessages(config.getFollowUpMessages(), onEvent)) {
                log.debug("Follow-up messages injected, continuing loop");
                continue;
            }

            break;
        }

        log.debug("Agent loop completed after {} turns", turnCount);
        onEvent.accept(new AgentEnd(newAssistantMessages));
    }

    // ── Helper Methods ──────────────────────────────────────────────

    /**
     * Add messages to context and emit events.
     */
    private void addMessagesToContext(List<Message> messages, Consumer<AgentEvent> onEvent) {
        if (messages == null) return;
        for (Message msg : messages) {
            context.messages().add(msg);
            onEvent.accept(new MessageStart(msg));
            onEvent.accept(new MessageEnd(msg));
        }
    }

    /**
     * Check if the loop should be aborted.
     */
    private boolean isAborted(AtomicBoolean signal) {
        return signal != null && signal.get();
    }

    /**
     * Prepare LLM messages from context.
     */
    private List<Map<String, Object>> prepareLlmMessages(AtomicBoolean signal) {
        List<Map<String, Object>> llmMessages = config.convertToLlm().convert(context.messages());
        if (config.transformContext() != null) {
            llmMessages = config.transformContext().transform(llmMessages, signal);
        }
        return llmMessages;
    }

    /**
     * Execute LLM call with retry and backoff logic.
     */
    private AssistantMessage executeLlmWithRetry(
            List<Map<String, Object>> llmMessages,
            List<Map<String, Object>> toolDefs,
            ProviderAuth auth,
            AtomicBoolean signal,
            Consumer<AgentEvent> onEvent) {
        
        int maxRetries = config.maxRetries();
        AssistantMessage assistant = null;
        int retryCount = 0;

        while (true) {
            var builder = AssistantMessage.builder()
                    .provider(config.model().provider())
                    .model(config.model().id());

            if (retryCount == 0) {
                // First attempt: stream in real-time
                var tempAssistant = new AssistantMessageRef(builder, onEvent);
                streamAssistant(llmMessages, toolDefs, auth, signal, tempAssistant);
                assistant = builder.build();

                // Emit streaming updates
                for (AgentEvent evt : tempAssistant.collectedEvents) {
                    onEvent.accept(evt);
                }
            } else {
                // Retry: buffer events
                log.info("Retrying LLM call (attempt {}/{})", retryCount + 1, maxRetries + 1);
                var tempAssistant = new AssistantMessageRef(builder, null);
                streamAssistant(llmMessages, toolDefs, auth, signal, tempAssistant);
                assistant = builder.build();

                onEvent.accept(new MessageStart(assistant));
                for (AgentEvent evt : tempAssistant.collectedEvents) {
                    onEvent.accept(evt);
                }
            }

            // Check for retry conditions
            RetryDecision decision = evaluateRetry(assistant, retryCount, maxRetries, llmMessages, signal);
            
            if (decision.shouldRetry()) {
                retryCount = decision.newRetryCount();
                llmMessages = decision.updatedMessages();
                if (decision.delaySeconds() > 0) {
                    sleepWithInterruptCheck(decision.delaySeconds(), signal);
                }
                continue;
            }
            
            break;
        }

        return assistant;
    }

    /**
     * Evaluate whether to retry and compute delay.
     */
    private RetryDecision evaluateRetry(
            AssistantMessage assistant,
            int retryCount,
            int maxRetries,
            List<Map<String, Object>> llmMessages,
            AtomicBoolean signal) {
        
        // Retryable error with exponential backoff
        if (assistant.stopReason() == StopReason.ERROR
                && assistant.retryableError()
                && retryCount < maxRetries) {
            double delay = calculateBackoffDelay(retryCount);
            log.debug("Retryable error detected, backing off for {:.2f}s", delay);
            return new RetryDecision(true, retryCount + 1, delay, llmMessages);
        }
        
        // Overflow error - trigger compaction
        if (assistant.overflowError()
                && config.compactCallback() != null
                && retryCount < maxRetries) {
            log.info("Context overflow detected, triggering compaction");
            boolean compacted = config.compactCallback().compact(context.messages());
            if (compacted) {
                List<Map<String, Object>> newMessages = prepareLlmMessages(signal);
                return new RetryDecision(true, retryCount + 1, 0, newMessages);
            }
        }
        
        return new RetryDecision(false, retryCount, 0, llmMessages);
    }

    /**
     * Calculate exponential backoff delay with jitter.
     */
    private double calculateBackoffDelay(int retryCount) {
        double delay = Math.min(
                config.retryBaseDelay() * Math.pow(2, retryCount),
                config.retryMaxDelay());
        return delay * (0.5 + ThreadLocalRandom.current().nextDouble());
    }

    /**
     * Sleep with interrupt check, propagating interrupt to signal if present.
     */
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

    /**
     * Execute tools from assistant message.
     */
    private List<ToolResultMessage> executeTools(
            AssistantMessage assistant,
            AtomicBoolean signal,
            Consumer<AgentEvent> onEvent) {
        
        if (!assistant.hasToolCalls() || config.toolRegistry() == null) {
            return List.of();
        }

        var registry = config.toolRegistry();
        var runner = new ToolRunner(registry, config.toolTimeout(),
                config.beforeToolCall(), config.afterToolCall());

        log.debug("Executing {} tool calls in {} mode", 
                assistant.toolCalls().size(), config.toolExecution());

        List<ToolResultMessage> results;
        if (config.toolExecution() == AgentLoopConfig.ToolExecutionMode.PARALLEL) {
            results = runner.executeParallel(assistant, signal, onEvent);
        } else {
            results = runner.executeSequential(assistant, signal, onEvent);
        }

        // Add tool results to context
        for (ToolResultMessage trm : results) {
            context.messages().add(trm);
        }

        return results;
    }

    /**
     * Check if loop should terminate based on assistant state.
     */
    private boolean shouldTerminateLoop(AssistantMessage assistant, List<ToolResultMessage> toolResults) {
        // Stop on error/abort
        if (assistant.stopReason() == StopReason.ERROR || assistant.stopReason() == StopReason.ABORTED) {
            log.debug("Loop terminating due to stop reason: {}", assistant.stopReason());
            return true;
        }
        
        // Continue if there were tool calls
        if (!toolResults.isEmpty()) {
            return false;
        }
        
        // No tool calls and no error - normal termination
        return true;
    }

    /**
     * Drain and emit messages from a drainer, returning true if messages were injected.
     */
    private boolean drainAndEmitMessages(AgentLoopConfig.MessageDrainer drainer, Consumer<AgentEvent> onEvent) {
        if (drainer == null) return false;
        
        List<Message> messages = drainer.drain();
        if (messages == null || messages.isEmpty()) return false;
        
        addMessagesToContext(messages, onEvent);
        return true;
    }

    /**
     * Get tool definitions in provider format.
     */
    private List<Map<String, Object>> getToolDefs() {
        if (config.toolRegistry() != null) {
            return config.toolRegistry().toProviderFormat();
        }
        return List.of();
    }

    /**
     * Retry decision record.
     */
    private record RetryDecision(
            boolean shouldRetry,
            int newRetryCount,
            double delaySeconds,
            List<Map<String, Object>> updatedMessages
    ) {}

    /**
     * Stream LLM response and accumulate into assistant message builder.
     * Optimized to avoid building intermediate AssistantMessage objects on every delta.
     */
    private void streamAssistant(
            List<Map<String, Object>> llmMessages,
            List<Map<String, Object>> toolDefs,
            ProviderAuth auth,
            AtomicBoolean signal,
            AssistantMessageRef ref) {

        StringBuilder textBuf = new StringBuilder();
        Map<String, Map<String, Object>> toolBuffers = new LinkedHashMap<>();
        String errorMessage = null;

        Iterator<StreamEvent> stream = config.streamFn().stream(
                config.model(), llmMessages, toolDefs,
                context.systemPrompt(),
                config.thinkingLevel(), config.temperature(), config.maxTokens(),
                signal, auth);

        while (stream.hasNext()) {
            StreamEvent evt = stream.next();
            switch (evt) {
                case StreamTextDelta td -> {
                    textBuf.append(td.text());
                    if (ref.eventConsumer != null) {
                        ref.collectedEvents.add(new MessageUpdate(
                                ref.builder.build(),
                                new AgentEvent.MessageDelta.TextDelta(td.text())));
                    }
                }
                case StreamThinkingDelta thd -> {
                    if (ref.eventConsumer != null) {
                        ref.collectedEvents.add(new MessageUpdate(
                                ref.builder.build(),
                                new AgentEvent.MessageDelta.ThinkingDelta(thd.text())));
                    }
                }
                case StreamToolCallStart tcs -> {
                    Map<String, Object> slot = new LinkedHashMap<>();
                    slot.put("id", tcs.id());
                    slot.put("name", tcs.name());
                    slot.put("args", Map.of());
                    toolBuffers.put(tcs.id(), slot);
                    if (ref.eventConsumer != null) {
                        ref.collectedEvents.add(new MessageUpdate(
                                ref.builder.build(),
                                new AgentEvent.MessageDelta.ToolCallDelta(tcs.id(), tcs.name(), null)));
                    }
                }
                case StreamToolCallEnd tce -> {
                    Map<String, Object> slot = toolBuffers.get(tce.id());
                    if (slot != null) {
                        slot.put("args", tce.arguments());
                    }
                }
                case StreamMessageEnd sme -> {
                    ref.builder.usage(new Usage(sme.inputTokens(), sme.outputTokens(), 0, 0));
                    ref.builder.stopReason(StopReason.fromValue(sme.stopReason()));
                }
                case StreamError se -> {
                    errorMessage = se.message();
                    ref.builder.stopReason(StopReason.ERROR);
                    if (se.retryable()) ref.builder.retryableError(true);
                    if (se.overflow()) ref.builder.overflowError(true);
                }
                default -> {}
            }
        }

        // Accumulate text and tool calls into builder
        if (!textBuf.isEmpty()) {
            ref.builder.addContent(new TextContent(textBuf.toString()));
        }
        for (Map<String, Object> slot : toolBuffers.values()) {
            String id = (String) slot.get("id");
            String name = (String) slot.get("name");
            @SuppressWarnings("unchecked")
            Map<String, Object> args = (Map<String, Object>) slot.getOrDefault("args", Map.of());
            ref.builder.addContent(new ToolCallContent(id, name != null ? name : "", args));
        }
        if (errorMessage != null) {
            ref.builder.errorMessage(errorMessage);
        }
    }

    /**
     * Mutable reference holder for assistant message construction during streaming.
     */
    private static class AssistantMessageRef {
        final AssistantMessage.Builder builder;
        final Consumer<AgentEvent> eventConsumer;
        final List<AgentEvent> collectedEvents = new ArrayList<>();

        AssistantMessageRef(AssistantMessage.Builder builder, Consumer<AgentEvent> eventConsumer) {
            this.builder = builder;
            this.eventConsumer = eventConsumer;
        }
    }
}
