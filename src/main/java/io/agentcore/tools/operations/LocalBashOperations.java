package io.agentcore.tools.operations;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Local bash operations using ProcessBuilder.
 * Integrates SandboxQuota for command safety and resource limits.
 */
public class LocalBashOperations implements BashOperations {
    private static final Pattern DANGEROUS_CMD_PATTERN = Pattern.compile(
            "(?i)(rm\\s+-rf\\s+/|mkfs|dd\\s+if=|:(){ :\\|:& };:|fork\\s*bomb" +
            "|chmod\\s+-R\\s+777|curl.*\\|\\s*sh|wget.*\\|\\s*sh|nc\\s+-[el]" +
            "|bash\\s+-i|/dev/sda|>\\s*/dev/sda|shutdown|reboot|halt|poweroff|init\\s+[06])"
    );

    /** Maximum bytes to capture per stream (stdout/stderr) to prevent OOM. */
    private static final int MAX_OUTPUT_BYTES = 1_000_000; // 1MB

    private final String cwd;
    private final String shell;
    private final SandboxQuota quota;

    public LocalBashOperations() {
        this(null, null, null);
    }

    public LocalBashOperations(String cwd, String shell) {
        this(cwd, shell, null);
    }

    public LocalBashOperations(String cwd, String shell, SandboxQuota quota) {
        this.cwd = cwd != null ? cwd : System.getProperty("user.dir");
        this.shell = shell != null ? shell : detectShell();
        this.quota = quota;
    }

    /**
     * Check if a command matches known dangerous patterns.
     * Returns true if the command appears dangerous.
     */
    public static boolean isDangerousCommand(String command) {
        return DANGEROUS_CMD_PATTERN.matcher(command).find();
    }

    private static String detectShell() {
        String s = System.getenv("SHELL");
        return s != null ? s : "/bin/sh";
    }

    @Override
    public CompletableFuture<BashResult> execute(
            String command, String cwd, Double timeout,
            Map<String, String> env, Consumer<byte[]> onData,
            AtomicBoolean signal
    ) {
        // Use dedicated virtual thread to avoid ForkJoinPool.commonPool() starvation
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Enforce quota timeout if set
                Double effectiveTimeout = timeout;
                if (quota != null && quota.timeoutSeconds() > 0) {
                    double quotaTimeout = quota.timeoutSeconds();
                    if (effectiveTimeout == null || effectiveTimeout > quotaTimeout) {
                        effectiveTimeout = quotaTimeout;
                    }
                }

                ProcessBuilder pb = new ProcessBuilder(shell, "-c", command);
                pb.directory(new File(cwd != null ? cwd : this.cwd));
                pb.redirectErrorStream(false);
                if (env != null) pb.environment().putAll(env);

                Process process = pb.start();

                // Use capped output streams to prevent OOM from unbounded output
                CappedOutputStream stdoutBytes = new CappedOutputStream(MAX_OUTPUT_BYTES);
                CappedOutputStream stderrBytes = new CappedOutputStream(MAX_OUTPUT_BYTES);

                Thread outReader = Thread.ofVirtual().start(() -> {
                    try (var is = process.getInputStream()) {
                        byte[] buf = new byte[4096];
                        int n;
                        while ((n = is.read(buf)) != -1) {
                            stdoutBytes.write(buf, 0, n);
                            if (onData != null) {
                                byte[] chunk = new byte[n];
                                System.arraycopy(buf, 0, chunk, 0, n);
                                onData.accept(chunk);
                            }
                        }
                    } catch (IOException ignored) {}
                });

                Thread errReader = Thread.ofVirtual().start(() -> {
                    try (var is = process.getErrorStream()) {
                        byte[] buf = new byte[4096];
                        int n;
                        while ((n = is.read(buf)) != -1) {
                            stderrBytes.write(buf, 0, n);
                        }
                    } catch (IOException ignored) {}
                });

                double timeoutSec = effectiveTimeout != null ? effectiveTimeout : 120.0;

                // Poll signal during wait for early cancellation
                long deadline = System.nanoTime() + (long) (timeoutSec * 1_000_000_000L);
                boolean finished = false;
                while (true) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) break;

                    // Check abort signal
                    if (signal != null && signal.get()) {
                        process.destroyForcibly();
                        break;
                    }

                    long pollMs = Math.min(500, remaining / 1_000_000);
                    if (pollMs <= 0) pollMs = 1;
                    finished = process.waitFor(pollMs, TimeUnit.MILLISECONDS);
                    if (finished) break;
                }

                outReader.join(5000);
                errReader.join(5000);

                String stdout = stdoutBytes.asString();
                String stderr = stderrBytes.asString();

                if (!finished) {
                    process.destroyForcibly();
                    return new BashResult(stdout, stderr, -1, true, null, null);
                }

                return new BashResult(stdout, stderr, process.exitValue());

            } catch (Exception e) {
                return new BashResult("", e.getMessage(), -1);
            }
        }, executor).whenComplete((result, error) -> executor.close());
    }

    /**
     * Output stream that silently discards bytes beyond a configurable limit,
     * preventing OOM from commands that produce unbounded output.
     */
    static class CappedOutputStream {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final int maxBytes;
        private boolean truncated = false;

        CappedOutputStream(int maxBytes) {
            this.maxBytes = maxBytes;
        }

        void write(byte[] buf, int off, int len) {
            int remaining = maxBytes - buffer.size();
            if (remaining <= 0) {
                truncated = true;
                return;
            }
            int toWrite = Math.min(len, remaining);
            buffer.write(buf, off, toWrite);
            if (toWrite < len) truncated = true;
        }

        String asString() {
            String s = buffer.toString(StandardCharsets.UTF_8);
            if (truncated) s += "\n... (output truncated at " + maxBytes + " bytes)";
            return s;
        }
    }
}
