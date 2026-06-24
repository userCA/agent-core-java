package io.agentcore.skill;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Skill and SkillLoader.
 */
class SkillLoaderTest {

    @TempDir
    Path tempDir;

    // ── Skill record ─────────────────────────────────────────

    @Test
    void skillRequiresName() {
        assertThrows(IllegalArgumentException.class, () ->
                new Skill("", "desc", "/path", "/base", false));
    }

    @Test
    void skillDefaults() {
        var skill = new Skill("test", "desc", null, null, false);
        assertEquals("", skill.filePath());
        assertEquals("", skill.baseDir());
        assertFalse(skill.disableModelInvocation());
    }

    // ── Validation ───────────────────────────────────────────

    @Test
    void validateNameCorrect() {
        var errors = SkillLoader.validateName("my-skill", "my-skill");
        assertTrue(errors.isEmpty());
    }

    @Test
    void validateNameMismatch() {
        var errors = SkillLoader.validateName("other-name", "my-skill");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).contains("does not match"));
    }

    @Test
    void validateNameUppercase() {
        var errors = SkillLoader.validateName("MySkill", "MySkill");
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("invalid characters")));
    }

    @Test
    void validateNameLeadingHyphen() {
        var errors = SkillLoader.validateName("-bad", "-bad");
        assertFalse(errors.isEmpty());
    }

    @Test
    void validateNameDoubleHyphen() {
        var errors = SkillLoader.validateName("bad--name", "bad--name");
        assertFalse(errors.isEmpty());
    }

    @Test
    void validateNameTooLong() {
        String longName = "a".repeat(65);
        var errors = SkillLoader.validateName(longName, longName);
        assertTrue(errors.stream().anyMatch(e -> e.contains("exceeds")));
    }

    @Test
    void validateDescriptionCorrect() {
        var errors = SkillLoader.validateDescription("A valid description");
        assertTrue(errors.isEmpty());
    }

    @Test
    void validateDescriptionEmpty() {
        var errors = SkillLoader.validateDescription("");
        assertFalse(errors.isEmpty());
    }

    @Test
    void validateDescriptionNull() {
        var errors = SkillLoader.validateDescription(null);
        assertFalse(errors.isEmpty());
    }

    @Test
    void validateDescriptionTooLong() {
        String longDesc = "x".repeat(1025);
        var errors = SkillLoader.validateDescription(longDesc);
        assertTrue(errors.stream().anyMatch(e -> e.contains("exceeds")));
    }

    // ── Frontmatter parsing ──────────────────────────────────

    @Test
    void parseFrontmatterBasic() {
        String content = "---\nname: my-skill\ndescription: A test skill\n---\nBody content here";
        var result = SkillLoader.parseFrontmatter(content);
        @SuppressWarnings("unchecked")
        Map<String, Object> fm = (Map<String, Object>) result.get("frontmatter");
        assertEquals("my-skill", fm.get("name"));
        assertEquals("A test skill", fm.get("description"));
        assertEquals("Body content here", result.get("body"));
    }

    @Test
    void parseFrontmatterBooleans() {
        String content = "---\ndescription: test\ndisable-model-invocation: true\n---\nbody";
        var result = SkillLoader.parseFrontmatter(content);
        @SuppressWarnings("unchecked")
        Map<String, Object> fm = (Map<String, Object>) result.get("frontmatter");
        assertEquals(true, fm.get("disable-model-invocation"));
    }

    @Test
    void parseFrontmatterNoFrontmatter() {
        String content = "Just plain markdown content";
        var result = SkillLoader.parseFrontmatter(content);
        @SuppressWarnings("unchecked")
        Map<String, Object> fm = (Map<String, Object>) result.get("frontmatter");
        assertTrue(fm.isEmpty());
        assertEquals(content, result.get("body"));
    }

    @Test
    void parseFrontmatterNull() {
        var result = SkillLoader.parseFrontmatter(null);
        @SuppressWarnings("unchecked")
        Map<String, Object> fm = (Map<String, Object>) result.get("frontmatter");
        assertTrue(fm.isEmpty());
    }

    // ── Loading ──────────────────────────────────────────────

    @Test
    void loadSkillFromFile() throws IOException {
        Path skillDir = tempDir.resolve("my-skill");
        Files.createDirectories(skillDir);
        Path skillFile = skillDir.resolve("SKILL.md");
        Files.writeString(skillFile,
                "---\ndescription: A test skill for unit testing\n---\n# My Skill\nInstructions here.");

        var result = SkillLoader.loadSkillFromFile(skillFile);
        assertNotNull(result.skill());
        assertEquals("my-skill", result.skill().name());
        assertEquals("A test skill for unit testing", result.skill().description());
        assertTrue(result.skill().filePath().endsWith("SKILL.md"));
    }

    @Test
    void loadSkillFromFileNoDescription() throws IOException {
        Path skillDir = tempDir.resolve("no-desc");
        Files.createDirectories(skillDir);
        Path skillFile = skillDir.resolve("SKILL.md");
        Files.writeString(skillFile, "---\nname: no-desc\n---\nBody");

        var result = SkillLoader.loadSkillFromFile(skillFile);
        assertNull(result.skill());
    }

    @Test
    void loadSkillFromFileMissing() {
        var result = SkillLoader.loadSkillFromFile(tempDir.resolve("nonexistent.md"));
        assertNull(result.skill());
        assertFalse(result.diagnostics().isEmpty());
    }

    @Test
    void loadSkillsFromDir() throws IOException {
        // Create two skills
        Path dir1 = tempDir.resolve("skill-one");
        Files.createDirectories(dir1);
        Files.writeString(dir1.resolve("SKILL.md"),
                "---\ndescription: First skill\n---\nContent 1");

        Path dir2 = tempDir.resolve("skill-two");
        Files.createDirectories(dir2);
        Files.writeString(dir2.resolve("SKILL.md"),
                "---\ndescription: Second skill\n---\nContent 2");

        var result = SkillLoader.loadSkillsFromDir(tempDir);
        assertEquals(2, result.skills().size());
    }

    @Test
    void loadSkillsFromDirEmpty() {
        var result = SkillLoader.loadSkillsFromDir(tempDir);
        assertTrue(result.skills().isEmpty());
    }

    @Test
    void loadSkillsFromNonexistentDir() {
        var result = SkillLoader.loadSkillsFromDir(tempDir.resolve("nonexistent"));
        assertTrue(result.skills().isEmpty());
    }

    @Test
    void loadSkillsCollisionDetection() throws IOException {
        // Create two dirs with same skill name
        Path dir1 = tempDir.resolve("dup-skill");
        Files.createDirectories(dir1);
        Files.writeString(dir1.resolve("SKILL.md"),
                "---\nname: dup-skill\ndescription: First\n---\nContent 1");

        // loadSkills with both dirs
        var result = SkillLoader.loadSkills(tempDir, tempDir,
                List.of(dir1, dir1), false);

        // Should detect collision
        assertTrue(result.diagnostics().stream()
                .anyMatch(d -> "collision".equals(d.get("type"))));
    }

    // ── Formatting ───────────────────────────────────────────

    @Test
    void formatSkillsForPrompt() {
        var skills = List.of(
                new Skill("skill-a", "Does A", "/path/a/SKILL.md", "/path/a", false),
                new Skill("skill-b", "Does B", "/path/b/SKILL.md", "/path/b", false)
        );
        String result = SkillLoader.formatSkillsForPrompt(skills);
        assertTrue(result.contains("<available_skills>"));
        assertTrue(result.contains("<name>skill-a</name>"));
        assertTrue(result.contains("<name>skill-b</name>"));
        assertTrue(result.contains("<description>Does A</description>"));
        assertTrue(result.contains("</available_skills>"));
    }

    @Test
    void formatSkillsForPromptHidesDisabled() {
        var skills = List.of(
                new Skill("visible", "Visible skill", "/v", "/v", false),
                new Skill("hidden", "Hidden skill", "/h", "/h", true)
        );
        String result = SkillLoader.formatSkillsForPrompt(skills);
        assertTrue(result.contains("visible"));
        assertFalse(result.contains("hidden"));
    }

    @Test
    void formatSkillsForPromptEmpty() {
        String result = SkillLoader.formatSkillsForPrompt(List.of());
        assertEquals("", result);
    }

    @Test
    void escapeXml() {
        assertEquals("&amp;&lt;&gt;&quot;&apos;",
                SkillLoader.escapeXml("&<>\"'"));
    }

    @Test
    void escapeXmlNull() {
        assertEquals("", SkillLoader.escapeXml(null));
    }
}
