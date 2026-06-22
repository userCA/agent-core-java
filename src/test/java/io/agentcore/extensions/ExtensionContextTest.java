package io.agentcore.extensions;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ExtensionContextTest {

    @Test
    void builderCreatesContext() {
        ExtensionContext ctx = ExtensionContext.builder()
                .sessionId("sess-1")
                .agent("agent-ref")
                .store("store-ref")
                .model("model-ref")
                .metadata(Map.of("key", "value"))
                .abortSignal(new AtomicBoolean(false))
                .build();

        assertEquals("sess-1", ctx.sessionId());
        assertEquals("agent-ref", ctx.agent());
        assertEquals("store-ref", ctx.store());
        assertEquals("model-ref", ctx.model());
        assertFalse(ctx.isAborted());
        assertEquals("value", ctx.metadata().get("key"));
    }

    @Test
    void isAbortedReturnsTrue() {
        ExtensionContext ctx = ExtensionContext.builder()
                .sessionId("sess-1")
                .abortSignal(new AtomicBoolean(true))
                .build();

        assertTrue(ctx.isAborted());
    }

    @Test
    void isAbortedReturnsFalseWhenNull() {
        ExtensionContext ctx = ExtensionContext.builder()
                .sessionId("sess-1")
                .build();

        assertFalse(ctx.isAborted());
    }

    @Test
    void metadataDefaultsToEmpty() {
        ExtensionContext ctx = ExtensionContext.builder()
                .sessionId("sess-1")
                .build();

        assertTrue(ctx.metadata().isEmpty());
    }

    @Test
    void metadataIsReadOnly() {
        ExtensionContext ctx = ExtensionContext.builder()
                .sessionId("sess-1")
                .metadata(Map.of("key", "value"))
                .build();

        assertThrows(UnsupportedOperationException.class, () ->
                ctx.metadata().put("new", "entry"));
    }

    @Test
    void builderRequiresSessionId() {
        assertThrows(IllegalStateException.class, () ->
                ExtensionContext.builder().build());
    }
}
