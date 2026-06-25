package io.agentcore.app.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.agentcore.config.AgentConfig;
import io.agentcore.agent.Agent;
import io.agentcore.model.AgentEvent;
import io.agentcore.model.Content;
import io.agentcore.model.Message;
import io.agentcore.tools.ToolkitFactory;
import io.agentcore.tools.ToolRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Interactive CLI (REPL) for agent-core-java.
 *
 * <p>Provides a terminal-based chat interface with streaming output,
 * tool execution visibility, multi-line input, and slash commands.
 *
 * <h3>Usage:</h3>
 * <pre>
 * # Interactive mode
 * java -jar agent-core.jar --cli
 * java -jar agent-core.jar --cli --no-tools
 *
 * # Non-interactive (pipe-friendly)
 * java -jar agent-core.jar --cli -p "What is 2+2?"
 * echo "Explain Java" | java -jar agent-core.jar --cli -p -
 * </pre>
 *
 * <h3>Slash Commands:</h3>
 * <ul>
 *   <li>{@code /help}              — show available commands</li>
 *   <li>{@code /reset}             — reset conversation history</li>
 *   <li>{@code /tools}             — list registered tools</li>
 *   <li>{@code /config}            — show current configuration</li>
 *   <li>{@code /history}           — show conversation messages</li>
 *   <li>{@code /thinking}          — toggle thinking/reasoning display</li>
 *   <li>{@code /system <prompt>}   — change system prompt</li>
 *   <li>{@code /save [file]}       — export conversation to JSONL</li>
 *   <li>{@code /stats}             — show session statistics</li>
 *   <li>{@code /abort}             — abort the current agent run</li>
 *   <li>{@code /exit}              — exit the CLI</li>
 * </ul>
 *
 * <h3>Multi-line Input:</h3>
 * <p>Type {@code """} to enter multi-line mode, type {@code """} again to submit.</p>
 */
