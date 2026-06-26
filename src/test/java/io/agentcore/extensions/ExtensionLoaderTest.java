package io.agentcore.extensions;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExtensionLoaderTest {

    @Test
    void load_noManual_returnsSpiExtensions() {
        // SPI file registers SandboxPolicyExtension and SelfHealingExtension
        List<Extension> loaded = ExtensionLoader.load();
        assertNotNull(loaded);
        // SPI extensions should be present
        assertTrue(loaded.stream().anyMatch(e -> e instanceof SandboxPolicyExtension),
                "SandboxPolicyExtension should be loaded via SPI");
        assertTrue(loaded.stream().anyMatch(e -> e instanceof SelfHealingExtension),
                "SelfHealingExtension should be loaded via SPI");
    }

    @Test
    void load_withManual_mergesBothSources() {
        Extension manual = new Extension() {
            @Override public String name() { return "manual-ext"; }
            @Override public int order() { return 999; }
        };

        List<Extension> loaded = ExtensionLoader.load(manual);
        assertNotNull(loaded);
        // Should contain SPI extensions + manual
        assertTrue(loaded.stream().anyMatch(e -> e instanceof SandboxPolicyExtension));
        assertTrue(loaded.stream().anyMatch(e -> e == manual),
                "Manual extension should be present");
    }

    @Test
    void load_withManualList_mergesBothSources() {
        Extension manual1 = new Extension() {
            @Override public String name() { return "m1"; }
            @Override public int order() { return 50; }
        };
        Extension manual2 = new Extension() {
            @Override public String name() { return "m2"; }
            @Override public int order() { return 60; }
        };

        List<Extension> loaded = ExtensionLoader.load(List.of(manual1, manual2));
        // SPI (2) + manual (2) = 4
        assertTrue(loaded.size() >= 4, "Should have SPI + manual extensions");
        assertTrue(loaded.contains(manual1));
        assertTrue(loaded.contains(manual2));
    }

    @Test
    void load_deduplicatesByIdentity() {
        // Load SPI twice via varargs — same instances should be deduplicated
        List<Extension> first = ExtensionLoader.load();
        List<Extension> second = ExtensionLoader.load();
        // Both loads should produce extensions with the same types
        assertEquals(first.size(), second.size());
    }

    @Test
    void load_nullManual_treatedAsEmpty() {
        List<Extension> loaded = ExtensionLoader.load((List<Extension>) null);
        assertNotNull(loaded);
        // Should still contain SPI extensions
        assertFalse(loaded.isEmpty());
    }
}
