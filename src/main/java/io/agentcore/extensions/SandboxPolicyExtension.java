package io.agentcore.extensions;

import io.agentcore.model.Content.ToolCallContent;
import io.agentcore.extensions.Extension;
import io.agentcore.extensions.HookTypes.*;
import io.agentcore.tools.shell.SecurityUtils;
import io.agentcore.tools.shell.SandboxQuota;

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
    public int order() {
        // Security policies must run before other extensions (e.g. SelfHealing)
        return -100;
    }

    @Override
    public ToolCallHookResult beforeToolCall(ToolCallContext context) {
        ToolCallContent toolCall = context.toolCall();
        if (!"bash".equals(toolCall.name())) return null;

        Object cmdObj = context.arguments().get("command");
        if (!(cmdObj instanceof String command) || command.isEmpty()) return null;

        // 1. Block destructive commands (delegated to SecurityUtils)
        if (SecurityUtils.isDestructive(command)) {
            return new ToolCallHookResult.Block(
                    "Dangerous command pattern detected. "
                            + "This operation is blocked to prevent system damage.");
        }

        // 2. Pick quota based on command content
        SandboxQuota quota = pickQuota(command);
        Map<String, Object> sandboxMeta = Map.of(
                "cpu_cores", quota.cpuCores(),
                "memory_mb", quota.memoryMb(),
                "timeout_seconds", quota.timeoutSeconds(),
                "network_allowed", quota.networkAllowed()
        );

        return new ToolCallHookResult.InjectMetadata(Map.of("sandbox_quota", sandboxMeta));
    }

    /**
     * Select the appropriate quota profile based on command content.
     */
    public SandboxQuota pickQuota(String command) {
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
}
