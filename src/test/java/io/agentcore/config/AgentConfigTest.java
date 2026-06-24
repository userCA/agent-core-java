package io.agentcore.config;

import io.agentcore.llm.ModelInfo;
import io.agentcore.llm.ModelProvider;
import io.agentcore.llm.anthropic.AnthropicProvider;
import io.agentcore.llm.openai.OpenAIProvider;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AgentConfig} — validates env/properties/builder injection paths.
 */
class AgentConfigTest {

    // ── Builder ────────────────────────────────────────────────

    @Nested
    class BuilderTests {

        @Test
        void defaultValues() {
            AgentConfig config = AgentConfig.builder().build();
            assertEquals("openai", config.getProvider());
            assertNull(config.getModel());
            assertNull(config.getApiKey());
            assertNull(config.getBaseUrl());
            assertEquals(4096, config.getMaxTokens());
            assertEquals(128000, config.getContextWindow());
            assertEquals(0.7, config.getTemperature(), 0.001);
            assertEquals(8080, config.getHttpPort());
            assertEquals(60, config.getTimeoutSeconds());
            assertTrue(config.getExtra().isEmpty());
        }

        @Test
        void customValues() {
            AgentConfig config = AgentConfig.builder()
                    .provider("minimax")
                    .model("MiniMax-M2.7")
                    .apiKey("sk-test-key")
                    .baseUrl("https://custom.api.com/v1")
                    .systemPrompt("You are a bot")
                    .maxTokens(8192)
                    .contextWindow(200000)
                    .temperature(0.9)
                    .httpPort(9090)
                    .timeoutSeconds(120)
                    .build();

            assertEquals("minimax", config.getProvider());
            assertEquals("MiniMax-M2.7", config.getModel());
            assertEquals("sk-test-key", config.getApiKey());
            assertEquals("https://custom.api.com/v1", config.getBaseUrl());
            assertEquals("You are a bot", config.getSystemPrompt());
            assertEquals(8192, config.getMaxTokens());
            assertEquals(200000, config.getContextWindow());
            assertEquals(0.9, config.getTemperature(), 0.001);
            assertEquals(9090, config.getHttpPort());
            assertEquals(120, config.getTimeoutSeconds());
        }

        @Test
        void isOpenAICompatible() {
            assertTrue(AgentConfig.builder().provider("openai").build().isOpenAICompatible());
            assertTrue(AgentConfig.builder().provider("minimax").build().isOpenAICompatible());
            assertTrue(AgentConfig.builder().provider("custom").build().isOpenAICompatible());
            assertFalse(AgentConfig.builder().provider("anthropic").build().isOpenAICompatible());
            assertFalse(AgentConfig.builder().provider("Anthropic").build().isOpenAICompatible());
        }
    }

    // ── Properties Loading ─────────────────────────────────────

    @Nested
    class PropertiesTests {

        @TempDir
        Path tempDir;

        @Test
        void loadFromPropertiesFile() throws Exception {
            Path propsFile = tempDir.resolve("test.properties");
            Files.writeString(propsFile, """
                    agent.provider=minimax
                    agent.model=MiniMax-M2.7
                    agent.api-key=sk-from-file
                    agent.base-url=https://api.minimaxi.com/v1
                    agent.max-tokens=8192
                    agent.context-window=204800
                    agent.temperature=0.8
                    agent.http-port=9090
                    agent.timeout=120
                    agent.system-prompt=Test prompt
                    """);

            AgentConfig config = AgentConfig.fromProperties(propsFile);

            assertEquals("minimax", config.getProvider());
            assertEquals("MiniMax-M2.7", config.getModel());
            assertEquals("sk-from-file", config.getApiKey());
            assertEquals("https://api.minimaxi.com/v1", config.getBaseUrl());
            assertEquals(8192, config.getMaxTokens());
            assertEquals(204800, config.getContextWindow());
            assertEquals(0.8, config.getTemperature(), 0.001);
            assertEquals(9090, config.getHttpPort());
            assertEquals(120, config.getTimeoutSeconds());
            assertEquals("Test prompt", config.getSystemPrompt());
        }

        @Test
        void loadFromPropertiesObject() {
            Properties props = new Properties();
            props.setProperty("agent.provider", "anthropic");
            props.setProperty("agent.model", "claude-sonnet-4-20250514");
            props.setProperty("agent.api-key", "sk-anthropic-key");
            props.setProperty("agent.max-tokens", "16384");
            props.setProperty("agent.context-window", "200000");

            AgentConfig config = AgentConfig.fromPropertiesObject(props);

            assertEquals("anthropic", config.getProvider());
            assertEquals("claude-sonnet-4-20250514", config.getModel());
            assertEquals("sk-anthropic-key", config.getApiKey());
            assertEquals(16384, config.getMaxTokens());
            assertEquals(200000, config.getContextWindow());
        }

