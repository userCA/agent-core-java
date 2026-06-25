package io.agentcore.tools;

import io.agentcore.extensions.SandboxPolicyExtension;
import io.agentcore.extensions.SelfHealingExtension;
import io.agentcore.model.Content.TextContent;
import io.agentcore.tools.builtin.ReadTool;
import io.agentcore.model.Content.ToolCallContent;
import io.agentcore.extensions.HookTypes.*;
import io.agentcore.tools.shell.SandboxQuota;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import io.agentcore.model.ToolResult;
import io.agentcore.model.Content;

/**
 * Tests for tool extensions: SandboxPolicyExtension, SelfHealingExtension,
 * ReadTool correctness, BashTool correctness, and RenderedOutput.
 */
class ToolExtensionsTest {

    // ── SandboxPolicyExtension ────────────────────────────────

    @Nested
    class SandboxPolicyTest {

        private final SandboxPolicyExtension policy = new SandboxPolicyExtension();

        @Test
        void blocksDangerousCommands() {
            var tc = new ToolCallContent("tc1", "bash",
                    Map.of("command", "rm -rf /"));
            var ctx = new ToolCallContext(tc, tc.arguments());

            var result = policy.beforeToolCall(ctx);
            assertNotNull(result);
            assertInstanceOf(ToolCallHookResult.Block.class, result);
        }

        @Test
        void blocksShutdown() {
            var tc = new ToolCallContent("tc1", "bash",
                    Map.of("command", "shutdown -h now"));
            var ctx = new ToolCallContext(tc, tc.arguments());

            var result = policy.beforeToolCall(ctx);
            assertNotNull(result);
            assertInstanceOf(ToolCallHookResult.Block.class, result);
        }

        @Test
        void allowsNormalCommands() {
            var tc = new ToolCallContent("tc1", "bash",
                    Map.of("command", "ls -la"));
            var ctx = new ToolCallContext(tc, tc.arguments());

            var result = policy.beforeToolCall(ctx);
            // Proceed or InjectMetadata (not Block)
            if (result != null) {
                assertFalse(result instanceof ToolCallHookResult.Block);
            }
        }

        @Test
        void ignoresNonBashTools() {
            var tc = new ToolCallContent("tc1", "read",
                    Map.of("path", "/etc/passwd"));
            var ctx = new ToolCallContext(tc, tc.arguments());

            var result = policy.beforeToolCall(ctx);
            assertNull(result);
        }

        @Test
        void pickQuotaDefault() {
            SandboxQuota quota = policy.pickQuota("echo hello");
            assertEquals(60, quota.timeoutSeconds());
            assertFalse(quota.networkAllowed());
        }

        @Test
        void pickQuotaInstallPackage() {
            SandboxQuota quota = policy.pickQuota("pip install numpy");
            assertTrue(quota.networkAllowed());
            assertEquals(512, quota.memoryMb());
        }

        @Test
        void pickQuotaDataAnalysis() {
            SandboxQuota quota = policy.pickQuota("python -c 'import pandas as pd'");
            assertEquals(1024, quota.memoryMb());
            assertEquals(120, quota.timeoutSeconds());
        }

        @Test
        void pickQuotaImageProcessing() {
            SandboxQuota quota = policy.pickQuota("python script.py # uses cv2 and PIL");
            assertEquals(2048, quota.memoryMb());
        }

        @Test
        void hasCorrectName() {
            assertEquals("sandbox-policy", policy.name());
        }
    }

    // ── SelfHealingExtension ─────────────────────────────────

    @Nested
    class SelfHealingTest {

        private final SelfHealingExtension healing = new SelfHealingExtension();

        @Test
        void detectsMissingModule() {
            assertEquals("numpy", healing.detectMissingModule(
                    "ModuleNotFoundError: No module named 'numpy'"));
            assertNull(healing.detectMissingModule("Some other error"));
        }

        @Test
        void detectsMemoryError() {
            assertTrue(healing.isMemoryError("MemoryError: out of memory"));
            assertTrue(healing.isMemoryError("Process was Killed"));
            assertFalse(healing.isMemoryError("Normal output"));
        }

        @Test
        void detectsTimeout() {
            assertTrue(healing.isTimeout("Command timed out after 60s"));
            assertFalse(healing.isTimeout("Command completed"));
        }

        @Test
        void detectsMissingFile() {
            assertEquals("data.csv", healing.detectMissingFile(
                    "FileNotFoundError: No such file or directory: 'data.csv'"));
            assertNull(healing.detectMissingFile("File exists"));
        }

        @Test
        void ignoresNonBashTools() {
            var tc = new ToolCallContent("tc1", "read", Map.of("path", "/tmp/x"));
            var result = new ToolResult("File not found: /tmp/x");
            var ctx = new AfterToolCallContext(tc, tc.arguments(), result, true);

            var response = healing.afterToolCall(ctx);
            assertNull(response);
        }

