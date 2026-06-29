package io.agentcore.prompt;

import io.agentcore.resources.ContextFileLoader.ContextFile;
import io.agentcore.skill.Skill;
import io.agentcore.tools.ToolDefinition;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SystemPromptBuilderTest {

    @Nested
    class BasicBuild {

        @Test
        void emptyBuildProducesBaseAndMeta() {
            SystemPromptBuilder builder = new SystemPromptBuilder();
            SystemPrompt prompt = builder.build(null, null, null, null,
                    LocalDate.of(2025, 1, 15));

            assertNotNull(prompt.text());
            assertTrue(prompt.text().contains("helpful assistant"));
            assertTrue(prompt.text().contains("2025-01-15"));
            assertEquals(0, prompt.toolCount());
            assertEquals(0, prompt.skillCount());
            assertEquals(0, prompt.contextFileCount());
        }

        @Test
        void customBasePromptIsUsed() {
            SystemPromptBuilder builder = new SystemPromptBuilder("You are a code expert.");
            SystemPrompt prompt = builder.build(null, null, null, null);

            assertTrue(prompt.text().contains("code expert"));
        }

        @Test
        void cwdIncludedInMeta() {
            SystemPromptBuilder builder = new SystemPromptBuilder();
            SystemPrompt prompt = builder.build("/home/user/project", null, null, null);

            assertTrue(prompt.text().contains("/home/user/project"));
        }
    }

    @Nested
    class ToolSection {

        @Test
        void toolsAreListedAlphabetically() {
            ToolDefinition zTool = new ToolDefinition("zebra", "Z tool", Map.of());
            ToolDefinition aTool = new ToolDefinition("alpha", "A tool", Map.of());

            SystemPromptBuilder builder = new SystemPromptBuilder();
            SystemPrompt prompt = builder.build(null, List.of(zTool, aTool), null, null);

            int alphaIdx = prompt.text().indexOf("alpha");
            int zebraIdx = prompt.text().indexOf("zebra");
            assertTrue(alphaIdx < zebraIdx, "Tools should be sorted alphabetically");
            assertEquals(2, prompt.toolCount());
        }

        @Test
        void snippetExtractedFromDescription() {
            ToolDefinition tool = new ToolDefinition("test", "Does something cool. Then more.", Map.of());
            SystemPromptBuilder builder = new SystemPromptBuilder();
            SystemPrompt prompt = builder.build(null, List.of(tool), null, null);

            assertTrue(prompt.text().contains("Does something cool"));
        }

        @Test
        void snippetUsesPromptSnippetWhenAvailable() {
            ToolDefinition tool = new ToolDefinition("test", "Long description",
                    Map.of(), "Custom snippet", null, null);
            SystemPromptBuilder builder = new SystemPromptBuilder();
            SystemPrompt prompt = builder.build(null, List.of(tool), null, null);

            assertTrue(prompt.text().contains("Custom snippet"));
        }
    }

    @Nested
    class Guidelines {

        @Test
        void dynamicGuidelinesGeneratedForSearchTools() {
            ToolDefinition grep = new ToolDefinition("grep", "Search files", Map.of());
            ToolDefinition find = new ToolDefinition("find", "Find files", Map.of());
            ToolDefinition ls = new ToolDefinition("ls", "List directory", Map.of());

            SystemPromptBuilder builder = new SystemPromptBuilder();
            SystemPrompt prompt = builder.build(null, List.of(grep, find, ls), null, null);

            assertTrue(prompt.text().contains("Prefer grep/find/ls over bash"));
        }

        @Test
        void editGuidelineAppearsWhenEditToolPresent() {
            ToolDefinition edit = new ToolDefinition("edit", "Edit files", Map.of());

            SystemPromptBuilder builder = new SystemPromptBuilder();
            SystemPrompt prompt = builder.build(null, List.of(edit), null, null);

            assertTrue(prompt.text().contains("Always use 'edit'"));
        }
    }

    @Nested
    class SkillsAndContext {

        @Test
        void skillsIncludedInPrompt() {
            Skill skill = new Skill(
                    "code-review", "Review code quality", "", "/path/skill.md", "/path", false);

            SystemPromptBuilder builder = new SystemPromptBuilder();
            SystemPrompt prompt = builder.build(null, null, List.of(skill), null);

            assertTrue(prompt.text().contains("code-review"));
            assertTrue(prompt.text().contains("Review code quality"));
            assertEquals(1, prompt.skillCount());
        }

        @Test
        void disabledSkillsAreHidden() {
            Skill skill = new Skill(
                    "secret", "Internal skill", "", "/path/secret.md", "/path", true);

            SystemPromptBuilder builder = new SystemPromptBuilder();
            SystemPrompt prompt = builder.build(null, null, List.of(skill), null);

            assertFalse(prompt.text().contains("secret"));
            assertEquals(1, prompt.skillCount());
        }

        @Test
        void contextFilesIncluded() {
            ContextFile cf = new ContextFile("/project/CLAUDE.md", "# Project Guide", "cwd");

            SystemPromptBuilder builder = new SystemPromptBuilder();
            SystemPrompt prompt = builder.build(null, null, null, List.of(cf));

            assertTrue(prompt.text().contains("Project Guide"));
            assertTrue(prompt.text().contains("/project/CLAUDE.md"));
            assertEquals(1, prompt.contextFileCount());
        }
    }

    @Nested
    class SectionStructure {

        @Test
        void allSectionsPresent() {
            SystemPromptBuilder builder = new SystemPromptBuilder();
            SystemPrompt prompt = builder.build(null, null, null, null);

            List<SystemPromptSection> sections = prompt.sections();
            assertTrue(sections.stream().anyMatch(s -> s.name().equals("base")));
            assertTrue(sections.stream().anyMatch(s -> s.name().equals("tools")));
            assertTrue(sections.stream().anyMatch(s -> s.name().equals("meta")));
        }
    }
}
