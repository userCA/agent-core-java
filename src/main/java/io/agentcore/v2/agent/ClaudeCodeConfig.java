package io.agentcore.v2.agent;

/**
 * Configuration for Claude Code agent behavior.
 *
 * <p>Uses plain types so the v2 package compiles independently of the
 * legacy agent-core-java packages. When the migration is complete,
 * the fields can be re-typed to the canonical enums.
 */
public record ClaudeCodeConfig(
        /** Thinking level: "off", "minimal", "low", "medium", "high", "xhigh" */
        String thinkingLevel,
        /** Tool execution mode: "parallel" or "sequential" */
        String toolExecution,
        int maxRetries,
        double retryBaseDelay,
        double retryMaxDelay,
        int maxTurns,
        double toolTimeout,
        int toolResultMaxChars,
        /** Steering queue drain mode: "all" or "one_at_a_time" */
        String steeringMode,
        /** Follow-up queue drain mode: "all" or "one_at_a_time" */
        String followUpMode
) {

    // Canonical constants
    public static final String THINKING_OFF = "off";
    public static final String THINKING_MINIMAL = "minimal";
    public static final String THINKING_LOW = "low";
    public static final String THINKING_MEDIUM = "medium";
    public static final String THINKING_HIGH = "high";
    public static final String THINKING_XHIGH = "xhigh";

    public static final String EXECUTION_PARALLEL = "parallel";
    public static final String EXECUTION_SEQUENTIAL = "sequential";

    public static final String QUEUE_ALL = "all";
    public static final String QUEUE_ONE_AT_A_TIME = "one_at_a_time";

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String thinkingLevel = THINKING_OFF;
        private String toolExecution = EXECUTION_PARALLEL;
        private int maxRetries = 3;
        private double retryBaseDelay = 1.0;
        private double retryMaxDelay = 60.0;
        private int maxTurns = 50;
        private double toolTimeout = 120.0;
        private int toolResultMaxChars = 4000;
        private String steeringMode = QUEUE_ONE_AT_A_TIME;
        private String followUpMode = QUEUE_ONE_AT_A_TIME;

        public Builder thinkingLevel(String v) { thinkingLevel = v; return this; }
        public Builder toolExecution(String v) { toolExecution = v; return this; }
        public Builder maxRetries(int v) { maxRetries = v; return this; }
        public Builder retryBaseDelay(double v) { retryBaseDelay = v; return this; }
        public Builder retryMaxDelay(double v) { retryMaxDelay = v; return this; }
        public Builder maxTurns(int v) { maxTurns = v; return this; }
        public Builder toolTimeout(double v) { toolTimeout = v; return this; }
        public Builder toolResultMaxChars(int v) { toolResultMaxChars = v; return this; }
        public Builder steeringMode(String v) { steeringMode = v; return this; }
        public Builder followUpMode(String v) { followUpMode = v; return this; }

        public ClaudeCodeConfig build() {
            return new ClaudeCodeConfig(
                    thinkingLevel, toolExecution, maxRetries, retryBaseDelay,
                    retryMaxDelay, maxTurns, toolTimeout, toolResultMaxChars,
                    steeringMode, followUpMode);
        }
    }
}
