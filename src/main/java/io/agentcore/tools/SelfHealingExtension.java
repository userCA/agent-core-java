package io.agentcore.tools;

import io.agentcore.core.Content;
import io.agentcore.core.Content.TextContent;
import io.agentcore.extensions.Extension;
import io.agentcore.tools.util.BashOperations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    public Map<String, Object> afterToolCall(Map<String, Object> callContext) {
        Object isErrorObj = callContext.get("is_error");
        boolean isError = Boolean.TRUE.equals(isErrorObj);
        if (!isError) return null;

        // Only process bash tool calls
        String toolName = extractToolName(callContext.get("tool_call"));
        if (!"bash".equals(toolName)) return null;

        String resultText = extractResultText(callContext.get("result"));
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
        String toolCallId = extractToolCallId(callContext.get("tool_call"));
        if (toolCallId != null) {
            fixesApplied.put(toolCallId, fixes);
        }

        return buildResponse(callContext.get("result"), fixes);
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

    private Map<String, Object> buildResponse(Object originalResult, List<String> fixes) {
        String origText = "";
        Object origDetails = null;
        Map<String, Object> origDisplay = null;

        if (originalResult instanceof ToolResult tr) {
            origText = tr.text();
            origDetails = tr.details();
            origDisplay = tr.display();
        }

        StringBuilder fixSb = new StringBuilder("\n\n[Self-healing]\n");
        for (String fix : fixes) {
            fixSb.append("- ").append(fix).append('\n');
        }
        String enhancedText = origText + fixSb;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", List.of(new TextContent(enhancedText)));
        result.put("details", origDetails);
        result.put("display", origDisplay);

        return Map.of("result", result);
    }

    // ── Extraction helpers ──

    private String extractToolName(Object toolCallObj) {
        if (toolCallObj instanceof Content.ToolCallContent tc) {
            return tc.name();
        }
        if (toolCallObj instanceof Map<?, ?> map) {
            Object name = map.get("name");
            return name instanceof String s ? s : null;
        }
        return null;
    }

    private String extractToolCallId(Object toolCallObj) {
        if (toolCallObj instanceof Content.ToolCallContent tc) {
            return tc.id();
        }
        if (toolCallObj instanceof Map<?, ?> map) {
            Object id = map.get("id");
            return id instanceof String s ? s : null;
        }
        return null;
    }

    private String extractResultText(Object resultObj) {
        if (resultObj instanceof ToolResult tr) {
            StringBuilder sb = new StringBuilder();
            for (Content c : tr.content()) {
                if (c instanceof TextContent tc) {
                    sb.append(tc.text());
                }
            }
            return sb.toString();
        }
        if (resultObj instanceof Map<?, ?> map) {
            Object content = map.get("content");
            if (content instanceof List<?> list) {
                StringBuilder sb = new StringBuilder();
                for (Object item : list) {
                    if (item instanceof TextContent tc) {
                        sb.append(tc.text());
                    }
                }
                return sb.toString();
            }
        }
        return "";
    }
}
