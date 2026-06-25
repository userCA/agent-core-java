package io.agentcore.config;

import io.agentcore.agent.Agent;
import io.agentcore.agent.AgentLoopConfig;
import io.agentcore.llm.*;
import io.agentcore.llm.anthropic.AnthropicProvider;
import io.agentcore.llm.openai.OpenAIProvider;
import io.agentcore.tools.ToolRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

/**
 * Centralized configuration for agent-core-java.
 *
 * <p>Supports two injection sources with the following priority:
 * <ol>
 *   <li><b>Environment variables</b> (highest priority)</li>
 *   <li><b>Properties file</b> ({@code agent.properties} or custom path)</li>
 * </ol>
 *
 * <h3>Supported Environment Variables:</h3>
 * <pre>
 * AGENT_PROVIDER       - provider type: openai | anthropic | minimax | openai-compatible (default: openai)
 * AGENT_MODEL          - model ID (default: gpt-4o)
 * AGENT_API_KEY        - generic API key (fallback)
 * OPENAI_API_KEY       - OpenAI-specific key
 * ANTHROPIC_API_KEY    - Anthropic-specific key
 * MINIMAX_API_KEY      - MiniMax-specific key
 * AGENT_BASE_URL       - custom base URL for OpenAI-compatible providers
 * AGENT_SYSTEM_PROMPT  - system prompt override
 * AGENT_MAX_TOKENS     - max output tokens (default: 4096)
 * AGENT_CONTEXT_WINDOW - context window size (default: 128000)
 * AGENT_TEMPERATURE    - sampling temperature (default: 0.7)
 * AGENT_HTTP_PORT      - HTTP SSE server port (default: 8080)
 * AGENT_TIMEOUT        - request timeout in seconds (default: 60)
 * </pre>
 *
 * <h3>Properties File (agent.properties):</h3>
 * <pre>
 * agent.provider=openai
 * agent.model=gpt-4o
 * agent.api-key=sk-...
 * agent.base-url=https://api.openai.com/v1
 * agent.system-prompt=You are a helpful assistant.
 * agent.max-tokens=4096
 * agent.context-window=128000
 * agent.temperature=0.7
 * agent.http-port=8080
 * agent.timeout=60
 * </pre>
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 * // From environment variables only
 * AgentConfig config = AgentConfig.fromEnvironment();
 *
 * // From properties file (classpath or file path)
 * AgentConfig config = AgentConfig.fromProperties("agent.properties");
 *
 * // Combined: env vars override properties file
 * AgentConfig config = AgentConfig.load();
 *
 * // Create Agent directly
 * Agent agent = config.createAgent();
 *
 * // Or get components individually
 * ModelProvider provider = config.createProvider();
 * AuthSource authSource = config.createAuthSource();
 * ModelInfo model = config.createModel();
 * }</pre>
 */
public final class AgentConfig {

    private static final Logger log = LoggerFactory.getLogger(AgentConfig.class);

    // ── Default values ────────────────────────────────────────

    public static final String DEFAULT_PROVIDER = "openai";
    public static final String DEFAULT_MODEL = "gpt-4o";
    public static final int DEFAULT_MAX_TOKENS = 4096;
    public static final int DEFAULT_CONTEXT_WINDOW = 128000;
    public static final double DEFAULT_TEMPERATURE = 0.7;
    public static final int DEFAULT_HTTP_PORT = 8080;
    public static final int DEFAULT_TIMEOUT = 60;

    // Known provider base URLs
    private static final Map<String, String> DEFAULT_BASE_URLS = Map.of(
            "openai", "https://api.openai.com/v1",
            "anthropic", "https://api.anthropic.com/v1",
            "minimax", "https://api.minimaxi.com/v1"
    );

    // Known provider-specific env var names for API keys
    private static final Map<String, String> PROVIDER_ENV_KEYS = Map.of(
            "openai", "OPENAI_API_KEY",
            "anthropic", "ANTHROPIC_API_KEY",
            "minimax", "MINIMAX_API_KEY"
    );

