package io.agentcore.observability;

import io.agentcore.core.AgentEvent;
import io.agentcore.core.AgentEvent.*;
import io.agentcore.extensions.Extension;

import java.util.Map;

/**
 * Optional observability for agent runs — logs structured events via SLF4J.
 *
 * <p>Mirrors Python {@code agent_core/observability.py}. When OpenTelemetry
 * is available on the classpath, users can wrap this with an OTel-backed
 * implementation. The default uses SLF4J for structured logging.
 *
 * <p>Usage:
 * <pre>{@code
 * Agent agent = Agent.create(...);
 * agent.subscribe(new ObservabilityExtension("my-session"));
 * agent.prompt("Hello", null);
 * }</pre>
 */
public class ObservabilityExtension implements Extension {

    private final String sessionId;
    private final String providerName;
    private final String modelId;
    private final EventSink sink;

    /**
     * Sink interface for observability events.
     * Implement this to bridge to OpenTelemetry, Datadog, etc.
     */
    @FunctionalInterface
    public interface EventSink {
        void emit(String eventType, Map<String, Object> attributes);
    }

    public ObservabilityExtension(String sessionId) {
        this(sessionId, "", "", EventSinks.slf4j());
    }

    public ObservabilityExtension(String sessionId, String providerName, String modelId) {
        this(sessionId, providerName, modelId, EventSinks.slf4j());
    }

    public ObservabilityExtension(String sessionId, String providerName,
                                   String modelId, EventSink sink) {
        this.sessionId = sessionId != null ? sessionId : "";
        this.providerName = providerName != null ? providerName : "";
        this.modelId = modelId != null ? modelId : "";
        this.sink = sink != null ? sink : EventSinks.noOp();
    }

    @Override
    public String name() {
        return "observability";
    }

    @Override
    public void onEvent(AgentEvent event) {
        if (event instanceof AgentStart) {
            sink.emit("agent.start", Map.of(
                    "session_id", sessionId,
                    "provider", providerName,
                    "model", modelId
            ));
        } else if (event instanceof AgentEnd ae) {
            sink.emit("agent.end", Map.of(
                    "session_id", sessionId,
                    "message_count", ae.messages().size()
            ));
        } else if (event instanceof TurnStart) {
            sink.emit("agent.turn.start", Map.of(
                    "session_id", sessionId
            ));
        } else if (event instanceof TurnEnd) {
            sink.emit("agent.turn.end", Map.of(
                    "session_id", sessionId
            ));
        } else if (event instanceof ToolExecutionStart tes) {
            sink.emit("agent.tool_call.start", Map.of(
                    "session_id", sessionId,
                    "tool.name", tes.toolName(),
                    "tool.call_id", tes.toolCallId()
            ));
        } else if (event instanceof ToolExecutionEnd tee) {
            sink.emit("agent.tool_call.end", Map.of(
                    "session_id", sessionId,
                    "tool.name", tee.toolName(),
                    "tool.call_id", tee.toolCallId(),
                    "is_error", tee.isError()
            ));
        }
    }

    /**
     * Built-in EventSink implementations.
     */
    public static final class EventSinks {

        private EventSinks() {}

        /** No-op sink that discards all events. */
        public static EventSink noOp() {
            return (type, attrs) -> {};
        }

        /** SLF4J-based sink that logs events at INFO level. */
        public static EventSink slf4j() {
            return new Slf4jEventSink();
        }
    }

    private static final class Slf4jEventSink implements EventSink {
        private static final org.slf4j.Logger log =
                org.slf4j.LoggerFactory.getLogger("io.agentcore.observability");

        @Override
        public void emit(String eventType, Map<String, Object> attributes) {
            log.info("[{}] {}", eventType, attributes);
        }
    }
}
