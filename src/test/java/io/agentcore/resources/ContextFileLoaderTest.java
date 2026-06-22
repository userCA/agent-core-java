package io.agentcore.resources;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContextFileLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsEmptyListWhenNoFilesFound() throws IOException {
        Path subDir = Files.createDirectory(tempDir.resolve("sub"));
        ContextFileLoader loader = new ContextFileLoader();
        List<ContextFileLoader.ContextFile> files = loader.load(subDir);
        assertTrue(files.isEmpty());
    }

    @Test
    void loadsContextFileFromCwd() throws IOException {
        Path cwdFile = tempDir.resolve("AGENTS.md");
        Files.writeString(cwdFile, "# Agents context\nHello from AGENTS.md");

        ContextFileLoader loader = new ContextFileLoader();
        List<ContextFileLoader.ContextFile> files = loader.load(tempDir);

        assertEquals(1, files.size());
        assertEquals(cwdFile.toString(), files.get(0).path());
        assertEquals("# Agents context\nHello from AGENTS.md", files.get(0).content());
        assertEquals("cwd", files.get(0).source());
    }

    @Test
    void loadsMultipleContextFiles() throws IOException {
        Files.writeString(tempDir.resolve("AGENTS.md"), "Agents content");
        Files.writeString(tempDir.resolve("CLAUDE.md"), "Claude content");

        ContextFileLoader loader = new ContextFileLoader();
        List<ContextFileLoader.ContextFile> files = loader.load(tempDir);

        assertEquals(2, files.size());
        assertTrue(files.get(0).content().contains("Agents"));
        assertTrue(files.get(1).content().contains("Claude"));
    }

    @Test
    void loadsFromAncestorDirectory() throws IOException {
        Files.writeString(tempDir.resolve("AGENTS.md"), "Ancestor content");
        Path subDir = Files.createDirectories(tempDir.resolve("a").resolve("b"));

        ContextFileLoader loader = new ContextFileLoader();
        List<ContextFileLoader.ContextFile> files = loader.load(subDir);

        assertEquals(1, files.size());
        assertEquals("ancestor", files.get(0).source());
        assertTrue(files.get(0).content().contains("Ancestor"));
    }

    @Test
    void stopsAtGitBoundary() throws IOException {
        Files.writeString(tempDir.resolve("AGENTS.md"), "Root content");
        Files.createDirectory(tempDir.resolve(".git"));
        Path workDir = Files.createDirectories(tempDir.resolve("subproject").resolve("work"));
        Files.writeString(workDir.resolve("AGENTS.md"), "Deep content");

        ContextFileLoader loader = new ContextFileLoader();
        List<ContextFileLoader.ContextFile> files = loader.load(workDir);

        // Should find the deep file (cwd) and root file (ancestor), but not beyond .git
        assertEquals(2, files.size());
        assertTrue(files.get(0).content().contains("Deep"));
        assertTrue(files.get(1).content().contains("Root"));
    }

    @Test
    void customFilenamesOverrideDefaults() throws IOException {
        Files.writeString(tempDir.resolve("CUSTOM.md"), "Custom content");

        ContextFileLoader loader = new ContextFileLoader(List.of("CUSTOM.md"), 5);
        List<ContextFileLoader.ContextFile> files = loader.load(tempDir);

        assertEquals(1, files.size());
        assertTrue(files.get(0).content().contains("Custom"));
    }

    @Test
    void maxDepthLimitsTraversal() throws IOException {
        Files.writeString(tempDir.resolve("AGENTS.md"), "Root content");
        Path deepPath = tempDir;
        for (int i = 0; i < 30; i++) {
            deepPath = Files.createDirectory(deepPath.resolve("level" + i));
        }

        ContextFileLoader loader = new ContextFileLoader(List.of("AGENTS.md", "CLAUDE.md"), 5);
        List<ContextFileLoader.ContextFile> files = loader.load(deepPath);

        // With maxDepth=5, should not reach the root AGENTS.md
        assertTrue(files.isEmpty());
    }

    @Test
    void handlesMissingFilesGracefully() {
        ContextFileLoader loader = new ContextFileLoader();
        assertDoesNotThrow(() -> loader.load(tempDir));
    }

    @Test
    void constructorCopiesFilenameList() {
        List<String> names = List.of("A.md", "B.md");
        ContextFileLoader loader = new ContextFileLoader(names, 5);
        // Modifying original list should not affect loader
        assertDoesNotThrow(() -> loader.load(tempDir));
    }

    @Test
    void nullFilenamesUsesDefaults() {
        ContextFileLoader loader = new ContextFileLoader(null, 5);
        assertDoesNotThrow(() -> loader.load(tempDir));
    }
}
