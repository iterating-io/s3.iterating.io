package io.iterating.s3.nats.messaging;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import io.iterating.s3.nats.config.NatsProperties;

public class NatsJetStreamConsumerLifecycleTest {

    @Test
    void executorThreadNameContainsConnectionName() throws Exception {
        NatsProperties props = new NatsProperties(
                "nats://localhost:4222",
                "",
                "test-connection",
                "test-stream",
                "test-durable",
                "test-subject",
                1,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1));

        final StringBuilder threadName = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);

        MessageHandler handler = (payload, subject) -> {
            threadName.append(Thread.currentThread().getName());
            latch.countDown();
        };

        NatsJetStreamConsumer consumer = new NatsJetStreamConsumer(null, props, handler);

        // Use processAdapter via executor to ensure the consumer's thread naming is used
        NatsJetStreamConsumer.MessageAdapter adapter = new NatsJetStreamConsumer.MessageAdapter() {
            @Override
            public byte[] getData() {
                return "data".getBytes();
            }

            @Override
            public String getSubject() {
                return "test-subject";
            }

            @Override
            public void ack() {
                /* noop */ }

            @Override
            public void term() {
                /* noop */ }

            @Override
            public void nak() {
                /* noop */ }
        };

        consumer.submitToExecutor(() -> consumer.processAdapter(adapter));

        boolean ok = latch.await(3, TimeUnit.SECONDS);
        consumer.stop();

        assertTrue(ok, "message was not handled in time");
        assertTrue(threadName.length() > 0, "thread name captured");
        assertTrue(threadName.toString().contains("test-connection"), "thread name contains connectionName");
    }
}
