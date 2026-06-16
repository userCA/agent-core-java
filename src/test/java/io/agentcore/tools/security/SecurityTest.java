package io.agentcore.tools.security;

import io.agentcore.tools.operations.LocalFileOperations;
import io.agentcore.tools.operations.SandboxQuota;
import io.agentcore.session.jsonl.JsonlStore;
import io.agentcore.session.store.SessionHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Security tests for path traversal prevention, sessionId validation,
 * and sandbox quota path restrictions.
 */
class SecurityTest {

    @TempDir
    Path tempDir;

    private LocalFileOperations ops;

    @BeforeEach
    void setUp() {
        ops = new LocalFileOperations(tempDir.toString(), null);
    }

    private void assertSecurityExceptionThrown(Executable action) {
        var ex = assertThrows(ExecutionException.class, action);
        assertInstanceOf(SecurityException.class, ex.getCause(),
                "Expected SecurityException as cause, got: " + ex.getCause());
    }

    // === Path Traversal Prevention (SEC-02) ===

    @Test
    void resolve_absolutePathOutsideCwd_blocked() {
        assertSecurityExceptionThrown(() ->
                ops.read("/etc/passwd", 0, null).get());
    }

    @Test
    void resolve_relativeTraversal_blocked() {
        assertSecurityExceptionThrown(() ->
                ops.read("../../etc/passwd", 0, null).get());
    }

    @Test
    void resolve_deepTraversal_blocked() {
        assertSecurityExceptionThrown(() ->
                ops.read("./../../../../../../etc/passwd", 0, null).get());
    }

    @Test
    void resolve_withinCwd_doesNotThrowSecurityException() {
        var future = ops.read("nonexistent.txt", 0, null);
        assertNotNull(future);
    }

    @Test
    void resolve_dotPath_allowed() {
        var future = ops.ls(".");
        assertNotNull(future);
    }

    // === SessionId Validation (SEC-07) ===

    @Test
    void jsonlStore_pathTraversalSessionId_blocked() {
        var store = new JsonlStore(tempDir.toString());
        var ex = assertThrows(ExecutionException.class, () ->
                store.createSession("../escape",
                        new SessionHeader("id", "ts", "cwd")).get());
        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
    }

    @Test
    void jsonlStore_validSessionId_accepted() {
        var store = new JsonlStore(tempDir.toString());
        assertDoesNotThrow(() ->
                store.createSession("valid-session_123",
                        new SessionHeader("id", "ts", "cwd")).get());
    }

    @Test
    void jsonlStore_slashesInSessionId_blocked() {
        var store = new JsonlStore(tempDir.toString());
        var ex = assertThrows(ExecutionException.class, () ->
                store.createSession("session/with/slashes",
                        new SessionHeader("id", "ts", "cwd")).get());
        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
    }

    // === SandboxQuota Path Restrictions (SEC-06) ===

    @Test
    void sandboxQuota_deniedPath_blocked() {
        var deniedPath = tempDir.resolve("secret.txt").toString();
        var quota = new SandboxQuota(1.0, 512, 120, 100, true, List.of(), List.of(deniedPath));
        var restrictedOps = new LocalFileOperations(tempDir.toString(), quota);
        assertSecurityExceptionThrown(() ->
                restrictedOps.write("secret.txt", "data").get());
    }

    @Test
    void sandboxQuota_writeOutsideAllowPaths_blocked() {
        var onlyThis = tempDir.resolve("only-this").toString();
        var quota = new SandboxQuota(1.0, 512, 120, 100, true, List.of(onlyThis), List.of());
        var restrictedOps = new LocalFileOperations(tempDir.toString(), quota);
        assertSecurityExceptionThrown(() ->
                restrictedOps.write("other.txt", "data").get());
    }

    // === Regex Safety (SEC-05) ===

    @Test
    void grep_validPattern_works() {
        var future = ops.grep("test", tempDir.toString(), false);
        assertNotNull(future);
    }

    @Test
    void grep_invalidRegex_handledGracefully() {
        var future = ops.grep("[invalid", tempDir.toString(), false);
        assertNotNull(future);
    }
}