        @Test
        void camelCaseProperties() {
            Properties props = new Properties();
            props.setProperty("agent.provider", "openai");
            props.setProperty("agent.apiKey", "sk-camel");
            props.setProperty("agent.baseUrl", "https://custom.com/v1");
            props.setProperty("agent.systemPrompt", "Camel prompt");
            props.setProperty("agent.maxTokens", "2048");
            props.setProperty("agent.contextWindow", "64000");
            props.setProperty("agent.httpPort", "3000");

            AgentConfig config = AgentConfig.fromPropertiesObject(props);

            assertEquals("sk-camel", config.getApiKey());
            assertEquals("https://custom.com/v1", config.getBaseUrl());
            assertEquals("Camel prompt", config.getSystemPrompt());
            assertEquals(2048, config.getMaxTokens());
            assertEquals(64000, config.getContextWindow());
            assertEquals(3000, config.getHttpPort());
        }

        @Test
        void extraProperties() {
            Properties props = new Properties();
            props.setProperty("agent.provider", "openai");
            props.setProperty("agent.extra.feature-flag", "true");
            props.setProperty("agent.extra.custom-endpoint", "/v2/chat");

            AgentConfig config = AgentConfig.fromPropertiesObject(props);

            assertEquals(2, config.getExtra().size());
            assertEquals("true", config.getExtra().get("feature-flag"));
            assertEquals("/v2/chat", config.getExtra().get("custom-endpoint"));
        }

        @Test
        void missingFileThrows() {
            assertThrows(RuntimeException.class, () ->
                    AgentConfig.fromProperties(tempDir.resolve("nonexistent.properties")));
        }

        @Test
        void defaultValuesForMissingKeys() {
            Properties props = new Properties();
            // Only provider, everything else defaults
            props.setProperty("agent.provider", "openai");

            AgentConfig config = AgentConfig.fromPropertiesObject(props);

            assertEquals("openai", config.getProvider());
            assertNull(config.getModel());
            assertNull(config.getApiKey());
            assertEquals(4096, config.getMaxTokens());
            assertEquals(128000, config.getContextWindow());
            assertEquals(0.7, config.getTemperature(), 0.001);
            assertEquals(8080, config.getHttpPort());
            assertEquals(60, config.getTimeoutSeconds());
        }

        @Test
        void invalidNumericFallsBackToDefault() {
            Properties props = new Properties();
            props.setProperty("agent.provider", "openai");
            props.setProperty("agent.max-tokens", "not-a-number");
            props.setProperty("agent.temperature", "invalid");
            props.setProperty("agent.http-port", "xyz");

            AgentConfig config = AgentConfig.fromPropertiesObject(props);

            assertEquals(4096, config.getMaxTokens());
            assertEquals(0.7, config.getTemperature(), 0.001);
            assertEquals(8080, config.getHttpPort());
        }
    }

    // ── Model Creation ────────────────────────────────────────

    @Nested
    class ModelCreationTests {

        @Test
        void createOpenAIModel() {
            AgentConfig config = AgentConfig.builder()
                    .provider("openai")
                    .model("gpt-4o")
                    .maxTokens(8192)
                    .contextWindow(128000)
                    .build();

            ModelInfo model = config.createModel();
            assertEquals("openai", model.provider());
            assertEquals("gpt-4o", model.id());
            assertEquals(128000, model.contextWindow());
            assertEquals(8192, model.maxOutputTokens());
        }

        @Test
        void createAnthropicModel() {
            AgentConfig config = AgentConfig.builder()
                    .provider("anthropic")
                    .model("claude-sonnet-4-20250514")
                    .maxTokens(16384)
                    .contextWindow(200000)
                    .build();

            ModelInfo model = config.createModel();
            assertEquals("anthropic", model.provider());
            assertEquals("claude-sonnet-4-20250514", model.id());
        }

        @Test
        void createMiniMaxModel() {
            AgentConfig config = AgentConfig.builder()
                    .provider("minimax")
                    .model("MiniMax-M2.7")
                    .maxTokens(16384)
                    .contextWindow(204800)
                    .build();

            ModelInfo model = config.createModel();
            assertEquals("minimax", model.provider());
            assertEquals("MiniMax-M2.7", model.id());
            assertEquals(204800, model.contextWindow());
        }

