package io.agentcore.extensions;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Context passed to every Extension hook, providing access to the running
 * agent, its state, and session metadata.
 */
public final class ExtensionContext {

    private final String sessionId;
    private final Object agent;
    private final Object store;
    private final AtomicBoolean abortSignal;
    private final Object model;
    private final Map<String, Object> metadata;

    private ExtensionContext(Builder builder) {
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

    /** The running agent instance (usually {@link io.agentcore.agent.Agent}). */
    public Object agent() {
        return agent;
    }

    /** Optional session store reference. */
    public Object store() {
        return store;
    }

    /** True if the agent has been requested to abort. */
    public boolean isAborted() {
        return abortSignal != null && abortSignal.get();
    }

    /** Optional model reference (provider-specific). */
    public Object model() {
        return model;
    }

    /** Arbitrary metadata map (read-only view). */
    public Map<String, Object> metadata() {
        return metadata;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String sessionId;
        private Object agent;
        private Object store;
        private AtomicBoolean abortSignal;
        private Object model;
        private Map<String, Object> metadata;

        public Builder sessionId(String v) { sessionId = v; return this; }
        public Builder agent(Object v) { agent = v; return this; }
        public Builder store(Object v) { store = v; return this; }
        public Builder abortSignal(AtomicBoolean v) { abortSignal = v; return this; }
        public Builder model(Object v) { model = v; return this; }
        public Builder metadata(Map<String, Object> v) { metadata = v; return this; }

        public ExtensionContext build() {
            if (sessionId == null) {
                throw new IllegalStateException("sessionId is required");
            }
            return new ExtensionContext(this);
        }
    }
}
