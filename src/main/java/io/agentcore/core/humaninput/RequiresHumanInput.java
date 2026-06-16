package io.agentcore.core.humaninput;

import java.util.Map;

/**
 * Thrown by tools when they need additional input from the user.
 * The tool runner catches this and creates a HumanInputRequired event,
 * then awaits resolution via HumanInputGate.
 */
public class RequiresHumanInput extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String prompt;
    private final transient Map<String, Object> inputSchema;

    public RequiresHumanInput(String prompt, Map<String, Object> inputSchema) {
        super(prompt);
        this.prompt = prompt;
        this.inputSchema = inputSchema;
    }

    public String prompt() { return prompt; }
    public Map<String, Object> inputSchema() { return inputSchema; }
}