    // Known default models per provider
    private static final Map<String, String> DEFAULT_MODELS = Map.of(
            "openai", "gpt-4o",
            "anthropic", "claude-sonnet-4-20250514",
            "minimax", "MiniMax-M2.7"
    );

    // ── Configuration fields ──────────────────────────────────

    private final String provider;
    private final String model;
    private final String apiKey;
    private final String baseUrl;
    private final String systemPrompt;
    private final int maxTokens;
    private final int contextWindow;
    private final double temperature;
    private final int httpPort;
    private final int timeoutSeconds;
    private final Map<String, String> extra;

    private AgentConfig(Builder builder) {
        this.provider = builder.provider;
        this.model = builder.model;
        this.apiKey = builder.apiKey;
        this.baseUrl = builder.baseUrl;
        this.systemPrompt = builder.systemPrompt;
        this.maxTokens = builder.maxTokens;
        this.contextWindow = builder.contextWindow;
        this.temperature = builder.temperature;
        this.httpPort = builder.httpPort;
        this.timeoutSeconds = builder.timeoutSeconds;
        this.extra = builder.extra != null ? Map.copyOf(builder.extra) : Map.of();
    }

    // ── Getters (record-style) ───────────────────────────────

    public String provider() { return provider; }
    public String model() { return model; }
    public String apiKey() { return apiKey; }
    public String baseUrl() { return baseUrl; }
    public String systemPrompt() { return systemPrompt; }
    public int maxTokens() { return maxTokens; }
    public int contextWindow() { return contextWindow; }
    public double temperature() { return temperature; }
    public int httpPort() { return httpPort; }
    public int timeoutSeconds() { return timeoutSeconds; }
    public Map<String, String> extra() { return extra; }

    // ── Getters (JavaBean-style, deprecated) ──────────────────

    /** @deprecated Use {@link #provider()} instead. */
    @Deprecated public String getProvider() { return provider; }
    /** @deprecated Use {@link #model()} instead. */
    @Deprecated public String getModel() { return model; }
    /** @deprecated Use {@link #apiKey()} instead. */
    @Deprecated public String getApiKey() { return apiKey; }
    /** @deprecated Use {@link #baseUrl()} instead. */
    @Deprecated public String getBaseUrl() { return baseUrl; }
    /** @deprecated Use {@link #systemPrompt()} instead. */
    @Deprecated public String getSystemPrompt() { return systemPrompt; }
    /** @deprecated Use {@link #maxTokens()} instead. */
    @Deprecated public int getMaxTokens() { return maxTokens; }
    /** @deprecated Use {@link #contextWindow()} instead. */
    @Deprecated public int getContextWindow() { return contextWindow; }
    /** @deprecated Use {@link #temperature()} instead. */
    @Deprecated public double getTemperature() { return temperature; }
    /** @deprecated Use {@link #httpPort()} instead. */
    @Deprecated public int getHttpPort() { return httpPort; }
    /** @deprecated Use {@link #timeoutSeconds()} instead. */
    @Deprecated public int getTimeoutSeconds() { return timeoutSeconds; }
    /** @deprecated Use {@link #extra()} instead. */
    @Deprecated public Map<String, String> getExtra() { return extra; }

    /**
     * Check if this is an OpenAI-compatible provider (uses OpenAI API format).
     */
    public boolean isOpenAICompatible() {
        return !"anthropic".equalsIgnoreCase(provider);
    }

    // ── Factory: create components ────────────────────────────

    /**
     * Create a {@link ModelProvider} based on this configuration.
     */
    public ModelProvider createProvider() {
        Duration timeout = Duration.ofSeconds(timeoutSeconds);

        if ("anthropic".equalsIgnoreCase(provider)) {
            String url = baseUrl != null ? baseUrl : DEFAULT_BASE_URLS.get("anthropic");
            return new AnthropicProvider(url, "anthropic", List.of(createModel()), timeout);
        }

        // All other providers use OpenAI-compatible API
        String url = resolveBaseUrl();
        String providerName = provider.toLowerCase();
        return new OpenAIProvider(url, providerName, List.of(createModel()), timeout);
    }

