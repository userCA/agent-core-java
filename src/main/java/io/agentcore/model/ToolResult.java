package io.agentcore.model;

import io.agentcore.model.Content;
import io.agentcore.model.Content.TextContent;
import java.util.List;
import java.util.Map;

/**
 * Result returned by a tool execution — pure data model.
 *
 * <p>Control flow signals (e.g. terminate) are handled separately via
 * {@link ToolContext#requestTerminate()} and {@link io.agentcore.agent.ToolRunner.ToolCallResult},
 * keeping this record focused on tool output data.
 *
 * <p>HITL (Human-in-the-Loop) support: when {@code requiresInput} is true,
 * the agent loop pauses execution, emits a HumanInputRequired event,
 * and resumes with user input injected into {@link ToolContext#userInput()}.
 *
 * @param content        list of text/image content blocks
 * @param details        optional structured details
 * @param display        optional display hints for UI
 * @param requiresInput  true if the tool needs human input before proceeding
 * @param inputPrompt    prompt to display to the user (only when requiresInput=true)
 * @param inputSchema    optional JSON Schema for expected input (only when requiresInput=true)
 */
public record ToolResult(
        List<Content> content,
        Map<String, Object> details,
        Map<String, Object> display,
        boolean requiresInput,
        String inputPrompt,
        Map<String, Object> inputSchema
) {
    public ToolResult {
        if (content == null) content = List.of();
        else content = List.copyOf(content);
        if (details != null) details = Map.copyOf(details);
        if (display != null) display = Map.copyOf(display);
        if (inputSchema != null) inputSchema = Map.copyOf(inputSchema);
    }

    /**
     * Backward-compatible constructor (no HITL fields).
     */
    public ToolResult(List<Content> content, Map<String, Object> details, Map<String, Object> display) {
        this(content, details, display, false, null, null);
    }

    /**
     * Create a simple text result.
     */
    public ToolResult(String text) {
        this(List.of(new TextContent(text)), null, null);
    }

    /**
     * Create a result from content blocks.
     */
    public ToolResult(List<Content> content) {
        this(content, null, null);
    }

    /**
     * Create a HITL result that signals the agent loop to pause for human input.
     *
     * <p><b>Background:</b> Tools use this when they need user confirmation, clarification,
     * or additional parameters before proceeding (e.g. ConfirmTool, HumanInputTool).
     * This replaces the previous exception-driven approach ({@code throw RequiresHumanInput})
     * with a return-value-based flow that treats human input as a normal business state.
     *
     * <p><b>Design intent:</b> Returns a normal ToolResult rather than throwing an exception,
     * because "needing human input" is an expected workflow state, not an error condition.
     * ToolRunner checks {@link #requiresInput()} to decide whether to enter the HITL flow.
     *
     * <p><b>Constraints:</b> The returned result has empty content and {@code requiresInput=true}.
     * {@code inputSchema} may be null if the tool accepts free-form input.
     *
     * @param prompt      question or message to display to the user
     * @param inputSchema optional JSON Schema describing expected input fields
     */
    public static ToolResult requiresHumanInput(String prompt, Map<String, Object> inputSchema) {
        return new ToolResult(List.of(), null, null, true, prompt, inputSchema);
    }

    /**
     * Extract the first text content.
     */
    public String text() {
        return content.stream()
                .filter(c -> c instanceof TextContent)
                .map(c -> ((TextContent) c).text())
                .findFirst()
                .orElse("");
    }

    // ── Error factory methods ────────────────────────────────

    /**
     * Create a standardized error result with a code and message.
     */
    public static ToolResult error(String code, String message) {
        return new ToolResult(
                List.of(new TextContent("ERROR [" + code + "]: " + message)),
                Map.of("error", code, "message", message), null);
    }

    /**
     * Create a simple error result with only a message.
     */
    public static ToolResult error(String message) {
        return new ToolResult(
                List.of(new TextContent("ERROR: " + message)),
                Map.of("error", "general", "message", message), null);
    }
}
