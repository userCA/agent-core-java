package io.agentcore.tools;

import io.agentcore.tools.mcp.MCPManager;
import io.agentcore.tools.util.BashOperations;
import io.agentcore.tools.util.FileMutationQueue;
import io.agentcore.tools.util.FileOperations;
import io.agentcore.tools.util.LocalBashOperations;
import io.agentcore.tools.util.LocalFileOperations;
import io.agentcore.tools.util.SandboxQuota;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Map;

/**
 * Factory for creating pre-configured {@link ToolRegistry} instances
 * with file/bash tools and confirm.
 *
 * <p>All tools use the utility layer ({@link FileOperations}, {@link LocalBashOperations},
 * {@link FileMutationQueue}) for security and consistency.
 */
public final class ToolkitFactory {

    private static final Logger log = LoggerFactory.getLogger(ToolkitFactory.class);

    private ToolkitFactory() {}

    /**
     * Creates a standard toolkit with all file/bash tools and confirm.
     */
    public static ToolRegistry standard() {
        return builder().build();
    }

    /**
     * Creates a standard toolkit with the given sandbox quota.
     */
    public static ToolRegistry standard(SandboxQuota quota) {
        return builder().quota(quota).build();
    }

    /**
     * Creates a read-only toolkit (read, grep, find, ls, bash).
     */
    public static ToolRegistry readOnly() {
        return builder().includeWrite(false).build();
    }

    /**
     * Creates a minimal toolkit (read, grep, find, ls only).
     */
    public static ToolRegistry minimal() {
        return builder().includeBash(false).includeWrite(false).build();
    }

    /**
     * Returns a new builder for fine-grained configuration.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Path workingDirectory;
        private SandboxQuota quota;
        private boolean includeBash = true;
        private boolean includeRead = true;
        private boolean includeWrite = true;
        private boolean includeSearch = true;
        private boolean includeConfirm = true;
        private boolean includeHttp = false;
        private boolean includeAgnesImage = false;
        private boolean includeAgnesVideo = false;
        private boolean includeFeishu = false;
        private boolean includeMcp = false;
        private String httpMethod = "GET";
        private String httpUrl;
        private Map<String, String> httpHeaders;
        private String httpBearerToken;

        public Builder workingDirectory(String v) {
            workingDirectory = Path.of(v);
            return this;
        }

        public Builder workingDirectory(Path v) { workingDirectory = v; return this; }
        public Builder quota(SandboxQuota v) { quota = v; return this; }
        public Builder includeBash(boolean v) { includeBash = v; return this; }
        public Builder includeRead(boolean v) { includeRead = v; return this; }
        public Builder includeWrite(boolean v) { includeWrite = v; return this; }
        public Builder includeSearch(boolean v) { includeSearch = v; return this; }
        public Builder includeConfirm(boolean v) { includeConfirm = v; return this; }
        public Builder includeAgnesImage(boolean v) { includeAgnesImage = v; return this; }
        public Builder includeAgnesVideo(boolean v) { includeAgnesVideo = v; return this; }
        public Builder includeFeishu(boolean v) { includeFeishu = v; return this; }
        public Builder includeMcp(boolean v) { includeMcp = v; return this; }

        /**
         * Enable an HTTP tool with the given URL template and method.
         */
        public Builder http(String method, String url) {
            includeHttp = true;
            httpMethod = method;
            httpUrl = url;
            return this;
        }

        public Builder httpHeaders(Map<String, String> v) { httpHeaders = v; return this; }
        public Builder httpBearerToken(String v) { httpBearerToken = v; return this; }

        public ToolRegistry build() {
            Path cwd = workingDirectory != null
                    ? workingDirectory : Path.of("").toAbsolutePath();
            FileOperations fileOps = new LocalFileOperations(cwd, quota);
            FileMutationQueue mutationQueue = new FileMutationQueue();
            ToolRegistry registry = new ToolRegistry();

            if (includeRead) {
                registry.register(new ReadTool(fileOps, mutationQueue));
            }

            if (includeWrite) {
                registry.register(new WriteTool(fileOps, mutationQueue));
                registry.register(new EditTool(fileOps, mutationQueue));
            }

            if (includeSearch) {
                registry.register(new GrepTool(fileOps));
                registry.register(new FindTool(fileOps));
                registry.register(new LsTool(fileOps));
            }

            if (includeBash) {
                BashOperations bashOps = new LocalBashOperations(cwd.toString(), null, quota);
                registry.register(new BashTool(bashOps));
            }

            if (includeConfirm) {
                registry.register(new ConfirmTool());
            }

            if (includeHttp && httpUrl != null) {
                registry.register(new HttpTool(
                        "http", "Make HTTP requests to external APIs.",
                        httpMethod, httpUrl, null,
                        httpHeaders, httpBearerToken, 30));
            }

            if (includeAgnesImage) {
                registry.register(new AgnesImageTool());
            }

            if (includeAgnesVideo) {
                registry.register(new AgnesVideoTool());
                registry.register(new CheckVideoTool());
            }

            if (includeFeishu) {
                registry.register(new FeishuCLITool());
            }

            if (includeMcp) {
                MCPManager manager = MCPManager.fromEnv();
                try {
                    manager.start();
                    manager.registerTools(registry);
                } catch (Exception e) {
                    log.warn("MCP tools init failed", e);
                    manager.stop();
                }
            }

            return registry;
        }
    }
}