    /**
     * Create an {@link AuthSource} based on this configuration.
     */
    public AuthSource createAuthSource() {
        if (apiKey != null && !apiKey.isBlank()) {
            return AuthSource.staticAuth(apiKey);
        }

        // Try provider-specific env var
        String envVar = PROVIDER_ENV_KEYS.get(provider.toLowerCase());
        if (envVar != null) {
            String value = System.getenv(envVar);
            if (value != null && !value.isBlank()) {
                return AuthSource.staticAuth(value);
            }
        }

        // Try generic AGENT_API_KEY env var
        String generic = System.getenv("AGENT_API_KEY");
        if (generic != null && !generic.isBlank()) {
            return AuthSource.staticAuth(generic);
        }

        throw new IllegalStateException(
                "No API key configured. Set AGENT_API_KEY, " +
                        (envVar != null ? envVar + ", " : "") +
                        "or agent.api-key in properties file.");
    }

    /**
     * Create a {@link ModelInfo} based on this configuration.
     */
    public ModelInfo createModel() {
        String providerName = provider.toLowerCase();
        String modelId = model != null ? model : DEFAULT_MODELS.getOrDefault(providerName, DEFAULT_MODEL);
        return new ModelInfo(providerName, modelId, contextWindow, maxTokens);
    }

    /**
     * Create a fully-configured {@link Agent} with default tools and system prompt.
     */
    public Agent createAgent() {
        return createAgent(null, null);
    }

    /**
     * Create a fully-configured {@link Agent} with optional tool registry and system prompt.
     *
     * @param toolRegistry optional tool registry (null = no tools)
     * @param systemPromptOverride optional system prompt override (null = use configured prompt)
     */
    public Agent createAgent(ToolRegistry toolRegistry, String systemPromptOverride) {
        ModelProvider providerInstance = createProvider();
        ModelInfo modelInfo = createModel();
        AuthSource authSource = createAuthSource();
        String prompt = systemPromptOverride != null ? systemPromptOverride : this.systemPrompt;

        return Agent.create(providerInstance, modelInfo, authSource, toolRegistry, prompt);
    }

    /**
     * Create an {@link AgentLoopConfig} for advanced usage.
     */
    public AgentLoopConfig createLoopConfig(ToolRegistry toolRegistry) {
        ModelProvider providerInstance = createProvider();
        ModelInfo modelInfo = createModel();
        AuthSource authSource = createAuthSource();

        AgentLoopConfig.MessageAssembler assembler = providerInstance.createMessageConverter()::apply;

        return AgentLoopConfig.builder()
                .model(modelInfo)
                .streamFn(providerInstance::stream)
                .messageAssembler(assembler)
                .authResolver(authSource::resolve)
                .toolRegistry(toolRegistry)
                .build();
    }

    // ── Loading methods ───────────────────────────────────────

    /**
     * Load configuration from environment variables only.
     */
    public static AgentConfig fromEnvironment() {
        Builder b = new Builder();
        b.provider = envStr("AGENT_PROVIDER", DEFAULT_PROVIDER);
        b.model = envStr("AGENT_MODEL", null);
        b.apiKey = resolveApiKey(b.provider);
        b.baseUrl = envStr("AGENT_BASE_URL", null);
        b.systemPrompt = envStr("AGENT_SYSTEM_PROMPT", null);
        b.maxTokens = envInt("AGENT_MAX_TOKENS", DEFAULT_MAX_TOKENS);
        b.contextWindow = envInt("AGENT_CONTEXT_WINDOW", DEFAULT_CONTEXT_WINDOW);
        b.temperature = envDouble("AGENT_TEMPERATURE", DEFAULT_TEMPERATURE);
        b.httpPort = envInt("AGENT_HTTP_PORT", DEFAULT_HTTP_PORT);
        b.timeoutSeconds = envInt("AGENT_TIMEOUT", DEFAULT_TIMEOUT);
        log.debug("Loaded AgentConfig from environment: provider={}, model={}", b.provider, b.model);
        return new AgentConfig(b);
    }

