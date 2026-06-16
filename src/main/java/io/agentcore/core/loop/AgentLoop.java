package io.agentcore.core.loop;

import io.agentcore.core.content.TextContent;
import io.agentcore.core.content.ToolCallContent;
import io.agentcore.core.context.AgentContext;
import io.agentcore.core.context.AgentLoopConfig;
import io.agentcore.core.events.*;
import io.agentcore.core.messages.*;
import io.agentcore.core.toolrunner.ToolRunner;
import io.agentcore.providers.base.ModelProvider;
import io.agentcore.providers.base.StreamRequest;
import io.agentcore.providers.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Pure agent loop — replicates the Python agent_loop async generator.
 * Runs on a Virtual Thread, emits events via Consumer callback.
 *
 * Control flow:
 * 1. emit AgentStart → emit user messages
 * 2. Turn loop: check abort/maxTurns → convert messages → resolve auth → stream LLM
 * 3. Retry loop: first attempt streams real-time, retries buffer + replay
 * 4. Tool execution via ToolRunner
 * 5. Loop continuation: tool results / steering / follow-up
 * 6. emit AgentEnd
 */
public final class AgentLoop {
    private static final Logger log = LoggerFactory.getLogger(AgentLoop.class);

    private AgentLoop() {}

    /**
     * Run the agent loop synchronously (intended to be called on a Virtual Thread).
     * Events are emitted via the {@code emit} callback.
     */
    public static void run(
            List<AgentMessage> newMessages,
            AgentContext context,
            AgentLoopConfig config,
            AtomicBoolean signal,
            Consumer<AgentEvent> emit
    ) {
        emit.accept(new AgentStart());

        // Append new messages and emit them
        context.messages().addAll(newMessages);
        for (var msg : newMessages) {
            emit.accept(new MessageStart(msg));
            emit.accept(new MessageEnd(msg));
        }

        List<AssistantMessage> newAssistantMessages = new ArrayList<>();
        int turnCount = 0;

        while (true) {
            // Exit conditions
            if (signal != null && signal.get()) break;
            if (config.maxTurns() != null && turnCount >= config.maxTurns()) break;

            emit.accept(new TurnStart());
            turnCount++;

            // Convert messages to LLM format
            List<Map<String, Object>> llmMessages;
            try {
                llmMessages = config.convertToLlm().convert(context.messages()).get();
            } catch (Exception e) {
                log.error("convert_to_llm failed: {}", e.getMessage());
                break;
            }

            // Optional transform
            if (config.transformContext() != null) {
                try {
                    llmMessages = config.transformContext().apply(llmMessages, signal).get();
                } catch (Exception e) {
                    log.warn("transform_context failed: {}", e.getMessage());
                }
            }

            // Resolve auth
            ProviderAuth auth;
            try {
                auth = config.authResolver().apply(config.model().provider()).get();
            } catch (Exception e) {
                log.error("auth_resolver failed: {}", e.getMessage());
                break;
            }

            // Convert tool definitions using provider-specific format
            List<Map<String, Object>> toolDefs;
            if (config.toolFormatConverter() != null) {
                toolDefs = config.toolFormatConverter().apply(context.tools());
            } else {
                // Default OpenAI format
                toolDefs = defaultToolsFormat(context.tools());
            }

            // === Retry Loop ===
            AssistantMessage.MutableAssistant assistant = null;
            int retryCount = 0;

            while (true) {
                assistant = new AssistantMessage.MutableAssistant(new AssistantMessage(
                        new ArrayList<>(), new Usage(), StopReason.STOP, null,
                        false, false, config.model().provider(), config.model().id(),
                        System.currentTimeMillis() / 1000.0
                ));

                if (retryCount == 0) {
                    // First attempt: stream in real-time
                    emit.accept(new MessageStart(assistant.toRecord()));
                    streamAssistant(config, llmMessages, toolDefs, auth, signal,
                            context.systemPrompt(), assistant, u -> emit.accept(u));
                } else {
                    // Retry: buffer events, then replay
                    List<MessageUpdate> buffered = new ArrayList<>();
                    streamAssistant(config, llmMessages, toolDefs, auth, signal,
                            context.systemPrompt(), assistant, buffered::add);
                    emit.accept(new MessageStart(assistant.toRecord()));
                    for (var upd : buffered) {
                        emit.accept(upd);
                    }
                }

                boolean shouldRetry = false;
                AssistantMessage finalAssistant = assistant.toRecord();

                if (finalAssistant.stopReason() == StopReason.ERROR
                        && finalAssistant.retryableError()
                        && retryCount < config.maxRetries()) {
                    shouldRetry = true;
                    retryCount++;
                    double delay = Math.min(
                            config.retryBaseDelay() * Math.pow(2, retryCount - 1),
                            config.retryMaxDelay());
                    delay = delay * (0.5 + ThreadLocalRandom.current().nextDouble());
                    log.warn("Retryable error (attempt {}/{}), retrying in {}s: {}",
                            retryCount, config.maxRetries(), String.format("%.1f", delay), finalAssistant.errorMessage());
                    sleepMillis((long) (delay * 1000));

                } else if (finalAssistant.overflowError()
                        && config.compactCallback() != null
                        && retryCount < config.maxRetries()) {
                    log.warn("Context overflow (attempt {}/{}), triggering compaction",
                            retryCount + 1, config.maxRetries());
                    try {
                        Boolean compacted = config.compactCallback().compact(context.messages()).get();
                        if (Boolean.TRUE.equals(compacted)) {
                            retryCount++;
                            llmMessages = config.convertToLlm().convert(context.messages()).get();
                            if (config.transformContext() != null) {
                                llmMessages = config.transformContext().apply(llmMessages, signal).get();
                            }
                            shouldRetry = true;
                        } else {
                            log.warn("Compaction callback returned false, cannot retry overflow");
                        }
                    } catch (Exception e) {
                        log.warn("Compaction failed: {}", e.getMessage());
                    }
                }

                if (shouldRetry) continue;
                break;
            }

            // Emit MessageEnd
            AssistantMessage finalAssistant = assistant.toRecord();
            emit.accept(new MessageEnd(finalAssistant));
            context.messages().add(finalAssistant);
            newAssistantMessages.add(finalAssistant);

            // === Tool Execution ===
            List<ToolResultMessage> toolResultMessages = new ArrayList<>();
            if (finalAssistant.hasToolCalls() && config.toolRegistry() != null) {
                ToolRunner.executeTools(
                        finalAssistant.toolCalls(),
                        config, context, signal,
                        toolResultMessages,
                        config.humanInputGate(),
                        config.mutationQueue(),
                        emit
                );
            }

            emit.accept(new TurnEnd(finalAssistant, toolResultMessages));

            // === Loop continuation ===
            StopReason stopReason = finalAssistant.stopReason();
            if (stopReason == StopReason.ERROR || stopReason == StopReason.ABORTED) break;
            if (!toolResultMessages.isEmpty()) continue;

            // Check steering messages
            if (config.getSteeringMessages() != null) {
                try {
                    List<AgentMessage> steering = config.getSteeringMessages().drain().get();
                    if (!steering.isEmpty()) {
                        for (var msg : steering) {
                            context.messages().add(msg);
                            emit.accept(new MessageStart(msg));
                            emit.accept(new MessageEnd(msg));
                        }
                        continue;
                    }
                } catch (Exception e) {
                    log.warn("Steering drain failed: {}", e.getMessage());
                }
            }

            // Check follow-up messages
            if (config.getFollowUpMessages() != null) {
                try {
                    List<AgentMessage> followUps = config.getFollowUpMessages().drain().get();
                    if (!followUps.isEmpty()) {
                        for (var msg : followUps) {
                            context.messages().add(msg);
                            emit.accept(new MessageStart(msg));
                            emit.accept(new MessageEnd(msg));
                        }
                        continue;
                    }
                } catch (Exception e) {
                    log.warn("Follow-up drain failed: {}", e.getMessage());
                }
            }

            break;
        }

        emit.accept(new AgentEnd(new ArrayList<>(newAssistantMessages)));
    }

