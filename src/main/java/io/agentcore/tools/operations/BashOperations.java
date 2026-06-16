package io.agentcore.tools.operations;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Pluggable bash operations protocol.
 */
public interface BashOperations {
    CompletableFuture<BashResult> execute(
            String command,
            String cwd,
            Double timeout,
            Map<String, String> env,
            Consumer<byte[]> onData,
            AtomicBoolean signal
    );
}
