package io.agentcore.tools.operations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

/**
 * Local filesystem implementation of FileOperations.
 * Enforces path boundary validation to prevent path traversal attacks.
 */
public class LocalFileOperations implements FileOperations {
    private static final Logger log = LoggerFactory.getLogger(LocalFileOperations.class);

    private final Path cwd;
    private final SandboxQuota quota;

    /** Default line limit for read() when no explicit limit is provided, to prevent OOM. */
    private static final int DEFAULT_READ_LIMIT = 2000;

    public LocalFileOperations() {
        this(Path.of("").toAbsolutePath().toString(), null);
    }

    public LocalFileOperations(String cwd) {
        this(cwd, null);
    }

    public LocalFileOperations(String cwd, SandboxQuota quota) {
        this.cwd = Path.of(cwd).toAbsolutePath().normalize();
        this.quota = quota;
    }

    /**
     * Resolve a path and validate it stays within the cwd boundary.
     * @throws SecurityException if the resolved path escapes cwd
     */
    private Path resolve(String path) {
        Path p = Path.of(path);
        Path resolved = p.isAbsolute() ? p.normalize() : cwd.resolve(p).normalize();
        if (!resolved.startsWith(cwd)) {
            throw new SecurityException("Path traversal blocked: " + path + " resolves outside working directory");
        }
        // Check denyPaths from quota
        if (quota != null && quota.denyPaths() != null) {
            String resolvedStr = resolved.toString();
            for (String denied : quota.denyPaths()) {
                if (resolvedStr.startsWith(Path.of(denied).normalize().toString())) {
                    throw new SecurityException("Access denied to path: " + path);
                }
            }
        }
        return resolved;
    }

