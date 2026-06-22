package io.agentcore.resources;

import io.agentcore.resources.ResourceTypes.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for resources package: ResourceLoader, ThemeLoader, PromptLoader,
 * ExtensionSpecLoader, DiagnosticsCollector.
 */
class ResourceLoaderTest {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("DiagnosticsCollector")
    class DiagnosticsTests {

        @Test
        void emptyCollector() {
            var dc = new DiagnosticsCollector();
            assertTrue(dc.isEmpty());
            assertEquals(0, dc.size());
        }

        @Test
        void addWarning() {
            var dc = new DiagnosticsCollector();
            dc.warning("test warning", "/path/to/file");
            assertEquals(1, dc.size());
            assertEquals(ResourceDiagnostic.Type.WARNING, dc.getItems().get(0).type());
            assertEquals("test warning", dc.getItems().get(0).message());
        }

        @Test
        void addCollision() {
            var dc = new DiagnosticsCollector();
            dc.collision("name collision", "/winner", "/loser");
            var item = dc.getItems().get(0);
            assertEquals(ResourceDiagnostic.Type.COLLISION, item.type());
            assertEquals("/winner", item.winnerPath());
            assertEquals("/loser", item.loserPath());
        }

        @Test
        void clearCollector() {
            var dc = new DiagnosticsCollector();
            dc.warning("w1", "");
            dc.warning("w2", "");
            assertEquals(2, dc.size());
            dc.clear();
            assertTrue(dc.isEmpty());
        }
    }

    @Nested
    @DisplayName("ThemeLoader")
    class ThemeTests {

        @Test
        void loadValidTheme() throws IOException {
            Path themeFile = tempDir.resolve("theme.json");
            Files.writeString(themeFile, "{\"name\":\"dark\",\"colors\":{\"bg\":\"#000\"}}");

            var diag = new DiagnosticsCollector();
            Theme theme = ThemeLoader.loadThemeFromFile(themeFile.toString(), diag);

            assertNotNull(theme);
            assertEquals("dark", theme.name());
            assertTrue(diag.isEmpty());
        }

        @Test
        void loadInvalidJson() throws IOException {
            Path themeFile = tempDir.resolve("bad.json");
            Files.writeString(themeFile, "not json");

            var diag = new DiagnosticsCollector();
            Theme theme = ThemeLoader.loadThemeFromFile(themeFile.toString(), diag);

            assertNull(theme);
            assertEquals(1, diag.size());
        }

        @Test
        void missingNameField() throws IOException {
            Path themeFile = tempDir.resolve("noname.json");
            Files.writeString(themeFile, "{\"colors\":{}}");

            var diag = new DiagnosticsCollector();
            Theme theme = ThemeLoader.loadThemeFromFile(themeFile.toString(), diag);

            assertNull(theme);
            assertTrue(diag.getItems().stream().anyMatch(d -> d.message().contains("name")));
        }

        @Test
        void nonExistentFile() {
            var diag = new DiagnosticsCollector();
            Theme theme = ThemeLoader.loadThemeFromFile("/nonexistent/file.json", diag);
            assertNull(theme);
            assertEquals(1, diag.size());
        }
    }

    @Nested
    @DisplayName("PromptLoader")
    class PromptTests {

        @Test
        void loadValidPrompt() throws IOException {
            Path file = tempDir.resolve("test.md");
            Files.writeString(file, """
                    ---
                    name: greeting
                    description: A greeting prompt
                    parameters:
                      - user_name
                      - project
                    ---
                    Hello {{user_name}}, welcome to {{project}}.
                    """);

            var diag = new DiagnosticsCollector();
            PromptTemplate pt = PromptLoader.loadPromptFromFile(file.toString(), diag);

            assertNotNull(pt);
            assertEquals("greeting", pt.name());
            assertEquals("A greeting prompt", pt.description());
            assertTrue(pt.template().contains("Hello"));
            assertEquals(2, pt.parameters().size());
        }

        @Test
        void missingFrontmatter() throws IOException {
            Path file = tempDir.resolve("nofm.md");
            Files.writeString(file, "# Just a heading\nSome text");

            var diag = new DiagnosticsCollector();
            PromptTemplate pt = PromptLoader.loadPromptFromFile(file.toString(), diag);

            assertNull(pt);
            assertEquals(1, diag.size());
        }