        @Test
        void ignoresNonErrors() {
            var tc = new ToolCallContent("tc1", "bash", Map.of("command", "ls"));
            var result = new ToolResult("file1.txt\nfile2.txt");
            var ctx = new AfterToolCallContext(tc, tc.arguments(), result, false);

            var response = healing.afterToolCall(ctx);
            assertNull(response);
        }

        @Test
        void enhancesTimeoutError() {
            var tc = new ToolCallContent("tc1", "bash", Map.of("command", "slow_command"));
            var result = new ToolResult("Command timed out after 60s");
            var ctx = new AfterToolCallContext(tc, tc.arguments(), result, true);

            var response = healing.afterToolCall(ctx);
            assertNotNull(response);
            assertInstanceOf(AfterToolCallHookResult.ModifyResult.class, response);
        }

        @Test
        void enhancesMemoryError() {
            var tc = new ToolCallContent("tc1", "bash", Map.of("command", "big_data"));
            var result = new ToolResult("MemoryError: process killed");
            var ctx = new AfterToolCallContext(tc, tc.arguments(), result, true);

            var response = healing.afterToolCall(ctx);
            assertNotNull(response);
        }

        @Test
        void enhancesMissingFile() {
            var tc = new ToolCallContent("tc1", "bash", Map.of("command", "cat missing.txt"));
            var result = new ToolResult("No such file or directory: 'missing.txt'");
            var ctx = new AfterToolCallContext(tc, tc.arguments(), result, true);

            var response = healing.afterToolCall(ctx);
            assertNotNull(response);
        }

        @Test
        void noEnhancementForUnknownErrors() {
            var tc = new ToolCallContent("tc1", "bash", Map.of("command", "unknown"));
            var result = new ToolResult("Some random error message");
            var ctx = new AfterToolCallContext(tc, tc.arguments(), result, true);

            var response = healing.afterToolCall(ctx);
            assertNull(response);
        }

        @Test
        void hasCorrectName() {
            assertEquals("self-healing", healing.name());
        }
    }

    // ── ReadTool correctness ─────────────────────────────────

    @Nested
    class ReadToolTest {

        @Test
        void readFileNotFound(@TempDir Path dir) throws Exception {
            var fileOps = new io.agentcore.tools.shell.LocalFileOperations(dir);
            var tool = new ReadTool(fileOps);

            var result = tool.execute("tc1", Map.of("path", "nonexistent.txt"), null);

            // Should return structured error with "not_found" detail
            assertNotNull(result.details());
            Map<String, Object> details = result.details();
            assertEquals("not_found", details.get("error"));
            assertTrue(result.text().contains("File not found"));
        }

        @Test
        void readFileSuccess(@TempDir Path dir) throws Exception {
            Files.writeString(dir.resolve("test.txt"), "Hello World\nLine 2");
            var fileOps = new io.agentcore.tools.shell.LocalFileOperations(dir);
            var tool = new ReadTool(fileOps);

            var result = tool.execute("tc1", Map.of("path", "test.txt"), null);

            assertNotNull(result.details());
            Map<String, Object> details = result.details();
            assertEquals(false, details.get("truncated"));
            assertEquals("test.txt", details.get("path"));
            assertTrue(result.text().contains("Hello World"));
        }

        @Test
        void readFileTruncation(@TempDir Path dir) throws Exception {
            // Create file with many lines
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 100; i++) {
                sb.append("Line ").append(i).append('\n');
            }
            Files.writeString(dir.resolve("big.txt"), sb.toString());

            var fileOps = new io.agentcore.tools.shell.LocalFileOperations(dir);
            var tool = new ReadTool(fileOps, 32000, 10);

            var result = tool.execute("tc1", Map.of("path", "big.txt"), null);

            Map<String, Object> details = result.details();
            assertEquals(true, details.get("truncated"));
        }

        @Test
        void requirePathParameter() throws Exception {
            var fileOps = new io.agentcore.tools.shell.LocalFileOperations();
            var tool = new ReadTool(fileOps);

            var result = tool.execute("tc1", Map.of(), null);
            assertTrue(result.text().contains("ERROR"));
        }
    }

    // ── RenderedOutput ─────────────────────────────────────────

    @Nested
    class RenderedOutputTest {

        @Test
        void renderedOutputDefaults() {
            var output = new RenderedOutput("hello");
            assertEquals("hello", output.text());
            assertNull(output.display());
            assertEquals("text/plain", output.mimeType());
        }

        @Test
        void renderedOutputWithDisplay() {
            var display = Map.<String, Object>of("language", "python");
            var output = new RenderedOutput("print('hi')", display);
            assertEquals("python", output.display().get("language"));
        }

        @Test
        void toolDefaultRenderCall() {
            Tool tool = new Tool() {
                @Override public ToolDefinition definition() { return new ToolDefinition("test", "test", Map.of()); }
                @Override public ToolResult execute(String id, Map<String, Object> params, ToolContext ctx) { return new ToolResult("ok"); }
            };
            assertEquals("test()", tool.renderCall("id1", "test", Map.of()));
            assertEquals("test(x=1)", tool.renderCall("id1", "test", Map.of("x", 1)));
        }
    }
}
