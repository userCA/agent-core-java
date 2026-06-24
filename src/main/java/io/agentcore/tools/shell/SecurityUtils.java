package io.agentcore.tools.shell;

import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Static security utilities shared by all V2 native tools.
 *
 * <p>Consolidates path traversal prevention, dangerous command detection,
 * and SSRF protection into a single dependency-free utility class.
 *
 * <p>Dangerous command detection uses two severity levels:
 * <ul>
 *   <li>{@link #isDestructive(String)} — always blocked (rm -rf /, fork bombs, etc.)</li>
 *   <li>{@link #isSuspicious(String)} — potentially risky, needs closer inspection</li>
 * </ul>
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    // ── Dangerous command detection (two severity levels) ──

    /**
     * Always-destructive patterns: commands that cause irreversible system damage.
     * These should always be blocked.
     */
    private static final Pattern DESTRUCTIVE_PATTERN = Pattern.compile(
            "(?i)(rm\\s+-rf\\s+/|rm\\s+-rf\\s+/\\*|mkfs|dd\\s+if="
            + "|:\\(\\)\\{\\s*:\\|:&\\s*\\}\\s*;:|fork\\s*bomb"
            + "|chmod\\s+-R\\s+777\\s+/|chmod\\s+777\\s+/"
            + "|curl.*\\|\\s*sh|wget.*\\|\\s*sh"
            + "|nc\\s+-[el]|bash\\s+-i"
            + "|>\\s*/dev/sda|>\\s*/dev/nvme"
            + "|shutdown|reboot|halt|poweroff|init\\s+[06])"
    );

    /**
     * Suspicious patterns: commands that are risky but may be legitimate.
     * Used by sandbox policy for closer inspection/quota adjustment.
     */
    private static final Pattern SUSPICIOUS_PATTERN = Pattern.compile(
            "(?i)(rm\\s+-rf|chmod\\s+-R\\s+777|killall|pkill|kill\\s+-9"
            + "|sudo\\s+|su\\s+-|passwd|useradd|userdel|groupadd"
            + "|iptables|ip\\s+link|ifconfig)"
    );

    /**
     * Returns {@code true} if the command is always destructive
     * (e.g. {@code rm -rf /}, fork bombs, disk formatting, system shutdown).
     *
     * <p>These commands should always be blocked regardless of context.
     */
    public static boolean isDestructive(String command) {
        return DESTRUCTIVE_PATTERN.matcher(command).find();
    }

    /**
     * Returns {@code true} if the command is suspicious (risky but possibly legitimate).
     *
     * <p>Used by sandbox policy to apply closer inspection or stricter quotas.
     */
    public static boolean isSuspicious(String command) {
        return SUSPICIOUS_PATTERN.matcher(command).find();
    }

    /**
     * @deprecated Use {@link #isDestructive(String)} instead.
     */
    @Deprecated
    public static boolean isDangerousCommand(String command) {
        return isDestructive(command);
    }

    // ── Path traversal prevention ──

    /**
     * Resolve a path against the working directory, blocking any traversal
     * that escapes the cwd boundary.
     *
     * @param path    user-supplied path (absolute or relative)
     * @param cwd     the sandbox working directory
     * @param denyPaths optional list of additionally denied path prefixes
     * @return the resolved, normalized {@link Path}
     * @throws SecurityException if the path escapes cwd or hits a deny rule
     */
    public static Path resolvePath(String path, Path cwd, List<String> denyPaths) {
        Path p = Path.of(path);
        Path resolved = p.isAbsolute() ? p.normalize() : cwd.resolve(p).normalize();
        if (!resolved.startsWith(cwd)) {
            throw new SecurityException(
                    "Path traversal blocked: " + path + " resolves outside working directory");
        }
        if (denyPaths != null) {
            String resolvedStr = resolved.toString();
            for (String denied : denyPaths) {
                if (resolvedStr.startsWith(Path.of(denied).normalize().toString())) {
                    throw new SecurityException("Access denied to path: " + path);
                }
            }
        }
        return resolved;
    }

    /**
     * Convenience overload without deny-paths.
     */
    public static Path resolvePath(String path, Path cwd) {
        return resolvePath(path, cwd, null);
    }

    /**
     * Resolve a path for write operations, additionally enforcing an
     * allowlist of writable directories.
     *
     * @param path         user-supplied path
     * @param cwd          sandbox working directory
     * @param denyPaths    optional denied path prefixes
     * @param allowWritePaths optional allowlist; if non-null and non-empty,
     *                       the resolved path must start with one of these
     * @return the resolved, normalized {@link Path}
     * @throws SecurityException if any check fails
     */
    public static Path resolveForWrite(String path, Path cwd,
                                       List<String> denyPaths,
                                       List<String> allowWritePaths) {
        Path resolved = resolvePath(path, cwd, denyPaths);
        if (allowWritePaths != null && !allowWritePaths.isEmpty()) {
            String resolvedStr = resolved.toString();
            boolean allowed = false;
            for (String allowedPath : allowWritePaths) {
                if (resolvedStr.startsWith(Path.of(allowedPath).normalize().toString())) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                throw new SecurityException("Write not allowed to path: " + path);
            }
        }
        return resolved;
    }

    // ── SSRF protection ──

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    /**
     * Validate a URL to prevent Server-Side Request Forgery (SSRF).
     * <ul>
     *   <li>Blocks non-http(s) schemes</li>
     *   <li>Blocks localhost, metadata services, {@code .internal}, {@code .local}</li>
     *   <li>DNS-resolves the host and rejects private/reserved IP ranges</li>
     * </ul>
     *
     * @throws SecurityException if the URL is unsafe
     */
    public static void validateUrl(String url) {
        URI uri = URI.create(url);
        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
            throw new SecurityException(
                    "Only http and https schemes are allowed, got: " + scheme);
        }

        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            throw new SecurityException("URL must have a valid host");
        }

        String hostLower = host.toLowerCase();
        if (hostLower.equals("localhost")
                || hostLower.equals("metadata.google.internal")
                || hostLower.equals("[::1]")
                || hostLower.contains("169.254.169.254")
                || hostLower.endsWith(".internal")
                || hostLower.endsWith(".local")) {
            throw new SecurityException(
                    "Access to metadata services and internal hosts is blocked");
        }

        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (addr.isLoopbackAddress() || addr.isLinkLocalAddress()
                        || addr.isSiteLocalAddress() || addr.isAnyLocalAddress()
                        || addr.isMulticastAddress()) {
                    throw new SecurityException(
                            "Access to private/internal network addresses is blocked");
                }
                byte[] raw = addr.getAddress();
                if (raw.length == 4) {
                    // IPv4: block 169.254.0.0/16 (link-local range)
                    if ((raw[0] & 0xFF) == 169 && (raw[1] & 0xFF) == 254) {
                        throw new SecurityException(
                                "Access to link-local addresses is blocked");
                    }
                    // Block 100.64.0.0/10 (carrier-grade NAT)
                    if ((raw[0] & 0xFF) == 100 && ((raw[1] & 0xFF) & 0xC0) == 64) {
                        throw new SecurityException(
                                "Access to reserved address ranges is blocked");
                    }
                }
            }
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new SecurityException("Unable to resolve host: " + host);
        }
    }
}