public class AgentCli implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AgentCli.class);
    private static final PrintStream out = System.out;
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    // ANSI colors
    private static final String RESET   = "\033[0m";
    private static final String BOLD    = "\033[1m";
    private static final String DIM     = "\033[2m";
    private static final String RED     = "\033[31m";
    private static final String GREEN   = "\033[32m";
    private static final String YELLOW  = "\033[33m";
    private static final String BLUE    = "\033[34m";
    private static final String MAGENTA = "\033[35m";
    private static final String CYAN    = "\033[36m";
    private static final String WHITE   = "\033[37m";

    private static final String MULTILINE_DELIMITER = "\"\"\"";

    private final AgentConfig config;
    private final Agent agent;
    private final ToolRegistry toolRegistry;
    private final AtomicBoolean abortSignal = new AtomicBoolean(false);
    private final Instant sessionStart = Instant.now();
    private final boolean interactive;

    // Session statistics
    private long totalInputTokens = 0;
    private long totalOutputTokens = 0;
    private int totalTurns = 0;

    // Feature toggles
    private boolean showThinking = true;
    private boolean running = true;

    /**
     * Create a CLI instance in interactive mode.
     */
    public AgentCli(AgentConfig config, boolean withTools) {
        this(config, withTools, true);
    }

    /**
     * Create a CLI instance.
     *
     * @param interactive true for REPL mode, false for single-shot pipe mode
     */
    public AgentCli(AgentConfig config, boolean withTools, boolean interactive) {
        this.config = config;
        this.interactive = interactive;
        this.toolRegistry = withTools ? ToolkitFactory.standard() : null;
        this.agent = config.createAgent(toolRegistry, null);

        // Register Ctrl+C handler (interactive mode only)
        if (interactive) {
            registerSignalHandler();
        }
    }

    // ── Signal Handler ─────────────────────────────────────

    private void registerSignalHandler() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (running && agent.isStreaming()) {
                agent.abort();
                out.println("\n" + YELLOW + "⚠ Interrupted." + RESET);
            }
        }));
    }

    // ── Non-interactive Mode ───────────────────────────────

    /**
     * Execute a single prompt and print the result (pipe-friendly).
     * Output goes to stdout without ANSI colors, suitable for piping.
     *
     * @param prompt the prompt text, or "-" to read from stdin
     * @return exit code (0 = success)
     */
    public int executeSingle(String prompt) {
        String text;
        if ("-".equals(prompt)) {
            // Read from stdin
            try (Scanner scanner = new Scanner(System.in)) {
                StringBuilder sb = new StringBuilder();
                while (scanner.hasNextLine()) {
                    if (!sb.isEmpty()) sb.append("\n");
                    sb.append(scanner.nextLine());
                }
                text = sb.toString().trim();
            }
        } else {
            text = prompt;
        }

        if (text.isEmpty()) {
            System.err.println("Error: empty prompt");
            return 1;
        }

        StringBuilder result = new StringBuilder();
        try {
            agent.prompt(text, event -> {
                if (event instanceof AgentEvent.MessageUpdate mu
                        && mu.delta() instanceof AgentEvent.MessageDelta.TextDelta td) {
                    result.append(td.text());
                }
            });
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }

        out.println(stripThinking(result.toString()));
        return 0;
    }

    /**
     * Strip {@code <think>...</think>} blocks from text (for non-interactive output).
     */
    private static String stripThinking(String text) {
        return text.replaceAll("<think>[\\s\\S]*?</think>", "").trim();
    }

    // ── Interactive REPL ───────────────────────────────────

    /**
     * Start the interactive REPL loop.
     */
    public void run() {
        if (!interactive) return;

        printBanner();

        Scanner scanner = new Scanner(System.in);
        while (running) {
            out.print(BOLD + CYAN + "\n❯ " + RESET);
            out.flush();

            String input;
            if (scanner.hasNextLine()) {
                input = scanner.nextLine();
            } else {
                break;
            }

            // Check for multi-line input
            if (input.trim().equals(MULTILINE_DELIMITER)) {
                input = readMultiLine(scanner);
                if (input == null || input.isEmpty()) continue;
            } else {
                input = input.trim();
            }

            if (input.isEmpty()) continue;

            if (input.startsWith("/")) {
                handleCommand(input);
            } else {
                handlePrompt(input);
            }
        }
    }

    private String readMultiLine(Scanner scanner) {
        out.println(DIM + "  [multi-line mode: type \"\"\" on a new line to submit]" + RESET);
        StringBuilder sb = new StringBuilder();
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.trim().equals(MULTILINE_DELIMITER)) {
                break;
            }
            if (!sb.isEmpty()) sb.append("\n");
            sb.append(line);
        }
        return sb.toString();
    }

    // ── Slash Commands ─────────────────────────────────────

    private void handleCommand(String input) {
        String[] parts = input.split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String arg = parts.length > 1 ? parts[1] : "";

        switch (cmd) {
            case "/help", "/h", "/?" -> printHelp();
            case "/exit", "/quit", "/q" -> {
                running = false;
                printStats();
                out.println(DIM + "Goodbye!" + RESET);
            }
            case "/reset", "/clear" -> {
                agent.reset();
                totalInputTokens = 0;
                totalOutputTokens = 0;
                totalTurns = 0;
                out.println(DIM + "↻ Conversation reset." + RESET);
            }
            case "/abort" -> {
                abortSignal.set(true);
                agent.abort();
                out.println(YELLOW + "⚠ Abort signal sent." + RESET);
            }
            case "/tools" -> printTools();
            case "/config" -> printConfig();
            case "/messages" -> printMessages();
            case "/history" -> printHistory(arg);
            case "/thinking" -> toggleThinking();
            case "/system" -> setSystemPrompt(arg);
            case "/save" -> saveConversation(arg);
            case "/stats" -> printStats();
            default -> out.println(RED + "Unknown command: " + cmd + ". Type /help for available commands." + RESET);
        }
    }

    // ── Prompt Handler ─────────────────────────────────────

    private void handlePrompt(String text) {
        abortSignal.set(false);
        totalTurns++;

        try {
            StringBuilder currentText = new StringBuilder();
            boolean[] firstChunk = {true};

            List<Message> results = agent.prompt(text, event -> {
                handleEvent(event, currentText, firstChunk);
            });

            // Ensure final newline
            if (!currentText.isEmpty()) {
                out.println();
            }

            // Print token usage if available
            if (results != null && !results.isEmpty()) {
                results.stream()
                    .filter(m -> m instanceof Message.AssistantMessage)
                    .map(m -> (Message.AssistantMessage) m)
                    .filter(am -> am.usage().totalTokensWithCache() > 0)
                    .findFirst()
                    .ifPresent(am -> {
                        int inTok = am.usage().inputTokens();
                        int outTok = am.usage().outputTokens();
                        totalInputTokens += inTok;
                        totalOutputTokens += outTok;
                        out.println(DIM + "  📊 tokens: " +
                                inTok + " in / " + outTok + " out" +
                                "  (session: " + totalInputTokens + " in / " +
                                totalOutputTokens + " out)" + RESET);
                    });
            }

        } catch (Exception e) {
            out.println(RED + "\n✗ Error: " + e.getMessage() + RESET);
            log.debug("Prompt error", e);
        }
    }

    // ── Event Handler ──────────────────────────────────────

    private void handleEvent(AgentEvent event, StringBuilder currentText, boolean[] firstChunk) {
        switch (event) {
            case AgentEvent.MessageUpdate mu -> {
                if (mu.delta() instanceof AgentEvent.MessageDelta.TextDelta td) {
                    if (firstChunk[0]) {
                        out.print(GREEN);
                        firstChunk[0] = false;
                    }
                    out.print(td.text());
                    out.flush();
                    currentText.append(td.text());
                } else if (mu.delta() instanceof AgentEvent.MessageDelta.ThinkingDelta thd) {
                    if (showThinking) {
                        out.print(DIM + thd.text() + RESET);
                        out.flush();
                    }
                } else if (mu.delta() instanceof AgentEvent.MessageDelta.ToolCallDelta tcd) {
                    if (tcd.name() != null && !tcd.name().isEmpty()) {
                        out.println(YELLOW + "  🔧 " + tcd.name() + RESET);
                        out.flush();
                    }
                }
            }
            case AgentEvent.ToolExecutionStart tes -> {
                out.println(YELLOW + "  ▶ " + tes.toolName() + " " +
                        formatArgs(tes.args()) + RESET);
                out.flush();
            }
            case AgentEvent.ToolExecutionEnd tee -> {
                String status = tee.isError() ? RED + "✗" + RESET : GREEN + "✓" + RESET;
                String preview = "";
                if (tee.result() != null) {
                    preview = Content.joinAllTextRaw(tee.result().content());
                    if (preview.length() > 120) {
                        preview = preview.substring(0, 117) + "...";
                    }
                }
                out.println(DIM + "  " + status + " " + tee.toolName() +
                        (preview.isEmpty() ? "" : " → " + preview) + RESET);
                out.flush();
            }
            case AgentEvent.HumanInputRequired hir -> {
                out.println(MAGENTA + "  ❓ Human input required: " + hir.prompt() + RESET);
                out.flush();
            }
            case AgentEvent.MessageEnd me -> {
                if (me.message() instanceof Message.AssistantMessage am && am.errorMessage() != null) {
                    out.println(RED + "  ✗ " + am.errorMessage() + RESET);
                    out.flush();
                }
            }
            default -> {}
        }
    }

    // ── Display helpers ────────────────────────────────────

    private void printBanner() {
        out.println();
        out.println(BOLD + "╔══════════════════════════════════════════╗" + RESET);
        out.println(BOLD + "║" + CYAN + "    agent-core-java  ·  Interactive CLI" + RESET + BOLD + "    ║" + RESET);
        out.println(BOLD + "╚══════════════════════════════════════════╝" + RESET);
        out.println();
        out.println(DIM + "  Provider: " + config.provider() +
                "  |  Model: " + config.model() + RESET);
        out.println(DIM + "  Tools: " +
                (toolRegistry != null ? toolRegistry.toDefinitions().size() + " registered" : "disabled") +
                "  |  Type " + BOLD + "/help" + RESET + DIM + " for commands" + RESET);
    }

    private void printHelp() {
        out.println(BOLD + "\nAvailable commands:" + RESET);
        out.println("  " + CYAN + "/help" + RESET + "              Show this help message");
        out.println("  " + CYAN + "/reset" + RESET + "             Clear conversation history");
        out.println("  " + CYAN + "/tools" + RESET + "             List registered tools");
        out.println("  " + CYAN + "/config" + RESET + "            Show current configuration");
        out.println("  " + CYAN + "/history [n]" + RESET + "       Show last n messages (default: all)");
        out.println("  " + CYAN + "/thinking" + RESET + "          Toggle thinking/reasoning display (now: " +
                (showThinking ? GREEN + "on" : RED + "off") + RESET + ")");
        out.println("  " + CYAN + "/system <prompt>" + RESET + "   Change system prompt");
        out.println("  " + CYAN + "/save [file]" + RESET + "       Export conversation to JSONL file");
        out.println("  " + CYAN + "/stats" + RESET + "             Show session statistics");
        out.println("  " + CYAN + "/messages" + RESET + "          Show message count in context");
        out.println("  " + CYAN + "/abort" + RESET + "             Abort the current agent run");
        out.println("  " + CYAN + "/exit" + RESET + "              Exit the CLI");
        out.println();
        out.println(DIM + "  Multi-line: type " + BOLD + "\"\"\"" + RESET + DIM +
                " to start, " + BOLD + "\"\"\"" + RESET + DIM + " to submit." + RESET);
        out.println(DIM + "  Ctrl+C: abort current run (double to exit)." + RESET);
    }

    private void printTools() {
        if (toolRegistry == null) {
            out.println(DIM + "No tools configured (started with --no-tools)." + RESET);
            return;
        }
        out.println(BOLD + "\nRegistered tools:" + RESET);
        for (var def : toolRegistry.toDefinitions()) {
            out.println("  " + GREEN + def.name() + RESET + DIM + " — " + def.description() + RESET);
        }
    }

    private void printConfig() {
        out.println(BOLD + "\nConfiguration:" + RESET);
        out.println("  Provider:       " + config.provider());
        out.println("  Model:          " + config.model());
        out.println("  Base URL:       " + config.baseUrl());
        out.println("  Max Tokens:     " + config.maxTokens());
        out.println("  Context Window: " + config.contextWindow());
        out.println("  Temperature:    " + config.temperature());
        out.println("  Timeout:        " + config.timeoutSeconds() + "s");
        out.println("  System Prompt:  " + agent.context().systemPrompt());
        out.println("  Thinking:       " + (showThinking ? "visible" : "hidden"));
    }

    private void printMessages() {
        List<Message> msgs = agent.messages();
        out.println(DIM + "Messages in context: " + msgs.size() + RESET);
    }

    // ── /history — Show conversation content ───────────────

    private void printHistory(String arg) {
        List<Message> msgs = agent.messages();
        if (msgs.isEmpty()) {
            out.println(DIM + "No messages in conversation." + RESET);
            return;
        }

        int limit = msgs.size();
        if (!arg.isEmpty()) {
            try {
                limit = Math.min(Integer.parseInt(arg), msgs.size());
            } catch (NumberFormatException e) {
                // ignore, show all
            }
        }

        List<Message> display = msgs.subList(Math.max(0, msgs.size() - limit), msgs.size());

        out.println(BOLD + "\n─── Conversation History ───" + RESET);
        for (Message msg : display) {
            switch (msg) {
                case Message.UserMessage um -> {
                    String text = Content.joinAllTextRaw(um.content());
                    out.println(BLUE + "  👤 You: " + RESET + truncate(text, 300));
                }
                case Message.AssistantMessage am -> {
                    String text = Content.joinAllTextRaw(am.content());
                    text = stripThinking(text);
                    if (!text.isEmpty()) {
                        out.println(GREEN + "  🤖 Agent: " + RESET + truncate(text, 300));
                    }
                    if (am.errorMessage() != null) {
                        out.println(RED + "  ⚠ Error: " + am.errorMessage() + RESET);
                    }
                }
                default -> {} // skip system/tool messages for readability
            }
        }
        out.println(DIM + "  ─── " + display.size() + " messages shown ───" + RESET);
    }

    // ── /thinking — Toggle thinking display ────────────────

    private void toggleThinking() {
        showThinking = !showThinking;
        out.println(DIM + "  💭 Thinking display: " +
                (showThinking ? GREEN + "on" : RED + "off") + RESET);
    }

    // ── /system — Change system prompt ─────────────────────

    private void setSystemPrompt(String prompt) {
        if (prompt.isEmpty()) {
            out.println(DIM + "  Current system prompt: " + RESET);
            out.println("  " + agent.context().systemPrompt());
            out.println();
            out.println(DIM + "  Usage: /system <new prompt>" + RESET);
            return;
        }
        agent.context().setSystemPrompt(prompt);
        out.println(DIM + "  ✓ System prompt updated." + RESET);
        out.println("  " + DIM + prompt + RESET);
    }

    // ── /save — Export conversation to JSONL ───────────────

    private void saveConversation(String arg) {
        List<Message> msgs = agent.messages();
        if (msgs.isEmpty()) {
            out.println(DIM + "  No messages to save." + RESET);
            return;
        }

        String filename = arg.isEmpty()
                ? "chat-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                        .withZone(ZoneId.systemDefault()).format(Instant.now()) + ".jsonl"
                : arg;

        Path path = Path.of(filename);
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            // Write metadata header
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("type", "session_meta");
            meta.put("provider", config.provider());
            meta.put("model", config.model());
            meta.put("timestamp", Instant.now().toString());
            meta.put("message_count", msgs.size());
            writer.write(JSON.writeValueAsString(meta));
            writer.newLine();

            // Write each message
            for (Message msg : msgs) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("type", "message");
                if (msg instanceof Message.UserMessage um) {
                    entry.put("role", "user");
                    entry.put("content", Content.joinAllTextRaw(um.content()));
                } else if (msg instanceof Message.AssistantMessage am) {
                    entry.put("role", "assistant");
                    entry.put("content", stripThinking(Content.joinAllTextRaw(am.content())));
                    if (am.usage().totalTokensWithCache() > 0) {
                        entry.put("input_tokens", am.usage().inputTokens());
                        entry.put("output_tokens", am.usage().outputTokens());
                    }
                } else {
                    continue;
                }
                entry.put("timestamp", msg.timestamp());
                writer.write(JSON.writeValueAsString(entry));
                writer.newLine();
            }

            out.println(DIM + "  💾 Saved " + msgs.size() + " messages to " +
                    path.toAbsolutePath() + RESET);
        } catch (Exception e) {
            out.println(RED + "  ✗ Failed to save: " + e.getMessage() + RESET);
        }
    }

    // ── /stats — Session statistics ────────────────────────

    private void printStats() {
        Duration elapsed = Duration.between(sessionStart, Instant.now());
        long minutes = elapsed.toMinutes();
        long seconds = elapsed.toSeconds() % 60;

        out.println(BOLD + "\n─── Session Statistics ───" + RESET);
        out.println("  Duration:        " + minutes + "m " + seconds + "s");
        out.println("  Turns:           " + totalTurns);
        out.println("  Messages:        " + agent.messages().size());
        out.println("  Input tokens:    " + totalInputTokens);
        out.println("  Output tokens:   " + totalOutputTokens);
        out.println("  Total tokens:    " + (totalInputTokens + totalOutputTokens));
        if (toolRegistry != null) {
            out.println("  Tools available: " + toolRegistry.size());
        }
    }

    // ── Utility ────────────────────────────────────────────

    private String formatArgs(Map<String, Object> args) {
        if (args == null || args.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("(");
        int i = 0;
        for (var entry : args.entrySet()) {
            if (i > 0) sb.append(", ");
            String val = String.valueOf(entry.getValue());
            if (val.length() > 60) val = val.substring(0, 57) + "...";
            sb.append(entry.getKey()).append("=").append(val);
            if (++i >= 3 && args.size() > 3) {
                sb.append(", +").append(args.size() - 3).append(" more");
                break;
            }
        }
        sb.append(")");
        return sb.toString();
    }

    private String truncate(String text, int maxLen) {
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen - 3) + "...";
    }

    @Override
    public void close() {
        try {
            agent.close();
        } catch (Exception e) {
            log.debug("Error closing agent", e);
        }
    }
}
