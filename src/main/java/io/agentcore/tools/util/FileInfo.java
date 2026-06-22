package io.agentcore.tools.util;

/**
 * Metadata for a single file or directory entry.
 *
 * @param name  file/directory name (without path prefix)
 * @param path  full resolved path string
 * @param isDir whether the entry is a directory
 * @param size  file size in bytes (0 for directories)
 */
public record FileInfo(
        String name,
        String path,
        boolean isDir,
        long size
) {}
