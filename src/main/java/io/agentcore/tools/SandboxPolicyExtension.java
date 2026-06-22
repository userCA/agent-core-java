package io.agentcore.tools;

import io.agentcore.extensions.Extension;
import io.agentcore.tools.util.SandboxQuota;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Sandbox policy extension — quota assignment and dangerous-command interception.
 *
 * <p>Mirrors Python {@code agent_core/tools/sandbox_policy.py}.
 * Inspects bash tool calls, blocks dangerous patterns, and assigns
 * resource quotas based on command content analysis.
 */
public class SandboxPolicyExtension implements Extension {

    private static final String NAME = "sandbox-policy";

    /** Patterns that indicate the command needs network access. */
    private static final List<Pattern> INSTALL_PATTERNS = List.of(
            Pattern.compile("\\bpip\\b.*\\binstall\\b"),
            Pattern.compile("\\bpip3\\b.*\\binstall\\b"),
            Pattern.compile("\\bapt-get\\b"),
            Pattern.compile("\\bapt\\b"),
            Pattern.compile("\\bnpm\\b.*\\binstall\\b"),
            Pattern.compile("\\bnpx\\b"),
            Pattern.compile("\\bcurl\\b"),
            Pattern.compile("\\bwget\\b"),
            Pattern.compile("\\bgit\\b.*\\bclone\\b")
    );

    /** Patterns that are always denied regardless of sandbox. */
    private static final List<Pattern> DANGEROUS_PATTERNS = List.of(
            Pattern.compile("\\brm\\s+-rf\\s+/"),
            Pattern.compile("\\brm\\s+-rf\\s+/\\*"),
            Pattern.compile("\\bchmod\\s+777\\s+/"),
            Pattern.compile("\\bmkfs\\."),
            Pattern.compile("\\bdd\\s+if="),
            Pattern.compile(">\\s*/dev/sda"),
            Pattern.compile(">\\s*/dev/nvme"),
            Pattern.compile("\\bshutdown\\b"),
            Pattern.compile("\\breboot\\b"),
            Pattern.compile("\\bfork\\s+bomb"),
            Pattern.compile(":\\(\\)\\s*\\{\\s*:\\|:")
    );

    /** Pre-defined quota profiles for different command types. */
    private static final Map<String, SandboxQuota> QUOTA_MATRIX = Map.of(
            "default", new SandboxQuota(0.5, 256, 60, 100, false, List.of(), List.of()),
            "data_analysis", new SandboxQuota(1.0, 1024, 120, 100, false, List.of(), List.of()),
            "image_processing", new SandboxQuota(2.0, 2048, 180, 100, false, List.of(), List.of()),
            "install_package", new SandboxQuota(1.0, 512, 120, 100, true, List.of(), List.of())
    );

    /** Keywords indicating data analysis tasks. */
    private static final List<String> DATA_KEYWORDS = List.of(
            "pandas", "numpy", "sklearn", "scipy", "sql",
            "dataframe", "csv", "groupby", "matplotlib -"
    );

    /** Keywords indicating image processing tasks. */
    private static final List<String> IMG_KEYWORDS = List.of(
            "matplotlib", "PIL", "cv2", "ffmpeg", "pillow",
            "plot", "chart", "graph", "figure"
    );

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Map<String, Object> beforeToolCall(Map<String, Object> callContext) {
        Object toolCallObj = callContext.get("tool_call");
        if (toolCallObj == null) return null;

        // Extract tool name
        String toolName = extractToolName(toolCallObj);
        if (!"bash".equals(toolName)) return null;

        // Extract command
        String command = extractCommand(callContext);
        if (command == null || command.isEmpty()) return null;

        // 1. Block dangerous commands
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(command).find()) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("block", true);
                result.put("reason",
                        "Dangerous command pattern detected: '" + pattern.pattern() + "'. "
                                + "This operation is blocked to prevent system damage.");
                return result;
            }
        }

        // 2. Pick quota based on command content
        SandboxQuota quota = pickQuota(command);
        Map<String, Object> sandboxMeta = new LinkedHashMap<>();
        sandboxMeta.put("cpu_cores", quota.cpuCores());
        sandboxMeta.put("memory_mb", quota.memoryMb());
        sandboxMeta.put("timeout_seconds", quota.timeoutSeconds());
        sandboxMeta.put("network_allowed", quota.networkAllowed());

        return Map.of("inject_metadata", Map.of("sandbox_quota", sandboxMeta));
    }

    /**
     * Select the appropriate quota profile based on command content.
     */
    SandboxQuota pickQuota(String command) {
        // Check for install commands first
        for (Pattern pattern : INSTALL_PATTERNS) {
            if (pattern.matcher(command).find()) {
                return QUOTA_MATRIX.get("install_package");
            }
        }

        String cmdLower = command.toLowerCase();

        // Check for image processing keywords
        for (String keyword : IMG_KEYWORDS) {
            if (cmdLower.contains(keyword.toLowerCase())) {
                return QUOTA_MATRIX.get("image_processing");
            }
        }

        // Check for data analysis keywords
        for (String keyword : DATA_KEYWORDS) {
            if (cmdLower.contains(keyword.toLowerCase())) {
                return QUOTA_MATRIX.get("data_analysis");
            }
        }

        return QUOTA_MATRIX.get("default");
    }

    private String extractToolName(Object toolCallObj) {
        if (toolCallObj instanceof io.agentcore.core.Content.ToolCallContent tc) {
            return tc.name();
        }
        if (toolCallObj instanceof Map<?, ?> map) {
            Object name = map.get("name");
            return name instanceof String s ? s : null;
        }
        return null;
    }

    private String extractCommand(Map<String, Object> callContext) {
        // Try from args directly
        Object argsObj = callContext.get("args");
        if (argsObj instanceof Map<?, ?> args) {
            Object cmd = args.get("command");
            if (cmd instanceof String s) return s;
        }
        // Try from tool_call arguments
        Object toolCallObj = callContext.get("tool_call");
        if (toolCallObj instanceof io.agentcore.core.Content.ToolCallContent tc) {
            Object cmd = tc.arguments().get("command");
            if (cmd instanceof String s) return s;
        }
        return null;
    }
}