    /**
     * Resolve a path and additionally validate write permissions.
     * @throws SecurityException if writing to this path is not allowed
     */
    private Path resolveForWrite(String path) {
        Path resolved = resolve(path);
        if (quota != null && quota.allowWritePaths() != null && !quota.allowWritePaths().isEmpty()) {
            String resolvedStr = resolved.toString();
            boolean allowed = false;
            for (String allowedPath : quota.allowWritePaths()) {
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

    @Override
    public CompletableFuture<String> read(String path, int offset, Integer limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path resolved = resolve(path);
                // Stream lines to avoid loading entire file into memory for large files
                try (Stream<String> lines = Files.lines(resolved)) {
                    var stream = lines.skip(offset);
                    // Apply explicit limit or default safety limit to prevent OOM
                    int effectiveLimit = (limit != null) ? limit : DEFAULT_READ_LIMIT;
                    // Take one extra line to detect if there are more lines beyond the limit
                    List<String> collected = stream.limit(effectiveLimit + 1).toList();
                    if (collected.size() > effectiveLimit) {
                        // More lines exist beyond the limit — return exactly effectiveLimit + marker
                        String content = String.join("\n", collected.subList(0, effectiveLimit));
                        return content + "\n\n... (truncated at " + effectiveLimit + " lines, " +
                                (offset + effectiveLimit) + " lines shown; file has more)";
                    }
                    return String.join("\n", collected);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> write(String path, String content) {
        return CompletableFuture.runAsync(() -> {
            try {
                Path resolved = resolveForWrite(path);
                Files.createDirectories(resolved.getParent());
                // Atomic write: write to temp file, then move
                Path temp = Files.createTempFile(resolved.getParent(), ".write_", ".tmp");
                try {
                    Files.writeString(temp, content);
                    Files.move(temp, resolved, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
                    throw e;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> edit(String path, String oldText, String newText) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path resolved = resolveForWrite(path);
                String content = Files.readString(resolved);
                if (!content.contains(oldText)) return false;
                String updated = content.replaceFirst(Pattern.quote(oldText), newText);
                // Atomic write: write to temp file, then move
                Path temp = Files.createTempFile(resolved.getParent(), ".edit_", ".tmp");
                try {
                    Files.writeString(temp, updated);
                    Files.move(temp, resolved, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    // Clean up temp file on failure
                    try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
                    throw e;
                }
                return true;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<List<FileInfo>> ls(String path) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path resolved = resolve(path != null ? path : ".");
                List<FileInfo> result = new ArrayList<>();
                try (var stream = Files.list(resolved)) {
                    stream.sorted().forEach(p -> {
                        try {
                            result.add(new FileInfo(
                                    p.getFileName().toString(),
                                    p.toString(),
                                    Files.isDirectory(p),
                                    Files.isDirectory(p) ? 0 : Files.size(p)
                            ));
                        } catch (IOException e) {
                            result.add(new FileInfo(p.getFileName().toString(), p.toString(), false, 0));
                        }
                    });
                }
                return result;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static final int MAX_GREP_RESULTS = 5000;
    private static final int MAX_FIND_RESULTS = 5000;
    private static final int MAX_WALK_DEPTH = 20;
    private static final long MAX_GREP_FILE_SIZE = 50_000_000L; // 50MB

    @Override
    public CompletableFuture<List<String>> grep(String pattern, String path, boolean recursive) {
        return grep(pattern, path, recursive, null);
    }

    @Override
    public CompletableFuture<List<String>> grep(String pattern, String path, boolean recursive, String include) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path resolved = resolve(path);
                if (pattern.length() > 200) {
                    throw new IllegalArgumentException("Pattern too long (max 200 characters)");
                }
                Pattern regex;
                try {
                    regex = Pattern.compile(pattern);
                } catch (PatternSyntaxException e) {
                    throw new IllegalArgumentException("Invalid regex pattern: " + e.getMessage());
                }
                // Build filename matcher from include glob (null means match all)
                PathMatcher includeMatcher = (include != null && !include.isEmpty())
                        ? FileSystems.getDefault().getPathMatcher("glob:" + include)
                        : null;
                List<String> results = new ArrayList<>();
                if (Files.isRegularFile(resolved)) {
                    grepFile(resolved, regex, results);
                } else if (recursive) {
                    // Use iterator instead of forEach to allow early termination
                    try (var stream = Files.walk(resolved, MAX_WALK_DEPTH)) {
                        Iterator<Path> it = stream.filter(Files::isRegularFile).iterator();
                        while (it.hasNext() && results.size() < MAX_GREP_RESULTS) {
                            Path f = it.next();
                            if (includeMatcher != null && !includeMatcher.matches(f.getFileName())) continue;
                            grepFile(f, regex, results);
                        }
                    }
                } else {
                    try (var stream = Files.list(resolved)) {
                        Iterator<Path> it = stream.filter(Files::isRegularFile).iterator();
                        while (it.hasNext() && results.size() < MAX_GREP_RESULTS) {
                            Path f = it.next();
                            if (includeMatcher != null && !includeMatcher.matches(f.getFileName())) continue;
                            grepFile(f, regex, results);
                        }
                    }
                }
                if (results.size() >= MAX_GREP_RESULTS) {
                    results.add("... (truncated at " + MAX_GREP_RESULTS + " results)");
                }
                return results;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void grepFile(Path file, Pattern regex, List<String> results) {
        try {
            if (results.size() >= MAX_GREP_RESULTS) return;
            long fileSize = Files.size(file);
            if (fileSize > MAX_GREP_FILE_SIZE) return; // skip very large files
            try (Stream<String> lines = Files.lines(file)) {
                int[] lineNum = {0};
                lines.forEach(line -> {
                    lineNum[0]++;
                    if (results.size() < MAX_GREP_RESULTS && regex.matcher(line).find()) {
                        results.add(file + ":" + lineNum[0] + ":" + line);
                    }
                });
            }
        } catch (IOException e) {
            log.warn("Could not read file {}: {}", file, e.getMessage());
        }
    }

    @Override
    public CompletableFuture<List<String>> find(String path, String namePattern) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path resolved = resolve(path);
                List<String> results = new ArrayList<>();
                // Convert glob pattern to regex with proper escaping
                Pattern nameRegex = namePattern != null ? compileGlob(namePattern) : null;
                // Use iterator for early termination when max results reached
                try (var stream = Files.walk(resolved, MAX_WALK_DEPTH)) {
                    Iterator<Path> it = stream.filter(Files::isRegularFile).iterator();
                    while (it.hasNext() && results.size() < MAX_FIND_RESULTS) {
                        Path p = it.next();
                        String name = p.getFileName().toString();
                        if (nameRegex == null || nameRegex.matcher(name).matches()) {
                            results.add(p.toString());
                        }
                    }
                }
                if (results.size() >= MAX_FIND_RESULTS) {
                    results.add("... (truncated at " + MAX_FIND_RESULTS + " results)");
                }
                return results;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Convert a glob pattern to a compiled regex, escaping regex special characters.
     */
    private static Pattern compileGlob(String glob) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append(".");
                case '.', '(', ')', '+', '|', '^', '$', '{', '}', '[', ']', '\\' ->
                        sb.append('\\').append(c);
                default -> sb.append(c);
            }
        }
        return Pattern.compile(sb.toString());
    }
}
