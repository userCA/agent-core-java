package io.agentcore.tools.local;

import io.agentcore.core.content.TextContent;
import io.agentcore.tools.base.*;
import io.agentcore.tools.operations.BashOperations;
import io.agentcore.tools.operations.LocalBashOperations;
import io.agentcore.tools.truncate.Truncate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class BashTool implements Tool {
    private final String cwd;
    private final BashOperations bashOps;

    public BashTool(String cwd, BashOperations bashOps) {
        this.cwd = cwd;
        this.bashOps = bashOps;
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition("bash", "Execute a bash command.",
                Map.of("type", "object", "properties", Map.of(
                        "command", Map.of("type", "string", "description", "Bash command"),
                        "timeout", Map.of("type", "integer", "description", "Timeout in seconds (1-300)")
                ), "required", List.of("command")),
                "Execute bash commands",
                List.of("Use bash for complex operations; prefer specialized tools for file operations."));
    }

    @Override
    public CompletableFuture<ToolResult> execute(String toolCallId, Map<String, Object> params, ToolContext ctx) {
        Object cmdObj = params.get("command");
        if (cmdObj == null || !(cmdObj instanceof String command) || command.isBlank()) {
            return CompletableFuture.completedFuture(
                    new ToolResult(List.of(new TextContent("Error: 'command' parameter is required and must be a non-empty string"))));
        }

        // Block dangerous commands
        if (LocalBashOperations.isDangerousCommand(command)) {
            return CompletableFuture.completedFuture(
                    new ToolResult(List.of(new TextContent(
                            "Command blocked: appears to be a dangerous operation (e.g., rm -rf /, fork bomb, " +
                            "reverse shell, system shutdown). Use safer alternatives or specialized tools."))));
        }

        Double timeout = params.containsKey("timeout") ?
                ((Number) params.get("timeout")).doubleValue() : 60.0;

        // Clamp timeout to safe range
        timeout = Math.max(1.0, Math.min(300.0, timeout));

        return bashOps.execute(command, cwd, timeout, null, null, ctx.signal())
                .thenApply(result -> {
                    String output = result.stdout() + (result.stderr().isEmpty() ? "" : "\n" + result.stderr());

                    boolean truncated = false;
                    int lineCount = countLines(output);
                    if (output.length() > 50000 || lineCount > 200) {
                        output = Truncate.truncateHead(output, 40000);
                        lineCount = countLines(output);
                        if (lineCount > 200) {
                            output = Truncate.truncateLine(output, 200);
                        }
                        truncated = true;
                    }

                    return new ToolResult(
                            List.of(new TextContent(output)),
                            Map.of("exit_code", result.returnCode(), "truncated", truncated)
                    );
                }).exceptionally(e -> new ToolResult(
                        List.of(new TextContent("Error executing command: " + e.getMessage()))));
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