        @Test
        void defaultModelWhenNull() {
            // openai → gpt-4o
            AgentConfig config = AgentConfig.builder().provider("openai").build();
            assertEquals("gpt-4o", config.createModel().id());

            // anthropic → claude-sonnet-4-20250514
            config = AgentConfig.builder().provider("anthropic").build();
            assertEquals("claude-sonnet-4-20250514", config.createModel().id());

            // minimax → MiniMax-M2.7
            config = AgentConfig.builder().provider("minimax").build();
            assertEquals("MiniMax-M2.7", config.createModel().id());
        }
    }

    // ── Provider Creation ─────────────────────────────────────

    @Nested
    class ProviderCreationTests {

        @Test
        void createOpenAIProvider() {
            AgentConfig config = AgentConfig.builder()
                    .provider("openai")
                    .model("gpt-4o")
                    .apiKey("sk-test")
                    .build();

            ModelProvider provider = config.createProvider();
            assertInstanceOf(OpenAIProvider.class, provider);
            assertEquals("openai", provider.name());
        }

        @Test
        void createAnthropicProvider() {
            AgentConfig config = AgentConfig.builder()
                    .provider("anthropic")
                    .model("claude-sonnet-4-20250514")
                    .apiKey("sk-test")
                    .build();

            ModelProvider provider = config.createProvider();
            assertInstanceOf(AnthropicProvider.class, provider);
            assertEquals("anthropic", provider.name());
        }

        @Test
        void createMiniMaxProvider() {
            AgentConfig config = AgentConfig.builder()
                    .provider("minimax")
                    .model("MiniMax-M2.7")
                    .apiKey("sk-test")
                    .build();

            ModelProvider provider = config.createProvider();
            assertInstanceOf(OpenAIProvider.class, provider);
            assertEquals("minimax", provider.name());
        }

        @Test
        void customBaseUrlProvider() {
            AgentConfig config = AgentConfig.builder()
                    .provider("custom-llm")
                    .model("custom-model")
                    .apiKey("sk-test")
                    .baseUrl("https://my-llm.example.com/v1")
                    .build();

            ModelProvider provider = config.createProvider();
            assertInstanceOf(OpenAIProvider.class, provider);
            assertEquals("custom-llm", provider.name());
        }
    }

    // ── AuthSource ────────────────────────────────────────────

    @Nested
    class AuthSourceTests {

        @Test
        void explicitApiKey() {
            AgentConfig config = AgentConfig.builder()
                    .provider("openai")
                    .apiKey("sk-explicit")
                    .build();

            var auth = config.createAuthSource().resolve("openai");
            assertEquals("sk-explicit", auth.apiKey());
        }

        @Test
        void noApiKeyThrows() {
            AgentConfig config = AgentConfig.builder()
                    .provider("unknown-provider-xyz")
                    .build();

            assertThrows(IllegalStateException.class, () -> config.createAuthSource());
        }
    }

    // ── toString ──────────────────────────────────────────────

    @Nested
    class ToStringTests {

        @Test
        void toStringWithKey() {
            AgentConfig config = AgentConfig.builder()
                    .provider("openai")
                    .model("gpt-4o")
                    .apiKey("sk-test")
                    .build();

            String s = config.toString();
            assertTrue(s.contains("openai"));
            assertTrue(s.contains("gpt-4o"));
            assertTrue(s.contains("hasApiKey=true"));
            assertFalse(s.contains("sk-test")); // Don't leak key
        }

        @Test
        void toStringWithoutKey() {
            AgentConfig config = AgentConfig.builder()
                    .provider("anthropic")
                    .build();

            String s = config.toString();
            assertTrue(s.contains("hasApiKey=false"));
        }
    }

    // ── Environment (fromEnvironment uses System.getenv) ──────

    @Nested
    class EnvironmentTests {

        @Test
        void fromEnvironmentDefaults() {
            // When no env vars are set, should use defaults
            AgentConfig config = AgentConfig.fromEnvironment();

            // Provider will be "openai" by default
            assertNotNull(config.getProvider());
            // Most fields will have defaults
            assertEquals(4096, config.getMaxTokens());
            assertEquals(128000, config.getContextWindow());
            assertEquals(0.7, config.getTemperature(), 0.001);
            assertEquals(8080, config.getHttpPort());
            assertEquals(60, config.getTimeoutSeconds());
        }

        @Test
        void loadCombinesPropsAndEnv() {
            // load() should work even without a properties file (just env + defaults)
            AgentConfig config = AgentConfig.load();
            assertNotNull(config);
            assertNotNull(config.getProvider());
        }
    }
}
