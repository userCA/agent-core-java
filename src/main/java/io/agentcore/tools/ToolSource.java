package io.agentcore.tools;

/**
 * Typed source identifier for tool provenance tracking.
 *
 * <p>Replaces the previous {@code Object source} field in {@link ToolInfo}
 * with a sealed interface for compile-time safety and clear categorization.
 */
public sealed interface ToolSource {

    /** Built-in tool shipped with agent-core. */
    record Builtin() implements ToolSource {
        public static final Builtin INSTANCE = new Builtin();
    }

    /** Tool discovered from an MCP server. */
    record McpServer(String serverName) implements ToolSource {
        public McpServer {
            if (serverName == null) serverName = "";
        }
    }

    /** Tool registered by an extension. */
    record Extension(String extensionName) implements ToolSource {
        public Extension {
            if (extensionName == null) extensionName = "";
        }
    }

    /** Tool from an external integration (Agnes, Feishu, etc.). */
    record External(String integrationName) implements ToolSource {
        public External {
            if (integrationName == null) integrationName = "";
        }
    }
}
