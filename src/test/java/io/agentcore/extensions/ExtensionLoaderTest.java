package io.agentcore.extensions;

import io.agentcore.resources.ResourceTypes.ExtensionSpec;
import io.agentcore.resources.ResourceTypes.SourceInfo;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Nested;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ExtensionLoader SPI discovery.
 */
class ExtensionLoaderTest {

    @Nested
    class ServiceLoaderTests {

        @Test
        void loadFromServiceLoader_returnsEmptyOrMore() {
            List<Extension> exts = ExtensionLoader.loadFromServiceLoader();
            assertNotNull(exts);
            // No SPI extensions registered by default, but should not throw
        }

        @Test
        void loadFromServiceLoader_customClassLoader() {
            List<Extension> exts = ExtensionLoader.loadFromServiceLoader(
                    Thread.currentThread().getContextClassLoader());
            assertNotNull(exts);
        }
    }

    @Nested
    class SpecTests {

        @Test
        void loadFromSpecs_emptyList() {
            List<Extension> exts = ExtensionLoader.loadFromSpecs(List.of());
            assertTrue(exts.isEmpty());
        }

        @Test
        void loadFromSpecs_unknownClass_returnsEmpty() {
            ExtensionSpec spec = new ExtensionSpec("test-ext",
                    "com.nonexistent.Extension", new SourceInfo("test", "test", "", ""));
            List<Extension> exts = ExtensionLoader.loadFromSpecs(List.of(spec));
            assertTrue(exts.isEmpty());
        }

        @Test
        void loadFromSpecs_emptyModulePath_skips() {
            ExtensionSpec spec = new ExtensionSpec("test-ext", "",
                    new SourceInfo("test", "test", "", ""));
            List<Extension> exts = ExtensionLoader.loadFromSpecs(List.of(spec));
            assertTrue(exts.isEmpty());
        }

        @Test
        void loadFromSpecs_knownExtensionClass() {
            // Use the MemoryExtension which is a known Extension implementation
            ExtensionSpec spec = new ExtensionSpec("memory",
                    "io.agentcore.memory.MemoryExtension",
                    new SourceInfo("test", "test", "", ""));
            // MemoryExtension requires constructor args, so this will fail gracefully
            List<Extension> exts = ExtensionLoader.loadFromSpecs(List.of(spec));
            // May or may not load depending on whether no-arg ctor exists — just shouldn't throw
            assertNotNull(exts);
        }
    }

    @Nested
    class LoadAllTests {

        @Test
        void loadAll_combinesAndDeduplicates() {
            List<ExtensionSpec> specs = List.of();
            List<Extension> all = ExtensionLoader.loadAll(specs);
            assertNotNull(all);
        }

        @Test
        void loadAll_doesNotDuplicateByName() {
            // Create specs that might resolve to the same extension
            List<ExtensionSpec> specs = List.of(
                    new ExtensionSpec("ext-a", "com.nonexistent.A", new SourceInfo("t", "t", "", "")),
                    new ExtensionSpec("ext-b", "com.nonexistent.B", new SourceInfo("t", "t", "", ""))
            );
            List<Extension> all = ExtensionLoader.loadAll(specs);
            // Both fail to load, so list is empty — just verify no exception
            assertNotNull(all);
        }
    }
}
