package io.agentcore.agent;

/**
 * Manages lazy-initialized, reusable resources (ToolRunner + StreamAccumulator).
 *
 * <p>Design intent: Isolate resource lifecycle concerns from Agent's orchestration logic.
 * Both resources use double-checked locking for thread-safe lazy initialization,
 * and are intentionally preserved across reset() calls for reuse.
 *
 * <p>Constraint: close() is guarded externally by Agent's AtomicBoolean,
 * so no synchronized is needed here.
 */
final class AgentResources implements AutoCloseable {

    private volatile ToolRunner sharedToolRunner;
    private volatile StreamAccumulator sharedStreamAccumulator;

    /**
     * Get or lazily create the shared ToolRunner.
     * Reuses the ExecutorService across calls for efficiency.
     *
     * @return the ToolRunner, or null if no tool registry is configured
     */
    ToolRunner getOrCreateToolRunner(AgentLoopConfig config) {
        if (config.toolRegistry() == null) return null;
        ToolRunner runner = sharedToolRunner;
        if (runner == null) {
            synchronized (this) {
                runner = sharedToolRunner;
                if (runner == null) {
                    runner = new ToolRunner(config.toolRegistry(), config.toolConfig(),
                            config.beforeToolCall(), config.afterToolCall(),
                            config.humanInputGate());
                    sharedToolRunner = runner;
                }
            }
        }
        return runner;
    }

    /**
     * Get or lazily create the shared StreamAccumulator.
     * Config parameters are synced per run via updateConfig().
     */
    StreamAccumulator getOrCreateStreamAccumulator(AgentLoopConfig config) {
        StreamAccumulator acc = sharedStreamAccumulator;
        if (acc == null) {
            synchronized (this) {
                acc = sharedStreamAccumulator;
                if (acc == null) {
                    acc = new StreamAccumulator(
                            config.streamFn(), config.model(),
                            config.thinkingLevel().value(), config.temperature(), config.maxTokens());
                    sharedStreamAccumulator = acc;
                }
            }
        }
        // Sync config for this run
        acc.updateConfig(config.model(), config.thinkingLevel().value(), config.temperature());
        return acc;
    }

    @Override
    public void close() {
        ToolRunner runner = sharedToolRunner;
        if (runner != null) {
            sharedToolRunner = null;
            runner.close();
        }
    }
}
