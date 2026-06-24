package io.agentcore.extensions;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Context passed to every Extension hook, providing access to the running
 * agent, its state, and session metadata.
 *
 * @param <A> agent type (e.g. {@code Agent})
 * @param <S> session store type (e.g. {@code SessionStore})
 * @param <M> model info type (e.g. {@code ModelInfo})
 */
public final class ExtensionContext<A, S, M> {

    private final String sessionId;
    private final A agent;
    private final S store;
    private final AtomicBoolean abortSignal;
    private final M model;
    private final Map<String, Object> metadata;

    private ExtensionContext(Builder<A, S, M> builder) {
        this.sessionId = builder.sessionId;
        this.agent = builder.agent;
        this.store = builder.store;
        this.abortSignal = builder.abortSignal;
        this.model = builder.model;
        this.metadata = builder.metadata != null
                ? Map.copyOf(builder.metadata) : Map.of();
    }

    /** Unique identifier for the current session. */
    public String sessionId() {
        return sessionId;
    }

    /** The running agent instance. */
    public A agent() {
        return agent;
    }

    /** Optional session store reference. */
    public S store() {
        return store;
    }

    /** True if the agent has been requested to abort. */
    public boolean isAborted() {
        return abortSignal != null && abortSignal.get();
    }

    /** Optional model reference. */
    public M model() {
        return model;
    }

    /** Arbitrary metadata map (read-only view). */
    public Map<String, Object> metadata() {
        return metadata;
    }

    public static Builder<Object, Object, Object> builder() {
        return new Builder<>();
    }

    public static final class Builder<A, S, M> {
        private String sessionId;
        private A agent;
        private S store;
        private AtomicBoolean abortSignal;
        private M model;
        private Map<String, Object> metadata;

        public Builder<A, S, M> sessionId(String v) { sessionId = v; return this; }
        public Builder<A, S, M> agent(A v) { agent = v; return this; }
        public Builder<A, S, M> store(S v) { store = v; return this; }
        public Builder<A, S, M> abortSignal(AtomicBoolean v) { abortSignal = v; return this; }
        public Builder<A, S, M> model(M v) { model = v; return this; }
        public Builder<A, S, M> metadata(Map<String, Object> v) { metadata = v; return this; }

        public ExtensionContext<A, S, M> build() {
            if (sessionId == null) {
                throw new IllegalStateException("sessionId is required");
            }
            return new ExtensionContext<>(this);
        }
    }
}
