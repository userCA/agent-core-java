package io.agentcore.tools.external;

import io.agentcore.model.Content.TextContent;
import io.agentcore.tools.shell.Truncation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import io.agentcore.model.ToolResult;
import io.agentcore.tools.Tool;
import io.agentcore.tools.ToolContext;
import io.agentcore.tools.ToolDefinition;

/**
 * Feishu CLI tool — wrap lark-cli for agent-driven Feishu operations.
 *
 * <p>Mirrors Python {@code agent_core/tools/feishu_cli_tool.py}.
 * Gives the agent access to 200+ Feishu operations via the official lark-cli.
 */
public class FeishuCLITool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(FeishuCLITool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int TIMEOUT_DEFAULT = 30;
    private static final int TIMEOUT_MAX = 120;
    private static final int MAX_OUTPUT = 8000;
    private static final int MAX_OUTPUT_BYTES = 1024 * 1024; // 1MB cap to prevent OOM

    private final String larkCliPath;

    public FeishuCLITool() {
        this.larkCliPath = findLarkCli();
    }

    private static final ToolDefinition DEF = new ToolDefinition(
            "feishu",
            "Execute Feishu/Lark operations via the official lark-cli. "
                    + "Install with: npm install -g @larksuite/cli"
                    + "\n\n"
                    + "Common commands:\n"
                    + "  lark-cli im +messages-send --as bot --chat-id <id> --text <text>\n"
                    + "  lark-cli im +messages-list --as bot --chat-id <id> --page-size 10\n"
                    + "  lark-cli calendar +agenda --as user\n"
                    + "  lark-cli drive +docs-search --as user --query <q>\n"
                    + "  lark-cli task +tasks-list --as user\n"
                    + "\nUse --as bot for bot actions, --as user for user actions."
                    + "Output is JSON; use jq or string parsing as needed.",
            Map.of("type", "object", "properties", Map.of(
                    "command", Map.of("type", "string",
                            "description", "The lark-cli command to execute (without the 'lark-cli' prefix). "
                                    + "Example: 'im +messages-send --as bot --chat-id oc_xxx --text Hello'"),
                    "timeout", Map.of("type", "integer",
                            "description", "Timeout in seconds. Default 30, max 120.")
            ), "required", List.of("command")),
            "feishu $ARGUMENTS — Execute Feishu/Lark operations via lark-cli.\n"
                    + "Available: send messages, query calendar, search docs, manage tasks, etc.\n"
                    + "Use --as bot for bot-scoped actions, --as user for user-scoped.",
            List.of(),
            60.0
    );

    @Override
    public ToolDefinition definition() {
        return DEF;
    }

    @Override
    public ToolResult execute(String toolCallId, Map<String, Object> params, ToolContext ctx) throws Exception {
        String raw = ((String) params.getOrDefault("command", "")).trim();
        if (raw.isEmpty()) {
            return ToolResult.error("missing_param", "'command' parameter is required");
        }

        // Parse command args — split by spaces respecting quotes
        String[] args;
        try {
            args = splitArgs(raw);
        } catch (Exception e) {
            return ToolResult.error("invalid_syntax", e.getMessage());
        }

        int timeout = TIMEOUT_DEFAULT;
        Object timeoutParam = params.get("timeout");
        if (timeoutParam instanceof Number n) {
            timeout = Math.min(n.intValue(), TIMEOUT_MAX);
        }

        // Build full command
        String[] fullCmd = new String[args.length + 1];
        fullCmd[0] = larkCliPath;
        System.arraycopy(args, 0, fullCmd, 1, args.length);

        try {
            ProcessBuilder pb = new ProcessBuilder(fullCmd);
            pb.environment().put("NO_COLOR", "1");
            pb.redirectErrorStream(false);

            Process process = pb.start();

            // Read stdout and stderr
            ByteArrayOutputStream stdoutBaos = new ByteArrayOutputStream();
            ByteArrayOutputStream stderrBaos = new ByteArrayOutputStream();

            Thread stdoutThread = Thread.startVirtualThread(() -> {
                try (InputStream is = process.getInputStream()) {
                    stdoutBaos.write(is.readNBytes(MAX_OUTPUT_BYTES));
                } catch (Exception ignored) {
                    log.debug("Error reading lark-cli stdout", ignored);
                }
            });
            Thread stderrThread = Thread.startVirtualThread(() -> {
                try (InputStream is = process.getErrorStream()) {
                    stderrBaos.write(is.readNBytes(MAX_OUTPUT_BYTES));
                } catch (Exception ignored) {
                    log.debug("Error reading lark-cli stderr", ignored);
                }
            });

            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("exit_code", -1);
                details.put("timeout", true);
                return new ToolResult(
                        List.of(new TextContent("Command timed out after " + timeout + "s")),
                        details, null);
            }

            stdoutThread.join(5000);
            stderrThread.join(5000);

            String output = stdoutBaos.toString().trim();
            String err = stderrBaos.toString().trim();
            int exitCode = process.exitValue();

            // Try to pretty-print JSON output
            if (output.startsWith("{") || output.startsWith("[")) {
                try {
                    Object parsed = MAPPER.readValue(output, Object.class);
                    output = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);
                } catch (Exception ignored) {
                    log.debug("Failed to parse lark-cli output as JSON", ignored);
                }
            }

            String resultText = output.isEmpty() ? "(no output)" : output;
            if (exitCode != 0) {
                resultText = "Error (exit " + exitCode + "):\n" + (err.isEmpty() ? output : err);
            } else if (!err.isEmpty()) {
                resultText += "\n\n(stderr):\n" + err;
            }

            boolean truncated = resultText.length() > MAX_OUTPUT;
            if (truncated) {
                resultText = Truncation.truncateTail(resultText, MAX_OUTPUT, "\n... (truncated)");
            }

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("exit_code", exitCode);
            details.put("truncated", truncated);

            return new ToolResult(
                    List.of(new TextContent(resultText)),
                    details, null);

        } catch (java.io.IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("No such file")) {
                return ToolResult.error("not_found",
                        "lark-cli not found. Install: npm install -g @larksuite/cli\n"
                                + "Then configure: lark-cli config init --new");
            }
            return ToolResult.error("io_error", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.error("interrupted", "Command interrupted");
        }
    }

    /**
     * Find lark-cli in PATH.
     */
    private static String findLarkCli() {
        try {
            Process p = new ProcessBuilder("which", "lark-cli")
                    .redirectErrorStream(true).start();
            String path = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor(5, TimeUnit.SECONDS);
            if (p.exitValue() == 0 && !path.isEmpty()) {
                return path;
            }
        } catch (Exception ignored) {
            log.debug("Failed to locate lark-cli in PATH", ignored);
        }
        return "lark-cli";
    }

    /**
     * Split command string into args, respecting single/double quotes.
     */
    public static String[] splitArgs(String raw) {
        var args = new java.util.ArrayList<String>();
        StringBuilder current = new StringBuilder();
        boolean inSingle = false, inDouble = false;

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (c == '"' && !inSingle) {
                inDouble = !inDouble;
            } else if (c == ' ' && !inSingle && !inDouble) {
                if (!current.isEmpty()) {
                    args.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            args.add(current.toString());
        }
        return args.toArray(new String[0]);
    }
}
