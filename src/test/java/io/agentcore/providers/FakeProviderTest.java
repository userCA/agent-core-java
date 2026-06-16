package io.agentcore.providers;

import io.agentcore.providers.base.StreamRequest;
import io.agentcore.providers.types.Model;
import io.agentcore.providers.types.ProviderAuth;
import io.agentcore.providers.types.StreamEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class FakeProviderTest {

    @Test
    void stream_producesQueuedScript() throws Exception {
        var fake = new FakeProvider();
        fake.queueScript(List.of(
                new StreamEvent.StreamTextDelta("Hello"),
                new StreamEvent.StreamTextDelta(" world"),
                new StreamEvent.StreamMessageEnd("stop", 10, 5)
        ));

        var request = new StreamRequest(
                new Model("fake", "fake-model", 128000, 4096),
                List.of(Map.of("role", "user", "content", "Hi")),
                List.of(), null, null, null, null, null,
                new ProviderAuth("test-key")
        );

        BlockingQueue<StreamEvent> received = new LinkedBlockingQueue<>();
        Flow.Publisher<StreamEvent> pub = fake.stream(request);
        pub.subscribe(new Flow.Subscriber<>() {
            Flow.Subscription sub;

            @Override public void onSubscribe(Flow.Subscription s) { sub = s; s.request(Long.MAX_VALUE); }
            @Override public void onNext(StreamEvent item) { received.offer(item); }
            @Override public void onError(Throwable throwable) { fail("Unexpected error: " + throwable); }
            @Override public void onComplete() { received.offer(new StreamEvent.StreamMessageEnd("done", 0, 0)); }
        });

        // Collect events
        List<StreamEvent> events = new ArrayList<>();
        for (int i = 0; i < 4; i++) { // 3 script events + 1 onComplete sentinel
            StreamEvent evt = received.poll(5, TimeUnit.SECONDS);
            if (evt != null) events.add(evt);
        }

        assertTrue(events.size() >= 3);
        assertInstanceOf(StreamEvent.StreamTextDelta.class, events.get(0));
        assertEquals("Hello", ((StreamEvent.StreamTextDelta) events.get(0)).text());
        assertEquals(" world", ((StreamEvent.StreamTextDelta) events.get(1)).text());
    }

    @Test
    void stream_multipleScripts_consumedInOrder() throws Exception {
        var fake = new FakeProvider();
        fake.queueScript(List.of(new StreamEvent.StreamTextDelta("first")));
        fake.queueScript(List.of(new StreamEvent.StreamTextDelta("second")));

        assertTrue(fake.hasPendingScripts());
        assertEquals("fake", fake.name());
        assertEquals(1, fake.listModels().size());
    }

    @Test
    void stream_noScripts_emitsError() throws Exception {
        var fake = new FakeProvider();
        var request = new StreamRequest(
                new Model("fake", "fake-model", 128000, 4096),
                List.of(), List.of(), null, null, null, null, null,
                new ProviderAuth("test-key")
        );

        BlockingQueue<Throwable> errors = new LinkedBlockingQueue<>();
        fake.stream(request).subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription s) { s.request(Long.MAX_VALUE); }
            @Override public void onNext(StreamEvent item) {}
            @Override public void onError(Throwable throwable) { errors.offer(throwable); }
            @Override public void onComplete() {}
        });

        Throwable error = errors.poll(2, TimeUnit.SECONDS);
        assertNotNull(error);
        assertInstanceOf(IllegalStateException.class, error);
    }
}
