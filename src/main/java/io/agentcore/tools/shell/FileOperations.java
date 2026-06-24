package io.agentcore.tools.shell;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Abstraction for sandboxed filesystem operations.
 *
 * <p>Implementations must enforce path traversal prevention and
 * optional write-allowlist restrictions. Read operations should
 * enforce line/file-size limits to prevent OOM.
 *
 * @see LocalFileOperations
 */
public interface FileOperations {

    /** The working directory that relative paths resolve against. */
    Path cwd();

    /**
     * Read file lines with offset/limit and streaming truncation.
     *
     * @param path   file path (relative to cwd or absolute)
     * @param offset number of lines to skip
     * @param limit  max lines to return (null → default limit)
     * @return file content as string, possibly truncated with a marker
     * @throws IOException if the file cannot be read
     */
    String read(String path, int offset, Integer limit) throws IOException;

    /**
     * Write content to a file. Creates parent directories if needed.
     * Implementations should use atomic write semantics.
     *
     * @param path    file path
     * @param content full file content
     * @throws IOException if write fails
     */
    void write(String path, String content) throws IOException;

    /**
     * Replace the first occurrence of {@code oldText} with {@code newText}.
     *
     * @return {@code true} if the replacement was made
     * @throws IOException if read/write fails
     */
    boolean edit(String path, String oldText, String newText) throws IOException;

    /**
     * List directory contents.
     *
     * @param path directory path (null → cwd)
     * @return sorted list of file entries
     * @throws IOException if the directory cannot be listed
     */
    List<FileInfo> ls(String path) throws IOException;

    /**
     * Search file contents with regex.
     *
     * @param pattern   regex pattern (max 200 chars)
     * @param path      file or directory to search
     * @param recursive whether to search recursively
     * @param include   filename glob filter (null → match all)
     * @return matching lines as "file:lineNum:content"
     * @throws IOException if files cannot be read
     */
    List<String> grep(String pattern, String path, boolean recursive, String include) throws IOException;

    /**
     * Find files by name pattern (glob).
     *
     * @param path        root directory
     * @param namePattern glob pattern (null → match all)
     * @return list of matching file paths
     * @throws IOException if the directory cannot be walked
     */
    List<String> find(String path, String namePattern) throws IOException;
}
