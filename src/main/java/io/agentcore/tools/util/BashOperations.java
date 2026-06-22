package io.agentcore.tools.util;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Abstraction for sandboxed shell command execution.
 *
 * <p>Implementations should enforce timeout quotas, cap output to
 * prevent OOM, and support signal-based cancellation.
 *
 * @see LocalBashOperations
 */
public interface BashOperations {

    /** The default working directory for commands. */
    String cwd();

    /**
     * Execute a shell command asynchronously.
     *
     * @param command     shell command string
     * @param cwdOverride working directory override (null → default cwd)
     * @param timeout     timeout in seconds (null → default)
     * @param env         additional environment variables (null → none)
     * @param onData      optional callback for stdout chunks (null → no callback)
     * @param signal      abort signal (null → no cancellation)
     * @return future resolving to the command result
     */
    CompletableFuture<BashResult> execute(
            String command, String cwdOverride, Double timeout,
            Map<String, String> env, Consumer<byte[]> onData,
            AtomicBoolean signal);
}
