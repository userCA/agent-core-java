package io.agentcore.extensions;

import io.agentcore.extensions.HookTypes.*;
import io.agentcore.model.AgentEvent;
import io.agentcore.model.Content;
import io.agentcore.model.Content.TextContent;
import io.agentcore.model.Content.ToolCallContent;
import io.agentcore.model.ToolResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ExtensionRunnerTest {

    // ── Helpers ──

    private static ToolCallContent toolCall(String name) {
        return new ToolCallContent("tc1", name, Map.of("key", "val"));
    }

    private static ToolCallContext toolCtx(String name) {
        return new ToolCallContext(toolCall(name), Map.of("key", "val"));
    }

    private static AfterToolCallContext afterCtx(String name, boolean isError) {
        return new AfterToolCallContext(toolCall(name), Map.of("key", "val"),
                new ToolResult("original"), isError);
    }

    private static Extension namedExt(String name, int order) {
        return new Extension() {
            @Override public String name() { return name; }
            @Override public int order() { return order; }
        };
    }

    // ── Basic ──

    @Test
    void emptyExtensions_hasExtensionsFalse() {
        var runner = new ExtensionRunner(List.of());
        assertFalse(runner.hasExtensions());
        assertNull(runner.onBeforeToolCall(toolCtx("bash")));
        assertNull(runner.onAfterToolCall(afterCtx("bash", true)));
        assertNull(runner.onBeforeAgentStart("prompt", "sys"));
    }

    @Test
    void nullExtensions_treatedAsEmpty() {
        var runner = new ExtensionRunner(null);
        assertFalse(runner.hasExtensions());
    }

    // ── Ordering ──

    @Test
    void extensions_sortedByOrder() {
        List<String> callOrder = new ArrayList<>();

        Extension first = new Extension() {
            @Override public String name() { return "first"; }
            @Override public int order() { return -10; }
            @Override public ToolCallHookResult onBeforeToolCall(ToolCallContext ctx) {
                callOrder.add("first");
                return null;
            }
        };
        Extension second = new Extension() {
            @Override public String name() { return "second"; }
            @Override public int order() { return 10; }
            @Override public ToolCallHookResult onBeforeToolCall(ToolCallContext ctx) {
                callOrder.add("second");
                return null;
            }
        };

        var runner = new ExtensionRunner(List.of(second, first));
        runner.onBeforeToolCall(toolCtx("bash"));

        assertEquals(List.of("first", "second"), callOrder);
    }

    // ── Error isolation ──

    @Test
    void onBeforeToolCall_exceptionDoesNotStopOthers() {
        Extension throwing = new Extension() {
            @Override public String name() { return "thrower"; }
            @Override public ToolCallHookResult onBeforeToolCall(ToolCallContext ctx) {
                throw new RuntimeException("boom");
            }
        };
        Extension normal = new Extension() {
            @Override public String name() { return "normal"; }
            @Override public int order() { return 10; }
            @Override public ToolCallHookResult onBeforeToolCall(ToolCallContext ctx) {
                return new ToolCallHookResult.InjectMetadata(Map.of("ok", true));
            }
        };

        var runner = new ExtensionRunner(List.of(throwing, normal));
        var result = runner.onBeforeToolCall(toolCtx("bash"));

        assertInstanceOf(ToolCallHookResult.InjectMetadata.class, result);
    }

    // ── onBeforeToolCall ──

    @Nested
    class BeforeToolCallTests {

        @Test
        void block_returnsImmediately() {
            Extension blocker = new Extension() {
                @Override public String name() { return "blocker"; }
                @Override public ToolCallHookResult onBeforeToolCall(ToolCallContext ctx) {
                    return new ToolCallHookResult.Block("not allowed");
                }
            };
            AtomicInteger called = new AtomicInteger(0);
            Extension after = new Extension() {
                @Override public String name() { return "after"; }
                @Override public int order() { return 10; }
                @Override public ToolCallHookResult onBeforeToolCall(ToolCallContext ctx) {
                    called.incrementAndGet();
                    return null;
                }
            };

            var runner = new ExtensionRunner(List.of(blocker, after));
            var result = runner.onBeforeToolCall(toolCtx("bash"));

            assertInstanceOf(ToolCallHookResult.Block.class, result);
            assertEquals(0, called.get()); // after was not called
        }

        @Test
        void proceed_mergesArguments() {
            Extension ext = new Extension() {
                @Override public String name() { return "mutator"; }
                @Override public ToolCallHookResult onBeforeToolCall(ToolCallContext ctx) {
                    return new ToolCallHookResult.Proceed(Map.of("extra", "value"));
                }
            };

            var runner = new ExtensionRunner(List.of(ext));
            var result = runner.onBeforeToolCall(toolCtx("bash"));

            assertInstanceOf(ToolCallHookResult.Proceed.class, result);
            var proceed = (ToolCallHookResult.Proceed) result;
            assertEquals("value", proceed.mutatedArguments().get("extra"));
        }

        @Test
        void injectMetadata_preservedInResult() {
            Extension ext = new Extension() {
                @Override public String name() { return "meta"; }
                @Override public ToolCallHookResult onBeforeToolCall(ToolCallContext ctx) {
                    return new ToolCallHookResult.InjectMetadata(Map.of("quota", 100));
                }
            };

            var runner = new ExtensionRunner(List.of(ext));
            var result = runner.onBeforeToolCall(toolCtx("bash"));

            assertInstanceOf(ToolCallHookResult.InjectMetadata.class, result);
            assertEquals(100, ((ToolCallHookResult.InjectMetadata) result).metadata().get("quota"));
        }
    }

    // ── afterToolCall ──

    @Nested
    class AfterToolCallTests {

        @Test
        void noModification_returnsNull() {
            Extension noop = new Extension() {
                @Override public String name() { return "noop"; }
                @Override public AfterToolCallHookResult onAfterToolCall(AfterToolCallContext ctx) {
                    return null;
                }
            };

            var runner = new ExtensionRunner(List.of(noop));
            assertNull(runner.onAfterToolCall(afterCtx("bash", true)));
        }

        @Test
        void modifyResult_overridesContent() {
            Extension ext = new Extension() {
                @Override public String name() { return "modifier"; }
                @Override public AfterToolCallHookResult onAfterToolCall(AfterToolCallContext ctx) {
                    return new AfterToolCallHookResult.ModifyResult(
                            List.of(new TextContent("changed")));
                }
            };

            var runner = new ExtensionRunner(List.of(ext));
            var result = runner.onAfterToolCall(afterCtx("bash", true));

            assertInstanceOf(AfterToolCallHookResult.ModifyResult.class, result);
            var mr = (AfterToolCallHookResult.ModifyResult) result;
            assertEquals("changed", Content.extractText(mr.content()));
        }

        @Test
        void multipleModifyResults_laterOverridesEarlier() {
            Extension first = new Extension() {
                @Override public String name() { return "first"; }
                @Override public AfterToolCallHookResult onAfterToolCall(AfterToolCallContext ctx) {
                    return new AfterToolCallHookResult.ModifyResult(
                            List.of(new TextContent("first")), null, true, null);
                }
            };
            Extension second = new Extension() {
                @Override public String name() { return "second"; }
                @Override public int order() { return 10; }
                @Override public AfterToolCallHookResult onAfterToolCall(AfterToolCallContext ctx) {
                    return new AfterToolCallHookResult.ModifyResult(
                            null, null, false, true);
                }
            };

            var runner = new ExtensionRunner(List.of(first, second));
            var result = runner.onAfterToolCall(afterCtx("bash", true));

            var mr = (AfterToolCallHookResult.ModifyResult) result;
            // Content from first ext (second didn't override)
            assertEquals("first", Content.extractText(mr.content()));
            // isError overridden by second ext to false
            assertFalse(mr.isError());
            // shouldTerminate from second ext
            assertTrue(mr.shouldTerminate());
        }
    }

    // ── onBeforeAgentStart ──

    @Test
    void onBeforeAgentStart_modifiesSystemPrompt() {
        Extension ext = new Extension() {
            @Override public String name() { return "prompt-mod"; }
            @Override public BeforeAgentStartResult onBeforeAgentStart(String prompt, String sys) {
                return new BeforeAgentStartResult.ModifySystemPrompt(sys + " + extra");
            }
        };

        var runner = new ExtensionRunner(List.of(ext));
        var result = runner.onBeforeAgentStart("user", "base");

        assertInstanceOf(BeforeAgentStartResult.ModifySystemPrompt.class, result);
        assertEquals("base + extra", ((BeforeAgentStartResult.ModifySystemPrompt) result).systemPrompt());
    }

    // ── add/remove extensions ──

    @Test
    void addExtension_visibleInSubsequentHookCalls() {
        var runner = new ExtensionRunner(List.of());
        assertFalse(runner.hasExtensions());

        Extension ext = new Extension() {
            @Override public String name() { return "dynamic"; }
            @Override public int order() { return 0; }
            @Override public void onEvent(AgentEvent event) { }
        };
        runner.addExtension(ext);

        assertTrue(runner.hasExtensions());
        assertEquals(1, runner.extensions().size());
        assertSame(ext, runner.extensions().get(0));
    }

    @Test
    void addExtensions_sortedByOrder() {
        var runner = new ExtensionRunner(List.of());
        Extension high = namedExt("high", 100);
        Extension low = namedExt("low", -100);

        runner.addExtensions(List.of(high, low));

        assertEquals(2, runner.extensions().size());
        assertEquals("low", runner.extensions().get(0).name());
        assertEquals("high", runner.extensions().get(1).name());
    }

    @Test
    void removeExtension_removesFromList() {
        Extension ext = namedExt("removable", 0);
        var runner = new ExtensionRunner(List.of(ext));
        assertEquals(1, runner.extensions().size());

        runner.removeExtension(ext);
        assertFalse(runner.hasExtensions());
        assertTrue(runner.extensions().isEmpty());
    }

    @Test
    void addAndRemove_concurrentSafe() throws InterruptedException {
        var runner = new ExtensionRunner(List.of());
        int threads = 10;
        var latch = new java.util.concurrent.CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            Thread.startVirtualThread(() -> {
                try {
                    Extension ext = namedExt("ext-" + idx, idx);
                    runner.addExtension(ext);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();

        assertEquals(threads, runner.extensions().size());
    }

    @Test
    void addExtensions_nullOrEmpty_noOp() {
        var runner = new ExtensionRunner(List.of(namedExt("base", 0)));
        runner.addExtensions(null);
        runner.addExtensions(List.of());
        assertEquals(1, runner.extensions().size());
    }

    // ── onEvent ──

    @Test
    void onEvent_forwardsToAllExtensions() {
        List<String> received = new ArrayList<>();
        Extension ext1 = new Extension() {
            @Override public String name() { return "e1"; }
            @Override public void onEvent(AgentEvent event) { received.add("e1"); }
        };
        Extension ext2 = new Extension() {
            @Override public String name() { return "e2"; }
            @Override public int order() { return 5; }
            @Override public void onEvent(AgentEvent event) { received.add("e2"); }
        };

        var runner = new ExtensionRunner(List.of(ext2, ext1));
        runner.onEvent(new AgentEvent.AgentStart());

        assertEquals(List.of("e1", "e2"), received);
    }
}
