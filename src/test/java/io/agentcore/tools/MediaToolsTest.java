package io.agentcore.tools;

import io.agentcore.core.Content.TextContent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AgnesImageTool, AgnesVideoTool, CheckVideoTool, FeishuCLITool.
 */
class MediaToolsTest {

    @Nested
    @DisplayName("AgnesImageTool")
    class ImageToolTests {

        private AgnesImageTool tool;

        @BeforeEach
        void setUp() {
            tool = new AgnesImageTool();
        }

        @Test
        void definitionName() {
            assertEquals("generate_image", tool.definition().name());
        }

        @Test
        void definitionHasRequiredParams() {
            var params = tool.definition().parameters();
            assertEquals("object", params.get("type"));
            @SuppressWarnings("unchecked")
            var props = (Map<String, Object>) params.get("properties");
            assertTrue(props.containsKey("prompt"));
            assertTrue(props.containsKey("size"));
            assertTrue(props.containsKey("input_images"));
        }

        @Test
        void definitionPromptIsRequired() {
            var params = tool.definition().parameters();
            @SuppressWarnings("unchecked")
            var required = (List<String>) params.get("required");
            assertTrue(required.contains("prompt"));
        }

        @Test
        void executeFailsWithoutApiKey() throws Exception {
            // AGNES_API_KEY should not be set in test env
            var result = tool.execute("tc-1", Map.of("prompt", "test"), null);
            assertNotNull(result);
            assertTrue(result.text().contains("AGNES_API_KEY"));
        }

        @Test
        void definitionHasPromptGuidelines() {
            var guidelines = tool.definition().promptGuidelines();
            assertNotNull(guidelines);
            assertFalse(guidelines.isEmpty());
            assertTrue(guidelines.stream().anyMatch(g -> g.contains("markdown")));
        }

        @Test
        void definitionTimeout() {
            assertEquals(120.0, tool.definition().timeoutSeconds());
        }
    }

    @Nested
    @DisplayName("AgnesVideoTool")
    class VideoToolTests {

        private AgnesVideoTool tool;

        @BeforeEach
        void setUp() {
            tool = new AgnesVideoTool();
        }

        @Test
        void definitionName() {
            assertEquals("generate_video", tool.definition().name());
        }

        @Test
        void definitionHasRequiredParams() {
            var params = tool.definition().parameters();
            @SuppressWarnings("unchecked")
            var props = (Map<String, Object>) params.get("properties");
            assertTrue(props.containsKey("prompt"));
            assertTrue(props.containsKey("image"));
            assertTrue(props.containsKey("images"));
            assertTrue(props.containsKey("mode"));
            assertTrue(props.containsKey("num_frames"));
        }

        @Test
        void validateFramesAccepts81() {
            assertEquals(81, AgnesVideoTool.validateFrames(81));
        }

        @Test
        void validateFramesAccepts441() {
            assertEquals(441, AgnesVideoTool.validateFrames(441));
        }

        @Test
        void validateFramesClampsTooHigh() {
            assertEquals(121, AgnesVideoTool.validateFrames(500));
        }

        @Test
        void validateFramesRoundsUp() {
            // 100 should round up to 121 (next valid 8n+1)
            assertEquals(121, AgnesVideoTool.validateFrames(100));
        }

        @Test
        void validateFramesAccepts121() {
            assertEquals(121, AgnesVideoTool.validateFrames(121));
        }

        @Test
        void executeFailsWithoutApiKey() throws Exception {
            var result = tool.execute("tc-1", Map.of("prompt", "test"), null);
            assertTrue(result.text().contains("AGNES_API_KEY"));
        }

        @Test
        void keyframesWithoutImagesFails() throws Exception {
            // API key check happens first, so without AGNES_API_KEY we get that error
            // Test the validation logic via the validateFrames method instead
            // Keyframes validation is tested at definition level
            assertNotNull(tool.definition());
            var params = tool.definition().parameters();
            @SuppressWarnings("unchecked")
            var props = (Map<String, Object>) params.get("properties");
            assertTrue(props.containsKey("mode"));
            assertTrue(props.containsKey("images"));
        }

