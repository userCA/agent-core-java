package io.agentcore.resources;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads project context files (AGENTS.md, CLAUDE.md, etc.) from the working
 * directory upward to ancestor directories.
 *
 * <p>Walks from {@code cwd} upward, collecting context files at each level.
 * Nearest files come first. Stops at a {@code .git} directory boundary or
 * after reaching {@code maxDepth}.
 */
public final class ContextFileLoader {

    private static final Logger log = LoggerFactory.getLogger(ContextFileLoader.class);
    private static final List<String> DEFAULT_FILENAMES = List.of("AGENTS.md", "CLAUDE.md");
    private static final int DEFAULT_MAX_DEPTH = 20;

    private final List<String> filenames;
    private final int maxDepth;

    public ContextFileLoader() {
        this(DEFAULT_FILENAMES, DEFAULT_MAX_DEPTH);
    }

    public ContextFileLoader(List<String> filenames, int maxDepth) {
        this.filenames = filenames != null ? List.copyOf(filenames) : DEFAULT_FILENAMES;
        this.maxDepth = maxDepth;
    }

    /**
     * Context file loaded from disk.
     *
     * @param path    absolute file path
     * @param content file content
     * @param source  "cwd" for the starting directory, "ancestor" for parents
     */
    public record ContextFile(String path, String content, String source) {}

    /**
     * Walk from {@code cwd} upward, collecting context files.
     *
     * @param cwd the starting working directory
     * @return list of context files, nearest first
     */
    public List<ContextFile> load(Path cwd) {
        List<ContextFile> results = new ArrayList<>();
        Path current = cwd.toAbsolutePath().normalize();
        int depth = 0;

        while (current != null && depth < maxDepth) {
            for (String name : filenames) {
                Path file = current.resolve(name);
                if (Files.isRegularFile(file)) {
                    try {
                        String content = Files.readString(file, StandardCharsets.UTF_8);
                        String source = depth == 0 ? "cwd" : "ancestor";
                        results.add(new ContextFile(file.toString(), content, source));
                    } catch (IOException e) {
                        log.debug("Failed to read context file {}: {}", file, e.getMessage());
                    }
                }
            }

            // Stop at .git boundary
            if (Files.isDirectory(current.resolve(".git"))) {
                break;
            }

            Path parent = current.getParent();
            if (parent == null || parent.equals(current)) {
                break;
            }
            current = parent;
            depth++;
        }

        return results;
    }
}
