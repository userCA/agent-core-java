package io.agentcore.v2.tools;

import io.agentcore.core.content.TextContent;
import io.agentcore.tools.base.Tool;
import io.agentcore.tools.base.ToolContext;
import io.agentcore.tools.base.ToolDefinition;
import io.agentcore.tools.base.ToolResult;
import io.agentcore.tools.mutation.FileMutationQueue;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Adapts an agent-core-java {@link Tool} to AgentScope Java's {@link ToolBase}.
 *
 * <p>All security features (path traversal, command injection, SSRF, output caps,
 * atomic writes) are preserved because the original tool runs unchanged.
 */
public final class AgentScopeToolAdapter extends ToolBase {

    private static final Logger log = LoggerFactory.getLogger(AgentScopeToolAdapter.class);

    private final Tool delegate;
    private final FileMutationQueue mutationQueue;

    public AgentScopeToolAdapter(Tool delegate, FileMutationQueue mutationQueue) {
        super(builderFrom(delegate.definition()));
        this.delegate = delegate;
        this.mutationQueue = mutationQueue;
    }

    public AgentScopeToolAdapter(Tool delegate) {
        this(delegate, null);
    }

    // ── ToolBase override ──

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        Map<String, Object> params = param.getInput();
        String toolCallId = param.getToolUseBlock() != null
                ? param.getToolUseBlock().getId()
                : delegate.definition().name();

        AtomicBoolean signal = new AtomicBoolean(false);
        ToolContext toolCtx = new ToolContext(signal, null, Map.of(), mutationQueue);

        CompletableFuture<ToolResult> future;
        try {
            future = delegate.execute(toolCallId, params, toolCtx);
        } catch (Exception e) {
            log.warn("Tool {} threw synchronously: {}",
                    delegate.definition().name(), e.getMessage());
            return Mono.just(ToolResultBlock.error(e.getMessage())
                    .withIdAndName(toolCallId, delegate.definition().name()));
        }

        return Mono.fromFuture(() -> future)
                .map(result -> toResultBlock(result, toolCallId))
                .onErrorResume(error -> {
                    log.warn("Tool {} failed: {}",
                            delegate.definition().name(), error.getMessage());
                    return Mono.just(ToolResultBlock.error(error.getMessage())
                            .withIdAndName(toolCallId, delegate.definition().name()));
                });
    }

    public Tool delegate() {
        return delegate;
    }

    // ── Static helpers ──

    private static Builder builderFrom(ToolDefinition def) {
        return ToolBase.builder()
                .name(def.name())
                .description(def.description())
                .inputSchema(def.parameters());
    }

    static ToolResultBlock toResultBlock(ToolResult result, String toolCallId) {
        List<io.agentscope.core.message.ContentBlock> output = new ArrayList<>();
        if (result.content() != null) {
            for (var c : result.content()) {
                if (c instanceof TextContent tc && tc.text() != null) {
                    output.add(TextBlock.builder().text(tc.text()).build());
                }
            }
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        if (result.details() instanceof Map<?, ?> m) {
            for (var entry : m.entrySet()) {
                meta.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }

        return output.isEmpty()
                ? ToolResultBlock.of(TextBlock.builder().text("").build(), meta)
                        .withIdAndName(toolCallId, "result")
                : ToolResultBlock.of(output, meta).withIdAndName(toolCallId, "result");
    }
}