    /**
     * Load configuration from a properties file.
     *
     * @param path path to the properties file, or a classpath resource name
     */
    public static AgentConfig fromProperties(String path) {
        Properties props = loadPropertiesFile(path);
        return fromPropertiesObject(props);
    }

    /**
     * Load configuration from a {@link Path}.
     */
    public static AgentConfig fromProperties(Path path) {
        Properties props = new Properties();
        try (InputStream is = Files.newInputStream(path)) {
            props.load(is);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load properties from " + path, e);
        }
        return fromPropertiesObject(props);
    }

    /**
     * Load from a {@link Properties} object.
     */
    public static AgentConfig fromPropertiesObject(Properties props) {
        Builder b = new Builder();
        b.provider = props.getProperty("agent.provider", DEFAULT_PROVIDER);
        b.model = props.getProperty("agent.model", null);
        b.apiKey = props.getProperty("agent.api-key", null);
        if (b.apiKey == null) b.apiKey = props.getProperty("agent.apiKey", null);
        b.baseUrl = props.getProperty("agent.base-url", null);
        if (b.baseUrl == null) b.baseUrl = props.getProperty("agent.baseUrl", null);
        b.systemPrompt = props.getProperty("agent.system-prompt", null);
        if (b.systemPrompt == null) b.systemPrompt = props.getProperty("agent.systemPrompt", null);
        b.maxTokens = parseIntSafe(props.getProperty("agent.max-tokens",
                props.getProperty("agent.maxTokens")), DEFAULT_MAX_TOKENS);
        b.contextWindow = parseIntSafe(props.getProperty("agent.context-window",
                props.getProperty("agent.contextWindow")), DEFAULT_CONTEXT_WINDOW);
        b.temperature = parseDoubleSafe(props.getProperty("agent.temperature"), DEFAULT_TEMPERATURE);
        b.httpPort = parseIntSafe(props.getProperty("agent.http-port",
                props.getProperty("agent.httpPort")), DEFAULT_HTTP_PORT);
        b.timeoutSeconds = parseIntSafe(props.getProperty("agent.timeout"), DEFAULT_TIMEOUT);

        // Collect extra properties (prefixed with agent.extra.)
        Map<String, String> extra = new LinkedHashMap<>();
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("agent.extra.")) {
                extra.put(key.substring("agent.extra.".length()), props.getProperty(key));
            }
        }
        b.extra = extra;

        log.debug("Loaded AgentConfig from properties: provider={}, model={}", b.provider, b.model);
        return new AgentConfig(b);
    }

    /**
     * Combined load: properties file → environment variables (env overrides props).
     *
     * <p>Searches for {@code agent.properties} in:
     * <ol>
     *   <li>Current working directory</li>
     *   <li>Classpath root</li>
     *   <li>{@code AGENT_CONFIG_FILE} environment variable</li>
     * </ol>
     */
    public static AgentConfig load() {
        // Try loading properties file first
        Properties fileProps = tryLoadDefaultProperties();

        // Build from properties
        AgentConfig propsConfig = fileProps != null ? fromPropertiesObject(fileProps) : null;

        // Environment variables override properties
        Builder b = new Builder();
        b.provider = envStr("AGENT_PROVIDER", propsConfig != null ? propsConfig.provider : DEFAULT_PROVIDER);
        b.model = envStr("AGENT_MODEL", propsConfig != null ? propsConfig.model : null);
        b.apiKey = resolveApiKeyWithFallback(b.provider, propsConfig != null ? propsConfig.apiKey : null);
        b.baseUrl = envStr("AGENT_BASE_URL", propsConfig != null ? propsConfig.baseUrl : null);
        b.systemPrompt = envStr("AGENT_SYSTEM_PROMPT", propsConfig != null ? propsConfig.systemPrompt : null);
        b.maxTokens = envInt("AGENT_MAX_TOKENS", propsConfig != null ? propsConfig.maxTokens : DEFAULT_MAX_TOKENS);
        b.contextWindow = envInt("AGENT_CONTEXT_WINDOW",
                propsConfig != null ? propsConfig.contextWindow : DEFAULT_CONTEXT_WINDOW);
        b.temperature = envDouble("AGENT_TEMPERATURE",
                propsConfig != null ? propsConfig.temperature : DEFAULT_TEMPERATURE);
        b.httpPort = envInt("AGENT_HTTP_PORT", propsConfig != null ? propsConfig.httpPort : DEFAULT_HTTP_PORT);
        b.timeoutSeconds = envInt("AGENT_TIMEOUT",
                propsConfig != null ? propsConfig.timeoutSeconds : DEFAULT_TIMEOUT);
        b.extra = propsConfig != null ? propsConfig.extra : Map.of();

        log.info("AgentConfig loaded: provider={}, model={}, baseUrl={}, hasApiKey={}",
                b.provider, b.model, b.baseUrl, b.apiKey != null);
        return new AgentConfig(b);
    }

    // ── Internal helpers ──────────────────────────────────────

    private String resolveBaseUrl() {
        if (baseUrl != null) return baseUrl;
        String providerLower = provider.toLowerCase();
        return DEFAULT_BASE_URLS.getOrDefault(providerLower, "https://api.openai.com/v1");
    }

    private static String resolveApiKey(String provider) {
        // Provider-specific env var
        String envVar = PROVIDER_ENV_KEYS.get(provider.toLowerCase());
        if (envVar != null) {
            String val = System.getenv(envVar);
            if (val != null && !val.isBlank()) return val;
        }
        // Generic fallback
        String generic = System.getenv("AGENT_API_KEY");
        if (generic != null && !generic.isBlank()) return generic;
        return null;
    }

    private static String resolveApiKeyWithFallback(String provider, String fallback) {
        String fromEnv = resolveApiKey(provider);
        return fromEnv != null ? fromEnv : fallback;
    }

    private static Properties tryLoadDefaultProperties() {
        // 1. Check AGENT_CONFIG_FILE env var
        String configFile = System.getenv("AGENT_CONFIG_FILE");
        if (configFile != null && !configFile.isBlank()) {
            Properties p = tryLoadFile(Path.of(configFile));
            if (p != null) {
                log.debug("Loaded config from AGENT_CONFIG_FILE: {}", configFile);
                return p;
            }
        }

        // 2. Current working directory
        Properties p = tryLoadFile(Path.of("agent.properties"));
        if (p != null) {
            log.debug("Loaded config from ./agent.properties");
            return p;
        }

        // 3. Classpath
        try (InputStream is = AgentConfig.class.getClassLoader().getResourceAsStream("agent.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                log.debug("Loaded config from classpath:agent.properties");
                return props;
            }
        } catch (IOException e) {
            log.debug("Failed to load classpath agent.properties", e);
        }

        return null;
    }

    private static Properties tryLoadFile(Path path) {
        if (Files.exists(path)) {
            try (InputStream is = Files.newInputStream(path)) {
                Properties props = new Properties();
                props.load(is);
                return props;
            } catch (IOException e) {
                log.warn("Failed to load config file: {}", path, e);
            }
        }
        return null;
    }

    private static Properties loadPropertiesFile(String path) {
        // Try as file path first
        Path filePath = Path.of(path);
        if (Files.exists(filePath)) {
            Properties props = new Properties();
            try (InputStream is = Files.newInputStream(filePath)) {
                props.load(is);
                return props;
            } catch (IOException e) {
                throw new RuntimeException("Failed to load properties from " + path, e);
            }
        }

        // Try as classpath resource
        try (InputStream is = AgentConfig.class.getClassLoader().getResourceAsStream(path)) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                return props;
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load classpath resource: " + path, e);
        }

        throw new RuntimeException("Properties file not found: " + path
                + " (checked filesystem and classpath)");
    }

    // ── Env/parse helpers ─────────────────────────────────────

    private static String envStr(String name, String defaultValue) {
        String val = System.getenv(name);
        return (val != null && !val.isBlank()) ? val : defaultValue;
    }

    private static int envInt(String name, int defaultValue) {
        String val = System.getenv(name);
        if (val != null && !val.isBlank()) {
            try { return Integer.parseInt(val.trim()); }
            catch (NumberFormatException e) {
                log.warn("Invalid integer for {}: {}, using default: {}", name, val, defaultValue);
            }
        }
        return defaultValue;
    }

    private static double envDouble(String name, double defaultValue) {
        String val = System.getenv(name);
        if (val != null && !val.isBlank()) {
            try { return Double.parseDouble(val.trim()); }
            catch (NumberFormatException e) {
                log.warn("Invalid double for {}: {}, using default: {}", name, val, defaultValue);
            }
        }
        return defaultValue;
    }

    private static int parseIntSafe(String val, int defaultValue) {
        if (val != null && !val.isBlank()) {
            try { return Integer.parseInt(val.trim()); }
            catch (NumberFormatException e) { return defaultValue; }
        }
        return defaultValue;
    }

    private static double parseDoubleSafe(String val, double defaultValue) {
        if (val != null && !val.isBlank()) {
            try { return Double.parseDouble(val.trim()); }
            catch (NumberFormatException e) { return defaultValue; }
        }
        return defaultValue;
    }

    // ── Builder ───────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String provider = DEFAULT_PROVIDER;
        private String model;
        private String apiKey;
        private String baseUrl;
        private String systemPrompt;
        private int maxTokens = DEFAULT_MAX_TOKENS;
        private int contextWindow = DEFAULT_CONTEXT_WINDOW;
        private double temperature = DEFAULT_TEMPERATURE;
        private int httpPort = DEFAULT_HTTP_PORT;
        private int timeoutSeconds = DEFAULT_TIMEOUT;
        private Map<String, String> extra;

        private Builder() {}

        public Builder provider(String v) { this.provider = v; return this; }
        public Builder model(String v) { this.model = v; return this; }
        public Builder apiKey(String v) { this.apiKey = v; return this; }
        public Builder baseUrl(String v) { this.baseUrl = v; return this; }
        public Builder systemPrompt(String v) { this.systemPrompt = v; return this; }
        public Builder maxTokens(int v) { this.maxTokens = v; return this; }
        public Builder contextWindow(int v) { this.contextWindow = v; return this; }
        public Builder temperature(double v) { this.temperature = v; return this; }
        public Builder httpPort(int v) { this.httpPort = v; return this; }
        public Builder timeoutSeconds(int v) { this.timeoutSeconds = v; return this; }
        public Builder extra(Map<String, String> v) { this.extra = v; return this; }

        public AgentConfig build() { return new AgentConfig(this); }
    }

    @Override
    public String toString() {
        return "AgentConfig{" +
                "provider='" + provider + '\'' +
                ", model='" + (model != null ? model : "default") + '\'' +
                ", baseUrl='" + (baseUrl != null ? baseUrl : "auto") + '\'' +
                ", hasApiKey=" + (apiKey != null) +
                ", maxTokens=" + maxTokens +
                ", contextWindow=" + contextWindow +
                ", temperature=" + temperature +
                ", httpPort=" + httpPort +
                ", timeout=" + timeoutSeconds + "s" +
                '}';
    }
}
