package io.agentcore.tools.shell;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

/**
 * Local filesystem implementation of {@link FileOperations} with
 * path traversal prevention and atomic write semantics.
 *
 * <p>All paths are resolved against a working directory and validated
 * before access. Write operations additionally enforce an optional
 * allowlist. Read operations enforce line/file-size limits to prevent OOM.
 */
public class LocalFileOperations implements FileOperations {

    private static final Logger log = LoggerFactory.getLogger(LocalFileOperations.class);

    /** Default line limit for read() to prevent OOM. */
    public static final int DEFAULT_READ_LIMIT = 2000;

    /** Maximum grep results before truncation. */
    public static final int MAX_GREP_RESULTS = 5000;

    /** Maximum find results before truncation. */
    public static final int MAX_FIND_RESULTS = 5000;

    /** Maximum directory walk depth. */
    public static final int MAX_WALK_DEPTH = 20;

    /** Maximum file size eligible for grep (50 MB). */
    public static final long MAX_GREP_FILE_SIZE = 50_000_000L;

    private final Path cwd;
    private final SandboxQuota quota;

    public LocalFileOperations(Path cwd, SandboxQuota quota) {
        this.cwd = cwd.toAbsolutePath().normalize();
        this.quota = quota;
    }

    public LocalFileOperations(Path cwd) {
        this(cwd, null);
    }

    public LocalFileOperations() {
        this(Path.of("").toAbsolutePath());
    }

    @Override
    public Path cwd() {
        return cwd;
    }

    // ── Read ──

    @Override
    public String read(String path, int offset, Integer limit) throws IOException {
        Path resolved = resolve(path);
        try (Stream<String> lines = Files.lines(resolved)) {
            var stream = lines.skip(offset);
            int effectiveLimit = (limit != null) ? limit : DEFAULT_READ_LIMIT;
            List<String> collected = stream.limit(effectiveLimit + 1).toList();
            if (collected.size() > effectiveLimit) {
                String content = String.join("\n", collected.subList(0, effectiveLimit));
                return content + "\n\n... (truncated at " + effectiveLimit + " lines, "
                        + (offset + effectiveLimit) + " lines shown; file has more)";
            }
            return String.join("\n", collected);
        }
    }

    // ── Write ──

    @Override
    public void write(String path, String content) throws IOException {
        Path resolved = resolveForWrite(path);
        Files.createDirectories(resolved.getParent());
        Path temp = Files.createTempFile(resolved.getParent(), ".write_", ".tmp");
        try {
            Files.writeString(temp, content);
            Files.move(temp, resolved,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try { Files.deleteIfExists(temp); } catch (IOException ex) {
                log.debug("Failed to delete temp file {}", temp, ex);
            }
            throw e;
        }
    }

    // ── Edit ──

    @Override
    public boolean edit(String path, String oldText, String newText) throws IOException {
        Path resolved = resolveForWrite(path);
        String content = Files.readString(resolved);
        if (!content.contains(oldText)) return false;
        String updated = content.replaceFirst(Pattern.quote(oldText), newText);
        Path temp = Files.createTempFile(resolved.getParent(), ".edit_", ".tmp");
        try {
            Files.writeString(temp, updated);
            Files.move(temp, resolved,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try { Files.deleteIfExists(temp); } catch (IOException ex) {
                log.debug("Failed to delete temp file {}", temp, ex);
            }
            throw e;
        }
        return true;
    }

    // ── Ls ──

    @Override
    public List<FileInfo> ls(String path) throws IOException {
        Path resolved = resolve(path != null ? path : ".");
        List<FileInfo> result = new ArrayList<>();
        try (var stream = Files.list(resolved)) {
            stream.sorted().forEach(p -> {
                try {
                    boolean isDir = Files.isDirectory(p);
                    result.add(new FileInfo(
                            p.getFileName().toString(),
                            p.toString(),
                            isDir,
                            isDir ? 0 : Files.size(p)
                    ));
                } catch (IOException e) {
                    result.add(new FileInfo(
                            p.getFileName().toString(), p.toString(), false, 0));
                }
            });
        }
        return result;
    }

    // ── Grep ──

    @Override
    public List<String> grep(String pattern, String path,
                             boolean recursive, String include) throws IOException {
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

        PathMatcher includeMatcher = (include != null && !include.isEmpty())
                ? FileSystems.getDefault().getPathMatcher("glob:" + include)
                : null;

        List<String> results = new ArrayList<>();
        if (Files.isRegularFile(resolved)) {
            grepFile(resolved, regex, results);
        } else if (recursive) {
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
    }

    private void grepFile(Path file, Pattern regex, List<String> results) {
        try {
            if (results.size() >= MAX_GREP_RESULTS) return;
            long fileSize = Files.size(file);
            if (fileSize > MAX_GREP_FILE_SIZE) return;
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

    // ── Find ──

    @Override
    public List<String> find(String path, String namePattern) throws IOException {
        Path resolved = resolve(path);
        List<String> results = new ArrayList<>();
        Pattern nameRegex = namePattern != null ? compileGlob(namePattern) : null;
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
    }

    // ── Path resolution ──

    Path resolve(String path) {
        List<String> denyPaths = quota != null ? quota.denyPaths() : null;
        return SecurityUtils.resolvePath(path, cwd, denyPaths);
    }

    Path resolveForWrite(String path) {
        List<String> denyPaths = quota != null ? quota.denyPaths() : null;
        List<String> allowWritePaths = quota != null ? quota.allowWritePaths() : null;
        return SecurityUtils.resolveForWrite(path, cwd, denyPaths, allowWritePaths);
    }

    // ── Glob helper ──

    static Pattern compileGlob(String glob) {
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
