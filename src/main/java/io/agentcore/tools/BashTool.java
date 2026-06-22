package io.agentcore.tools;

import io.agentcore.tools.util.BashOperations;
import io.agentcore.tools.util.BashResult;
import io.agentcore.tools.util.SecurityUtils;
import io.agentcore.tools.util.Truncation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Bash command execution tool.
 *
 * <p>Features:
 * <ul>
 *   <li>Dangerous command blocking (rm -rf /, fork bombs, etc.)</li>
 *   <li>Output truncation to prevent context overflow</li>
 *   <li>Quota-enforced timeouts</li>
 *   <li>Signal-based cancellation via {@link ToolContext}</li>
 * </ul>
 */
public class BashTool implements Tool {

    private static final ToolDefinition DEF = new ToolDefinition(
            "bash",
            "Execute a bash command. Use for complex operations; "
                    + "prefer specialized tools (read/write/edit/grep/find/ls) for file operations.",
            Map.of("type", "object", "properties", Map.of(
                    "command", Map.of("type", "string",
                            "description", "Bash command to execute"),
                    "timeout", Map.of("type", "integer",
                            "description", "Timeout in seconds (1-300, default 60)")
            ), "required", List.of("command")),
            null, null, null
    );

    private final BashOperations bashOps;

    public BashTool(BashOperations bashOps) {
        this.bashOps = bashOps;
    }

    @Override
    public ToolDefinition definition() {
        return DEF;
    }

    @Override
    public ToolResult execute(String toolCallId, Map<String, Object> params, ToolContext ctx) throws Exception {
        Object cmdObj = params.get("command");
        if (cmdObj == null || !(cmdObj instanceof String command) || command.isBlank()) {
            return new ToolResult("ERROR: 'command' parameter is required and must be a non-empty string");
        }

        if (SecurityUtils.isDangerousCommand(command)) {
            return new ToolResult("ERROR: Command blocked: appears to be a dangerous operation "
                    + "(e.g., rm -rf /, fork bomb, reverse shell, system shutdown). "
                    + "Use safer alternatives or specialized tools.");
        }

        Object timeoutObj = params.get("timeout");
        double timeout = timeoutObj instanceof Number n ? n.doubleValue() : 60.0;
        timeout = Math.max(1.0, Math.min(300.0, timeout));

        CompletableFuture<BashResult> future = bashOps.execute(
                command, null, timeout, null, null, null);

        try {
            BashResult result = future.join();
            String output = result.stdout();
            if (result.stderr() != null && !result.stderr().isEmpty()
                    && !result.stderr().equals(result.stdout())) {
                output = (output.isEmpty() ? result.stderr() : output + "\n" + result.stderr());
            }

            // Head truncation: keep last portion (most recent output is most relevant)
            boolean truncated = false;
            int lineCount = countLines(output);
            Map<String, Object> truncationInfo = null;

            if (output.length() > 50000 || lineCount > 200) {
                truncationInfo = Map.of(
                        "total_chars", output.length(),
                        "total_lines", lineCount,
                        "max_chars", 40000,
                        "max_lines", 200
                );
                output = Truncation.truncateHead(output, 40000);
                lineCount = countLines(output);
                if (lineCount > 200) {
                    String[] lines = output.split("\n", -1);
                    int skip = lines.length - 200;
                    output = "... (" + skip + " lines skipped)\n"
                            + String.join("\n", java.util.Arrays.asList(lines).subList(skip, lines.length));
                }
                truncated = true;
            }

            if (output.trim().isEmpty()) {
                output = "(no output)";
            }

            String finalOutput = output + "\n[exit_code=" + result.returnCode()
                    + (truncated ? ", truncated=true" : "") + "]";

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("exit_code", result.returnCode());
            details.put("truncated", truncated);
            if (truncationInfo != null) {
                details.put("truncation_info", truncationInfo);
            }

            return new ToolResult(
                    List.of(new io.agentcore.core.Content.TextContent(finalOutput)),
                    details, null);
        } catch (Exception e) {
            return new ToolResult("Error executing command: " + e.getMessage());
        }
    }

    private static int countLines(String s) {
        if (s == null || s.isEmpty()) return 0;
        int count = 1;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\n') count++;
        }
        return count;
    }
}
