package io.agentcore.tools.operations;

/**
 * File metadata returned by ls/find operations.
 */
public record FileInfo(
        String name,
        String path,
        boolean isDir,
        long size
) {}