    /**
     * Stream LLM response, accumulating text and tool calls into the assistant message,
     * and emitting MessageUpdate events.
     */
    private static void streamAssistant(
            AgentLoopConfig config,
            List<Map<String, Object>> llmMessages,
            List<Map<String, Object>> toolDefs,
            ProviderAuth auth,
            AtomicBoolean signal,
            String systemPrompt,
            AssistantMessage.MutableAssistant assistant,
            Consumer<MessageUpdate> emit
    ) {
        StringBuilder textBuf = new StringBuilder();
        Map<String, ToolCallBuffer> toolBuffers = new LinkedHashMap<>();

        // Build stream request
        StreamRequest request = new StreamRequest(
                config.model(), llmMessages, toolDefs, systemPrompt,
                config.thinkingLevel().getValue(), config.temperature(),
                config.maxTokens(), signal, auth
        );

        // Subscribe to the publisher and collect events via blocking queue
        // Queue holds both StreamEvent and Sentinel (Object to support heterogeneous items)
        BlockingQueue<Object> queue = new LinkedBlockingQueue<>();
        final Flow.Subscription[] subscriptionHolder = new Flow.Subscription[1];
        Flow.Publisher<StreamEvent> publisher = config.streamFn().apply(request);
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription s) {
                subscriptionHolder[0] = s;
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(StreamEvent item) {
                queue.offer(item);
            }

            @Override
            public void onError(Throwable throwable) {
                queue.offer(new StreamEvent.StreamError(throwable.getMessage(), true, false));
                queue.offer(new Sentinel());
            }

            @Override
            public void onComplete() {
                queue.offer(new Sentinel());
            }
        });

        // Process stream events
        try {
        while (true) {
            Object raw;
            try {
                raw = queue.poll(60, TimeUnit.SECONDS);
                if (raw == null) {
                    // Timeout - log warning and set error state
                    log.warn("Stream timeout after 60 seconds, no events received");
                    assistant.errorMessage("Stream timeout: no response from provider within 60 seconds");
                    assistant.stopReason(StopReason.ERROR);
                    assistant.retryableError(true);
                    break;
                }
                if (raw instanceof Sentinel) break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Stream interrupted");
                assistant.errorMessage("Stream was interrupted");
                assistant.stopReason(StopReason.ABORTED);
                break;
            }

            if (!(raw instanceof StreamEvent evt)) continue;

            if (evt instanceof StreamEvent.StreamTextDelta td) {
                textBuf.append(td.text());
                emit.accept(new MessageUpdate(assistant.toRecord(), new TextDelta(td.text())));

            } else if (evt instanceof StreamEvent.StreamThinkingDelta thd) {
                emit.accept(new MessageUpdate(assistant.toRecord(), new ThinkingDelta(thd.text())));

            } else if (evt instanceof StreamEvent.StreamToolCallStart tcs) {
                toolBuffers.put(tcs.id(), new ToolCallBuffer(tcs.id(), tcs.name(), new StringBuilder()));
                emit.accept(new MessageUpdate(assistant.toRecord(), new ToolCallDelta(tcs.id(), tcs.name(), null)));

            } else if (evt instanceof StreamEvent.StreamToolCallDelta tcd) {
                emit.accept(new MessageUpdate(assistant.toRecord(),
                        new ToolCallDelta(tcd.id(), null, tcd.argumentsDelta())));

            } else if (evt instanceof StreamEvent.StreamToolCallEnd tce) {
                var slot = toolBuffers.computeIfAbsent(tce.id(),
                        id -> new ToolCallBuffer(id, "", new StringBuilder()));
                slot.argsJson(tce.arguments());

            } else if (evt instanceof StreamEvent.StreamMessageEnd sme) {
                assistant.usage(new Usage(sme.inputTokens(), sme.outputTokens(), 0, 0));
                String stop = sme.stopReason();
                if ("tool_calls".equals(stop) || "tool_use".equals(stop)) {
                    assistant.stopReason(StopReason.TOOL_USE);
                } else if ("stop".equals(stop) || "end_turn".equals(stop)) {
                    assistant.stopReason(StopReason.STOP);
                } else if ("length".equals(stop)) {
                    assistant.stopReason(StopReason.LENGTH);
                } else {
                    assistant.stopReason(StopReason.STOP);
                }

            } else if (evt instanceof StreamEvent.StreamError se) {
                assistant.errorMessage(se.message());
                assistant.stopReason(StopReason.ERROR);
                if (se.retryable()) assistant.retryableError(true);
                if (se.overflow()) assistant.overflowError(true);
            }
        }
        } finally {
            // Always cancel the subscription to release HTTP/SSE connections
            Flow.Subscription sub = subscriptionHolder[0];
            if (sub != null) {
                sub.cancel();
            }
        }

        // Materialize accumulated content
        List<io.agentcore.core.content.Content> content = new ArrayList<>();
        if (!textBuf.isEmpty()) {
            content.add(new TextContent(textBuf.toString()));
        }
        for (var slot : toolBuffers.values()) {
            Map<String, Object> args = slot.argsJson();
            if (args == null || args.isEmpty()) {
                // Try to parse from accumulated string
                args = Map.of();
            }
            content.add(new ToolCallContent(slot.id(), slot.name() != null ? slot.name() : "", args));
        }
        assistant.content(content);
    }

    private static void sleepMillis(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Default tool format conversion (OpenAI function-calling schema).
     */
    private static List<Map<String, Object>> defaultToolsFormat(List<io.agentcore.tools.base.ToolDefinition> tools) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (var tool : tools) {
            Map<String, Object> fn = new LinkedHashMap<>();
            fn.put("name", tool.name());
            fn.put("description", tool.description());
            fn.put("parameters", tool.parameters());
            result.add(Map.of("type", "function", "function", fn));
        }
        return result;
    }

    /**
     * Sentinel value to signal stream completion in the blocking queue.
     * Uses a separate interface to avoid polluting the StreamEvent sealed hierarchy.
     */
    private interface StreamQueueItem {}
    private record Sentinel() implements StreamQueueItem {}

    /**
     * Buffer for accumulating tool call arguments during streaming.
     */
    private static class ToolCallBuffer {
        private final String id;
        private final String name;
        private final StringBuilder argsBuilder;
        private Map<String, Object> argsJson;

        ToolCallBuffer(String id, String name, StringBuilder argsBuilder) {
            this.id = id;
            this.name = name;
            this.argsBuilder = argsBuilder;
        }

        String id() { return id; }
        String name() { return name; }

        void argsJson(Map<String, Object> args) { this.argsJson = args; }
        Map<String, Object> argsJson() { return argsJson; }
    }
}
