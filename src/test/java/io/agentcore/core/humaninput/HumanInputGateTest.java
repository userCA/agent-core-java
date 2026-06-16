package io.agentcore.core.humaninput;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class HumanInputGateTest {

    @Test
    void requireInput_createsPendingFuture() {
        var gate = new HumanInputGate();
        var future = gate.requireInput("tc_1");

        assertNotNull(future);
        assertFalse(future.isDone());
        assertTrue(gate.isWaiting("tc_1"));
    }

    @Test
    void provideInput_resolvesPendingFuture() throws Exception {
        var gate = new HumanInputGate();
        var future = gate.requireInput("tc_1");

        boolean result = gate.provideInput("tc_1", Map.of("answer", "yes"));
        assertTrue(result);
        assertTrue(future.isDone());

        Map<String, Object> values = future.get(1, TimeUnit.SECONDS);
        assertEquals("yes", values.get("answer"));
        assertFalse(gate.isWaiting("tc_1"));
    }

    @Test
    void provideInput_unknownId_returnsFalse() {
        var gate = new HumanInputGate();
        boolean result = gate.provideInput("nonexistent", Map.of());
        assertFalse(result);
    }

    @Test
    void cancelAll_cancelsAllPendingFutures() {
        var gate = new HumanInputGate();
        var f1 = gate.requireInput("tc_1");
        var f2 = gate.requireInput("tc_2");

        gate.cancelAll();

        assertTrue(f1.isCancelled());
        assertTrue(f2.isCancelled());
        assertFalse(gate.isWaiting("tc_1"));
        assertFalse(gate.isWaiting("tc_2"));
    }

    @Test
    void multipleToolCalls_independentFutures() throws Exception {
        var gate = new HumanInputGate();
        var f1 = gate.requireInput("tc_1");
        var f2 = gate.requireInput("tc_2");

        // Resolve only tc_1
        gate.provideInput("tc_1", Map.of("val", "a"));
        assertTrue(f1.isDone());
        assertFalse(f2.isDone());

        // tc_2 still pending
        assertTrue(gate.isWaiting("tc_2"));
        assertFalse(gate.isWaiting("tc_1"));

        // Resolve tc_2
        gate.provideInput("tc_2", Map.of("val", "b"));
        assertTrue(f2.isDone());
    }

    @Test
    void isWaiting_returnsFalseForUnknownId() {
        var gate = new HumanInputGate();
        assertFalse(gate.isWaiting("nonexistent"));
    }

    @Test
    void cancelAll_emptyGate_noError() {
        var gate = new HumanInputGate();
        assertDoesNotThrow(gate::cancelAll);
    }
}
