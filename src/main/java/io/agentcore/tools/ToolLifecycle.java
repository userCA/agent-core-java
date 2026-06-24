package io.agentcore.tools;

/**
 * Optional lifecycle interface for tools that need startup/shutdown hooks.
 *
 * <p>Tools implementing this interface will have their lifecycle managed
 * by the {@link ToolRegistry}: {@link #start()} is called on registration,
 * {@link #stop()} is called on unregistration or registry shutdown.
 *
 * <p>Typical use cases: MCP connections, HTTP clients, database pools.
 */
public interface ToolLifecycle {

    /**
     * Called when the tool is registered. Initialize resources here.
     *
     * @throws Exception if initialization fails (the tool will not be registered)
     */
    default void start() throws Exception {}

    /**
     * Called when the tool is unregistered or the registry shuts down.
     * Release resources here.
     */
    default void stop() {}
}