        @Test
        void inlineListParams() throws IOException {
            Path file = tempDir.resolve("inline.md");
            Files.writeString(file, """
                    ---
                    name: inline-test
                    parameters: [a, b, c]
                    ---
                    Body text
                    """);

            var diag = new DiagnosticsCollector();
            PromptTemplate pt = PromptLoader.loadPromptFromFile(file.toString(), diag);

            assertNotNull(pt);
            assertEquals(3, pt.parameters().size());
            assertTrue(pt.parameters().contains("a"));
        }
    }

    @Nested
    @DisplayName("ExtensionSpecLoader")
    class ExtensionSpecTests {

        @Test
        void discoverExtensionJson() throws IOException {
            Path extDir = tempDir.resolve("extensions");
            Files.createDirectories(extDir);
            Files.writeString(extDir.resolve("MyExtension.json"), "{\"name\":\"test\"}");

            var diag = new DiagnosticsCollector();
            var specs = ExtensionSpecLoader.discoverSpecs(
                    List.of(extDir.toString()), diag);

            assertEquals(1, specs.size());
            assertTrue(specs.get(0).name().contains("MyExtension"));
        }

        @Test
        void emptyDir() throws IOException {
            Path extDir = tempDir.resolve("empty_ext");
            Files.createDirectories(extDir);

            var diag = new DiagnosticsCollector();
            var specs = ExtensionSpecLoader.discoverSpecs(
                    List.of(extDir.toString()), diag);

            assertTrue(specs.isEmpty());
        }

        @Test
        void nonExistentDir() {
            var diag = new DiagnosticsCollector();
            var specs = ExtensionSpecLoader.discoverSpecs(
                    List.of("/nonexistent/dir"), diag);
            assertTrue(specs.isEmpty());
        }
    }

    @Nested
    @DisplayName("ResourceLoader")
    class ResourceLoaderTests {

        @Test
        void defaultConstructor() {
            var loader = new ResourceLoader();
            assertNotNull(loader.getCwd());
        }

        @Test
        void loadSkillsFromEmptyDir() throws IOException {
            Path skillsDir = tempDir.resolve(".pi").resolve("skills");
            Files.createDirectories(skillsDir);

            var loader = new ResourceLoader(tempDir, List.of(), List.of(), List.of());
            var result = loader.loadSkills();

            assertNotNull(result);
            assertTrue(result.skills().isEmpty());
        }

        @Test
        void loadThemesFromDir() throws IOException {
            Path themesDir = tempDir.resolve(".pi").resolve("themes");
            Files.createDirectories(themesDir);
            Files.writeString(themesDir.resolve("dark.json"), "{\"name\":\"dark\",\"bg\":\"#000\"}");
            Files.writeString(themesDir.resolve("light.json"), "{\"name\":\"light\",\"bg\":\"#fff\"}");

            var loader = new ResourceLoader(tempDir, List.of(), List.of(), List.of());
            var result = loader.loadThemes();

            assertEquals(2, result.themes().size());
        }

        @Test
        void loadPromptsFromDir() throws IOException {
            Path promptsDir = tempDir.resolve(".pi").resolve("prompts");
            Files.createDirectories(promptsDir);
            Files.writeString(promptsDir.resolve("greeting.md"), """
                    ---
                    name: greeting
                    description: Hello
                    ---
                    Hello world
                    """);

            var loader = new ResourceLoader(tempDir, List.of(), List.of(), List.of());
            var result = loader.loadPromptTemplates();

            assertEquals(1, result.prompts().size());
            assertEquals("greeting", result.prompts().get(0).name());
        }

        @Test
        void loadContextFiles() throws IOException {
            Files.writeString(tempDir.resolve("CLAUDE.md"), "# Project Context");

            var loader = new ResourceLoader(tempDir, List.of(), List.of(), List.of());
            var contextFiles = loader.loadContextFiles();

            assertFalse(contextFiles.isEmpty());
            assertTrue(contextFiles.get(0).content().contains("Project Context"));
        }
    }
}
