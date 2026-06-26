package io.agentcore.agent;

import io.agentcore.model.Content.TextContent;
import io.agentcore.model.Content.ToolCallContent;
import io.agentcore.model.Message.AssistantMessage;
import io.agentcore.model.Message.Usage;
import io.agentcore.model.Message.StopReason;
import io.agentcore.llm.StreamEvent;
import io.agentcore.llm.StreamEvent.*;
import io.agentcore.model.AgentEvent.MessageDelta.*;
import io.agentcore.agent.AgentLoopConfig.LlmStreamProvider;
import io.agentcore.llm.ModelInfo;
import io.agentcore.llm.ProviderAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import io.agentcore.model.AgentEvent;
import io.agentcore.model.Message;

/**
 * Accumulates LLM stream events into an AssistantMessage.
 *
 * <p>Extracted from AgentLoop to isolate stream protocol parsing
 * (SRP) and enable independent unit testing.
 */
public final class StreamAccumulator {

    private static final Logger log = LoggerFactory.getLogger(StreamAccumulator.class);

    /**
     * Result of stream accumulation.
     */
    public record Result(
            AssistantMessage message
    ) {}

    /**
     * Type-safe accumulator slot for building tool calls during streaming.
     */
    private static final class ToolCallSlot {
        final String id;
        final String name;
        volatile Map<String, Object> args;

        ToolCallSlot(String id, String name) {
            this.id = Objects.requireNonNull(id, "tool call id");
            this.name = name != null ? name : "";
            this.args = Map.of();
        }

        void setArgs(Map<String, Object> args) {
            this.args = args != null ? args : Map.of();
        }
    }

    private final LlmStreamProvider streamFn;
    private volatile ModelInfo model;
    private volatile String thinkingLevel;
    private volatile Double temperature;
    private final Integer maxTokens;

    public StreamAccumulator(LlmStreamProvider streamFn, ModelInfo model,
                             String thinkingLevel, Double temperature, Integer maxTokens) {
        this.streamFn = streamFn;
        this.model = model;
        this.thinkingLevel = thinkingLevel;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }

    /**
     * Update mutable config parameters for dynamic model/thinking switching.
     * Null values are ignored (keep current).
     */
    public void updateConfig(ModelInfo model, String thinkingLevel, Double temperature) {
        if (model != null) this.model = model;
        if (thinkingLevel != null) this.thinkingLevel = thinkingLevel;
        if (temperature != null) this.temperature = temperature;
    }

    /**
     * Consume stream events and accumulate into an AssistantMessage.
     *
     * <p>Streaming events ({@link AgentEvent.MessageUpdate}) are emitted in real-time
     * through the {@code eventSink} as they arrive from the provider, enabling
     * true streaming for subscribers.
     *
     * @param llmMessages    messages to send to the LLM
     * @param toolDefs       tool definitions in provider format
     * @param auth           provider auth
     * @param systemPrompt   system prompt
     * @param signal         abort signal (nullable)
     * @param eventSink      real-time event sink for streaming updates (nullable)
     * @return accumulated result
     */
    public Result accumulate(
            List<Map<String, Object>> llmMessages,
            List<Map<String, Object>> toolDefs,
            ProviderAuth auth,
            String systemPrompt,
            AtomicBoolean signal,
            Consumer<AgentEvent> eventSink) {

        var builder = AssistantMessage.builder()
                .provider(model.provider())
                .model(model.id());

        StringBuilder textBuf = new StringBuilder();
        Map<String, ToolCallSlot> toolBuffers = new LinkedHashMap<>();
        String errorMessage = null;

        Iterator<StreamEvent> stream = streamFn.stream(
                model, llmMessages, toolDefs, systemPrompt,
                thinkingLevel, temperature, maxTokens, signal, auth);

        // Emit MessageStart BEFORE streaming begins (mirrors Python: yield MessageStart at stream start)
        if (eventSink != null) {
            eventSink.accept(new AgentEvent.MessageStart(
                    builder.buildStreamingSnapshot("")));
        }

        while (stream.hasNext()) {
            StreamEvent evt = stream.next();
            switch (evt) {
                case StreamTextDelta td -> {
                    textBuf.append(td.text());
                    if (eventSink != null) {
                        eventSink.accept(new AgentEvent.MessageUpdate(
                                builder.buildStreamingSnapshot(textBuf.toString()),
                                new TextDelta(td.text())));
                    }
                }
                case StreamThinkingDelta thd -> {
                    if (eventSink != null) {
                        eventSink.accept(new AgentEvent.MessageUpdate(
                                builder.buildStreamingSnapshot(textBuf.toString()),
                                new ThinkingDelta(thd.text())));
                    }
                }
                case StreamToolCallStart tcs -> {
                    toolBuffers.put(tcs.id(), new ToolCallSlot(tcs.id(), tcs.name()));
                    if (eventSink != null) {
                        eventSink.accept(new AgentEvent.MessageUpdate(
                                builder.buildStreamingSnapshot(textBuf.toString()),
                                new ToolCallDelta(tcs.id(), tcs.name(), null)));
                    }
                }
                case StreamToolCallDelta tcd -> {
                    if (eventSink != null) {
                        eventSink.accept(new AgentEvent.MessageUpdate(
                                builder.buildStreamingSnapshot(textBuf.toString()),
                                new ToolCallDelta(tcd.id(), null, tcd.argumentsDelta())));
                    }
                }
                case StreamToolCallEnd tce -> {
                    ToolCallSlot slot = toolBuffers.get(tce.id());
                    if (slot != null) {
                        slot.setArgs(tce.arguments());
                    }
                }
                case StreamMessageEnd sme -> {
                    builder.usage(new Usage(sme.inputTokens(), sme.outputTokens(), 0, 0));
                    builder.stopReason(StopReason.fromValue(sme.stopReason()));
                }
                case StreamError se -> {
                    errorMessage = se.message();
                    builder.stopReason(StopReason.ERROR);
                    if (se.retryable()) builder.retryableError(true);
                    if (se.overflow()) builder.overflowError(true);
                }
                default -> log.trace("Ignoring unknown StreamEvent: {}", evt.getClass().getSimpleName());
            }
        }

        // Accumulate text and tool calls into builder
        if (!textBuf.isEmpty()) {
            builder.addContent(new TextContent(textBuf.toString()));
        }
        for (ToolCallSlot slot : toolBuffers.values()) {
            builder.addContent(new ToolCallContent(slot.id, slot.name != null ? slot.name : "", slot.args));
        }
        if (errorMessage != null) {
            builder.errorMessage(errorMessage);
        }

        return new Result(builder.build());
    }
}
