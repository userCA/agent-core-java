package io.agentcore.v2.agent;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.middleware.MiddlewareBase;

import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.GenerateOptions;
import io.agentcore.tools.base.Tool;
import io.agentcore.v2.tools.AgentScopeToolAdapter;

import java.util.ArrayList;
import java.util.List;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Claude Code-compatible agent built on AgentScope Java's ReActAgent.
 *
 * <p>Composes a {@link ReActAgent} with Claude Code-specific middlewares:
 * <ul>
 *   <li><b>Turn lifecycle:</b> AgentStart → TurnStart → ModelCall → ToolExecution →
 *       TurnEnd → ... → AgentEnd</li>
 *   <li><b>Retry with backoff:</b> exponential backoff on retryable errors</li>
 *   <li><b>Context compaction:</b> automatic on overflow detection</li>
 *   <li><b>Steering queues:</b> mid-conversation message injection</li>
 * </ul>
 *
 * <p>Phase 1 provides the skeleton with basic prompt/stream delegation.
 * Phase 2 adds the full middleware chain for Claude Code turn semantics.
 */
public class ClaudeCodeAgent {

    private final ReActAgent delegate;
    private final ClaudeCodeConfig ccConfig;

    ClaudeCodeAgent(ReActAgent delegate, ClaudeCodeConfig ccConfig) {
        this.delegate = delegate;
        this.ccConfig = ccConfig;
    }

    /** The underlying AgentScope Java ReActAgent. */
    public ReActAgent delegate() {
        return delegate;
    }

    /** Claude Code-specific configuration. */
    public ClaudeCodeConfig config() {
        return ccConfig;
    }

    // ==================== Delegating API ====================

    /**
     * Sends a user-text prompt and returns the final assistant response.
     */
    public Mono<Msg> prompt(String text) {
        return prompt(text, null);
    }

    /**
     * Sends a user-text prompt with an optional per-call {@link RuntimeContext}.
     */
    public Mono<Msg> prompt(String text, RuntimeContext rc) {
        Msg userMsg = UserMessage.builder()
                .content(TextBlock.builder().text(text).build())
                .build();
        return delegate.call(List.of(userMsg), rc);
    }

    /**
     * Sends messages and returns the final assistant response.
     */
    public Mono<Msg> call(List<Msg> msgs) {
        return delegate.call(msgs);
    }

    /**
     * Sends messages with per-call {@link RuntimeContext}.
     */
    public Mono<Msg> call(List<Msg> msgs, RuntimeContext rc) {
        return delegate.call(msgs, rc);
    }

    /**
     * Streams the full agent execution as a reactive event stream.
     */
    public Flux<AgentEvent> streamPrompt(String text) {
        return streamPrompt(text, null);
    }

    /**
     * Streams with a per-call {@link RuntimeContext}.
     */
    public Flux<AgentEvent> streamPrompt(String text, RuntimeContext rc) {
        Msg userMsg = UserMessage.builder()
                .content(TextBlock.builder().text(text).build())
                .build();
        return delegate.streamEvents(List.of(userMsg), rc);
    }

    /**
     * Streams the full agent execution for the given messages.
     */
    public Flux<AgentEvent> streamEvents(List<Msg> msgs) {
        return delegate.streamEvents(msgs);
    }

    /**
     * Streams with per-call {@link RuntimeContext}.
     */
    public Flux<AgentEvent> streamEvents(List<Msg> msgs, RuntimeContext rc) {
        return delegate.streamEvents(msgs, rc);
    }

    /** Interrupt the currently running call. */
    public void interrupt() {
        delegate.interrupt();
    }

    /** Returns the agent's mutable state. */
    public io.agentscope.core.state.AgentState agentState() {
        return delegate.getAgentState();
    }

    // ── Config mapping helpers ──

    static Integer mapThinkingBudget(String level) {
        return switch (level) {
            case ClaudeCodeConfig.THINKING_MINIMAL -> 128;
            case ClaudeCodeConfig.THINKING_LOW -> 512;
            case ClaudeCodeConfig.THINKING_MEDIUM -> 1024;
            case ClaudeCodeConfig.THINKING_HIGH -> 2048;
            case ClaudeCodeConfig.THINKING_XHIGH -> 4096;
            default -> null; // OFF → null (no thinking)
        };
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String name = "ClaudeCodeAgent";
        private String description;
        private String sysPrompt;
        private Model model;
        private Toolkit toolkit;
        private ClaudeCodeConfig ccConfig = ClaudeCodeConfig.builder().build();
        private AgentStateStore stateStore;
        private final List<MiddlewareBase> middlewares = new ArrayList<>();
        private final List<Tool> legacyTools = new ArrayList<>();

        public Builder name(String v) { name = v; return this; }
        public Builder description(String v) { description = v; return this; }
        public Builder sysPrompt(String v) { sysPrompt = v; return this; }
        public Builder model(Model v) { model = v; return this; }
        public Builder toolkit(Toolkit v) { toolkit = v; return this; }
        public Builder claudeCodeConfig(ClaudeCodeConfig v) { ccConfig = v; return this; }
        public Builder stateStore(AgentStateStore v) { stateStore = v; return this; }

        /** Add a middleware to the agent's execution pipeline. */
        public Builder middleware(MiddlewareBase m) {
            middlewares.add(m);
            return this;
        }

        /**
         * Register an agent-core-java {@link Tool}, automatically wrapping it
         * in an {@link AgentScopeToolAdapter} and adding it to the toolkit.
         */
        public Builder tool(Tool tool) {
            legacyTools.add(tool);
            return this;
        }

        public ClaudeCodeAgent build() {
            if (model == null) throw new IllegalStateException("model is required");

            if (stateStore == null) {
                stateStore = io.agentcore.v2.config.AgentScopeConfig.defaultStateStore();
            }

            // Build or reuse toolkit
            Toolkit effectiveToolkit = toolkit != null ? toolkit : new Toolkit();
            for (Tool t : legacyTools) {
                effectiveToolkit.registerAgentTool(new AgentScopeToolAdapter(t));
            }

            ReActAgent.Builder raBuilder = ReActAgent.builder()
                    .name(name)
                    .model(model)
                    .toolkit(effectiveToolkit)
                    .stateStore(stateStore)
                    .maxIters(ccConfig.maxTurns())
                    .maxRetries(ccConfig.maxRetries());

            if (description != null) raBuilder.description(description);
            if (sysPrompt != null) raBuilder.sysPrompt(sysPrompt);
            if (!middlewares.isEmpty()) raBuilder.middlewares(middlewares);

            // Wire ClaudeCodeConfig into AgentScope Java options
            raBuilder.toolExecutionConfig(
                    ExecutionConfig.builder()
                            .timeout(java.time.Duration.ofSeconds((long) ccConfig.toolTimeout()))
                            .build());

            raBuilder.generateOptions(
                    GenerateOptions.builder()
                            .thinkingBudget(mapThinkingBudget(ccConfig.thinkingLevel()))
                            .parallelToolCalls(ccConfig.toolExecution()
                                    .equals(ClaudeCodeConfig.EXECUTION_PARALLEL))
                            .build());

            ReActAgent delegate = raBuilder.build();
            return new ClaudeCodeAgent(delegate, ccConfig);
        }
    }
}