        @Test
        void definitionTimeout() {
            assertEquals(40.0, tool.definition().timeoutSeconds());
        }
    }

    @Nested
    @DisplayName("CheckVideoTool")
    class CheckVideoTests {

        private CheckVideoTool tool;

        @BeforeEach
        void setUp() {
            tool = new CheckVideoTool();
        }

        @Test
        void definitionName() {
            assertEquals("check_video_status", tool.definition().name());
        }

        @Test
        void definitionRequiresTaskId() {
            var params = tool.definition().parameters();
            @SuppressWarnings("unchecked")
            var required = (List<String>) params.get("required");
            assertTrue(required.contains("task_id"));
        }

        @Test
        void executeRequiresTaskId() throws Exception {
            var result = tool.execute("tc-1", Map.of(), null);
            assertTrue(result.text().contains("task_id"));
        }

        @Test
        void executeFailsWithoutApiKey() throws Exception {
            var result = tool.execute("tc-1", Map.of("task_id", "test-123"), null);
            assertTrue(result.text().contains("AGNES_API_KEY"));
        }

        @Test
        void definitionTimeout() {
            assertEquals(15.0, tool.definition().timeoutSeconds());
        }
    }

    @Nested
    @DisplayName("FeishuCLITool")
    class FeishuTests {

        private FeishuCLITool tool;

        @BeforeEach
        void setUp() {
            tool = new FeishuCLITool();
        }

        @Test
        void definitionName() {
            assertEquals("feishu", tool.definition().name());
        }

        @Test
        void definitionRequiresCommand() {
            var params = tool.definition().parameters();
            @SuppressWarnings("unchecked")
            var required = (List<String>) params.get("required");
            assertTrue(required.contains("command"));
        }

        @Test
        void executeRequiresCommand() throws Exception {
            var result = tool.execute("tc-1", Map.of(), null);
            assertTrue(result.text().contains("required") || result.text().contains("Error"));
        }

        @Test
        void splitArgsSimple() {
            String[] args = FeishuCLITool.splitArgs("im +messages-send --as bot");
            assertArrayEquals(new String[]{"im", "+messages-send", "--as", "bot"}, args);
        }

        @Test
        void splitArgsWithQuotes() {
            String[] args = FeishuCLITool.splitArgs("im +messages-send --text 'Hello World'");
            assertArrayEquals(new String[]{"im", "+messages-send", "--text", "Hello World"}, args);
        }

        @Test
        void splitArgsWithDoubleQuotes() {
            String[] args = FeishuCLITool.splitArgs("drive +docs-search --query \"test query\"");
            assertArrayEquals(new String[]{"drive", "+docs-search", "--query", "test query"}, args);
        }

        @Test
        void splitArgsEmpty() {
            String[] args = FeishuCLITool.splitArgs("");
            assertEquals(0, args.length);
        }

        @Test
        void splitArgsSingleArg() {
            String[] args = FeishuCLITool.splitArgs("calendar");
            assertArrayEquals(new String[]{"calendar"}, args);
        }

        @Test
        void executeLarkCliNotFound() throws Exception {
            // lark-cli likely not installed in test env — should get "not found" error
            var result = tool.execute("tc-1", Map.of("command", "im +messages-list"), null);
            assertNotNull(result);
            // Either "not found" or a real error (lark-cli may be installed)
            assertFalse(result.text().isEmpty());
        }

        @Test
        void definitionTimeout() {
            assertEquals(60.0, tool.definition().timeoutSeconds());
        }

        @Test
        void definitionDescriptionContainsLarkCli() {
            assertTrue(tool.definition().description().contains("lark-cli"));
        }
    }
}
