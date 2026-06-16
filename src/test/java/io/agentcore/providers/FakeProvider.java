package io.agentcore.providers;

import io.agentcore.providers.base.ModelProvider;
import io.agentcore.providers.base.StreamRequest;
import io.agentcore.providers.types.Model;
import io.agentcore.providers.types.StreamEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Flow;

/**
 * Test double for ModelProvider — produces pre-scripted StreamEvent sequences.
 * Mirrors the Python conftest.py FakeProvider pattern.
 *
 * Usage:
 * <pre>
 *   var fake = new FakeProvider();
 *   fake.queueScript(List.of(
 *       new StreamEvent.StreamTextDelta("Hello"),
 *       new StreamEvent.StreamMessageEnd("stop", 10, 5)
 *   ));
 *   // Each call to stream() consumes the next script
 * </pre>
 */
public class FakeProvider implements ModelProvider {
    private final Deque<List<StreamEvent>> scripts = new ArrayDeque<>();
    private final String providerName;
    private final List<Model> models;

    public FakeProvider() {
        this("fake", List.of(new Model("fake", "fake-model", 128000, 4096)));
    }

    public FakeProvider(String providerName, List<Model> models) {
        this.providerName = providerName;
        this.models = models;
    }

    public void queueScript(List<StreamEvent> events) {
        scripts.addLast(events);
    }

    public boolean hasPendingScripts() {
        return !scripts.isEmpty();
    }

    @Override
    public String name() {
        return providerName;
    }

    @Override
    public List<Model> listModels() {
        return models;
    }

    @Override
    public Flow.Publisher<StreamEvent> stream(StreamRequest request) {
        return subscriber -> {
            if (scripts.isEmpty()) {
                subscriber.onSubscribe(new NoOpSubscription());
                subscriber.onError(new IllegalStateException("No scripts queued"));
                return;
            }
            List<StreamEvent> script = scripts.pollFirst();
            subscriber.onSubscribe(new NoOpSubscription());

            Thread.ofVirtual().name("fake-provider").start(() -> {
                try {
                    for (var event : script) {
                        subscriber.onNext(event);
                    }
                    subscriber.onComplete();
                } catch (Exception e) {
                    subscriber.onError(e);
                }
            });
        };
    }

    private static class NoOpSubscription implements Flow.Subscription {
        @Override public void request(long n) {}
        @Override public void cancel() {}
    }
}
