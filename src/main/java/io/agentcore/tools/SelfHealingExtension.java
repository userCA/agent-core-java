package io.agentcore.tools;

import io.agentcore.model.Content;
import io.agentcore.model.Content.TextContent;
import io.agentcore.model.Content.ToolCallContent;
import io.agentcore.extensions.Extension;
import io.agentcore.extensions.HookTypes.*;
import io.agentcore.tools.shell.BashOperations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import io.agentcore.model.ToolResult;

/**
 * Self-healing extension — detect common sandbox errors and apply fixes.
 *
 * <p>Mirrors Python {@code agent_core/tools/sandbox_healing.py}.
 *
 * <p>Phase 1 (immediate): Auto-install missing modules, copy files into sandbox.
 * Phase 2 (prompt retry): Return enhanced error messages telling the LLM
 * what was done so it can retry the original command.
 *
 * <p>Does NOT block the tool runner — the LLM retries naturally in the next turn.
 */
public class SelfHealingExtension implements Extension {

    private static final Logger log = LoggerFactory.getLogger(SelfHealingExtension.class);

    private static final String NAME = "self-healing";

    private static final Pattern MISSING_MODULE_RE =
            Pattern.compile("No module named '(\\w+)'");
    private static final Pattern MEMORY_ERROR_RE =
            Pattern.compile("MemoryError|Killed");
    private static final Pattern FILE_NOT_FOUND_RE =
            Pattern.compile("No such file or directory: '?([^'\\n]+)'?");

    private final BashOperations bashOps;

    /** Track fixes applied per tool call ID for diagnostics. */
    private final Map<String, List<String>> fixesApplied =
            Collections.synchronizedMap(new LinkedHashMap<>());

    /**
     * Create a self-healing extension with optional bash operations backend.
     *
     * @param bashOps backend for running fix commands (pip install, etc.).
     *                If null, auto-install fixes are skipped (only error enhancement is applied).
     */
    public SelfHealingExtension(BashOperations bashOps) {
        this.bashOps = bashOps;
    }

    public SelfHealingExtension() {
        this(null);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public AfterToolCallHookResult afterToolCall(AfterToolCallContext context) {
        if (!context.isError()) return null;

        // Only process bash tool calls
        if (!"bash".equals(context.toolName())) return null;

        String resultText = extractResultText(context.result());
        List<String> fixes = new ArrayList<>();

        // 1. ModuleNotFoundError → pip install
        String missingModule = detectMissingModule(resultText);
        if (missingModule != null && bashOps != null) {
            String fixResult = installModule(missingModule);
            if (fixResult != null) {
                fixes.add("Installed missing module '" + missingModule + "': " + fixResult);
            } else {
                fixes.add("Attempted to install '" + missingModule
                        + "' but it may have failed. Check that the package name is correct.");
            }
        }

        // 2. MemoryError → suggest quota increase
        if (isMemoryError(resultText)) {
            fixes.add("Memory limit exceeded. Consider processing data in smaller chunks "
                    + "or requesting higher memory quota.");
        }

        // 3. Timeout → suggest longer timeout
        if (isTimeout(resultText)) {
            fixes.add("Command timed out. Consider optimizing the command, reducing data size, "
                    + "or requesting a longer timeout.");
        }

        // 4. FileNotFoundError → suggest copying file into sandbox
        String missingFile = detectMissingFile(resultText);
        if (missingFile != null) {
            fixes.add("File not found: '" + missingFile
                    + "'. If this file exists outside the sandbox, "
                    + "use the write tool to copy it into the sandbox output directory.");
        }

        if (fixes.isEmpty()) return null;

        // Track fixes for this tool call
        fixesApplied.put(context.toolCallId(), fixes);

        // Build enhanced result
        return new AfterToolCallHookResult.ModifyResult(
                List.of(new TextContent(resultText + buildFixMessage(fixes)))
        );
    }

    /**
     * Get all fixes applied so far (for diagnostics).
     */
    public Map<String, List<String>> getFixesApplied() {
        return Map.copyOf(fixesApplied);
    }

    // ── Detection helpers ──

    String detectMissingModule(String text) {
        Matcher m = MISSING_MODULE_RE.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    boolean isMemoryError(String text) {
        return MEMORY_ERROR_RE.matcher(text).find();
    }

    boolean isTimeout(String text) {
        return text.toLowerCase().contains("timed out");
    }

    String detectMissingFile(String text) {
        Matcher m = FILE_NOT_FOUND_RE.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    // ── Fix helpers ──

    private String installModule(String module) {
        try {
            var result = bashOps.execute(
                    "pip install " + module, null, 60.0, null, null, null
            ).join();
            if (result.returnCode() == 0 && result.stdout() != null && !result.stdout().isEmpty()) {
                String[] lines = result.stdout().strip().split("\n");
                return lines[lines.length - 1];
            }
            return result.returnCode() == 0 ? "ok" : null;
        } catch (Exception e) {
            log.warn("Auto-install of '{}' failed: {}", module, e.getMessage());
            return null;
        }
    }

    // ── Response building ──

    private String buildFixMessage(List<String> fixes) {
        StringBuilder sb = new StringBuilder("\n\n[Self-healing]\n");
        for (String fix : fixes) {
            sb.append("- ").append(fix).append('\n');
        }
        return sb.toString();
    }

    // ── Extraction helpers ──

    private String extractResultText(ToolResult result) {
        if (result == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Content c : result.content()) {
            if (c instanceof TextContent tc) {
                sb.append(tc.text());
            }
        }
        return sb.toString();
    }
}
