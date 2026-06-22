package io.agentcore.tools.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Local implementation of {@link BashOperations} using {@link ProcessBuilder}.
 *
 * <p>Features:
 * <ul>
 *   <li>Capped output streams to prevent OOM from unbounded output</li>
 *   <li>Quota-enforced timeout capping</li>
 *   <li>Signal-based early cancellation via {@link AtomicBoolean}</li>
 *   <li>Virtual threads for stdout/stderr readers</li>
 * </ul>
 */
public class LocalBashOperations implements BashOperations {

    private static final Logger log = LoggerFactory.getLogger(LocalBashOperations.class);

    /** Maximum bytes to capture per stream (stdout/stderr) to prevent OOM. */
    public static final int MAX_OUTPUT_BYTES = 1_000_000; // 1 MB

    private final String cwd;
    private final String shell;
    private final SandboxQuota quota;

    public LocalBashOperations(String cwd, String shell, SandboxQuota quota) {
        this.cwd = cwd != null ? cwd : System.getProperty("user.dir");
        this.shell = shell != null ? shell : detectShell();
        this.quota = quota;
    }

    public LocalBashOperations(String cwd) {
        this(cwd, null, null);
    }

    public LocalBashOperations() {
        this(null, null, null);
    }

    @Override
    public String cwd() {
        return cwd;
    }

    @Override
    public CompletableFuture<BashResult> execute(
            String command, String cwdOverride, Double timeout,
            Map<String, String> env, Consumer<byte[]> onData,
            AtomicBoolean signal) {

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
                pb.directory(new File(cwdOverride != null ? cwdOverride : this.cwd));
                pb.redirectErrorStream(false);
                if (env != null) pb.environment().putAll(env);

                Process process = pb.start();

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
                    } catch (IOException e) {
                        log.debug("Error reading stdout from process", e);
                    }
                });

                Thread errReader = Thread.ofVirtual().start(() -> {
                    try (var is = process.getErrorStream()) {
                        byte[] buf = new byte[4096];
                        int n;
                        while ((n = is.read(buf)) != -1) {
                            stderrBytes.write(buf, 0, n);
                        }
                    } catch (IOException e) {
                        log.debug("Error reading stderr from process", e);
                    }
                });

                double timeoutSec = effectiveTimeout != null ? effectiveTimeout : 120.0;
                long deadline = System.nanoTime() + (long) (timeoutSec * 1_000_000_000L);
                boolean finished = false;
                while (true) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) break;
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

    private static String detectShell() {
        String s = System.getenv("SHELL");
        return s != null ? s : "/bin/sh";
    }

    /**
     * Output stream that silently discards bytes beyond a configurable limit,
     * preventing OOM from commands that produce unbounded output.
     */
    static final class CappedOutputStream {
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
