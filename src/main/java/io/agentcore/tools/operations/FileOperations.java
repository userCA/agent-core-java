package io.agentcore.tools.operations;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Pluggable file operations protocol — allows swapping execution backends
 * (local, SSH, Docker container, etc.) without changing tool implementations.
 */
public interface FileOperations {
    CompletableFuture<String> read(String path, int offset, Integer limit);
    CompletableFuture<Void> write(String path, String content);
    CompletableFuture<Boolean> edit(String path, String oldText, String newText);
    CompletableFuture<List<FileInfo>> ls(String path);
    CompletableFuture<List<String>> grep(String pattern, String path, boolean recursive);

    /**
     * Search for regex pattern matches in files, with optional filename glob filter.
     *
     * @param include glob pattern to filter which filenames are searched (null means all files)
     */
    default CompletableFuture<List<String>> grep(String pattern, String path, boolean recursive, String include) {
        return grep(pattern, path, recursive);
    }

    CompletableFuture<List<String>> find(String path, String namePattern);
}
