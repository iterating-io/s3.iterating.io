package io.iterating.s3.nats.messaging;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import io.iterating.s3.nats.config.NatsProperties;

public class NatsJetStreamConsumerUnitTest {

    @Test
    void process_ackOnSuccess() throws Exception {
        NatsProperties props = new NatsProperties(
                "nats://localhost:4222",
                "",
                "test-conn",
                "stream",
                "durable",
                "subject",
                1,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1));

        MessageHandler handler = (payload, subject) -> {
            // successful handling
        };
        NatsJetStreamConsumer consumer = new NatsJetStreamConsumer(null, props, handler);

        AtomicBoolean acked = new AtomicBoolean(false);
        AtomicBoolean termed = new AtomicBoolean(false);
        AtomicBoolean naked = new AtomicBoolean(false);

        NatsJetStreamConsumer.MessageAdapter adapter = new NatsJetStreamConsumer.MessageAdapter() {
            @Override
            public byte[] getData() {
                return "payload".getBytes();
            }

            @Override
            public String getSubject() {
                return "subject";
            }

            @Override
            public void ack() {
                acked.set(true);
            }

            @Override
            public void term() {
                termed.set(true);
            }

            @Override
            public void nak() {
                naked.set(true);
            }
        };

        consumer.processAdapter(adapter);

        assertTrue(acked.get());
        assertFalse(termed.get());
        assertFalse(naked.get());
    }

    @Test
    void process_termOnInvalidMessage() throws Exception {
        NatsProperties props = new NatsProperties(
                "nats://localhost:4222",
                "",
                "test-conn",
                "stream",
                "durable",
                "subject",
                1,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1));

        MessageHandler handler = (payload, subject) -> {
            throw new InvalidMessageException("bad", null);
        };
        NatsJetStreamConsumer consumer = new NatsJetStreamConsumer(null, props, handler);

        AtomicBoolean acked = new AtomicBoolean(false);
        AtomicBoolean termed = new AtomicBoolean(false);
        AtomicBoolean naked = new AtomicBoolean(false);

        NatsJetStreamConsumer.MessageAdapter adapter = new NatsJetStreamConsumer.MessageAdapter() {
            @Override
            public byte[] getData() {
                return new byte[0];
            }

            @Override
            public String getSubject() {
                return "subject";
            }

            @Override
            public void ack() {
                acked.set(true);
            }

            @Override
            public void term() {
                termed.set(true);
            }

            @Override
            public void nak() {
                naked.set(true);
            }
        };

        consumer.processAdapter(adapter);

        assertFalse(acked.get());
        assertTrue(termed.get());
        assertFalse(naked.get());
    }

    @Test
    void process_nakOnOtherException() throws Exception {
        NatsProperties props = new NatsProperties(
                "nats://localhost:4222",
                "",
                "test-conn",
                "stream",
                "durable",
                "subject",
                1,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1));

        MessageHandler handler = (payload, subject) -> {
            throw new RuntimeException("boom");
        };
        NatsJetStreamConsumer consumer = new NatsJetStreamConsumer(null, props, handler);

        AtomicBoolean acked = new AtomicBoolean(false);
        AtomicBoolean termed = new AtomicBoolean(false);
        AtomicBoolean naked = new AtomicBoolean(false);

        NatsJetStreamConsumer.MessageAdapter adapter = new NatsJetStreamConsumer.MessageAdapter() {
            @Override
            public byte[] getData() {
                return new byte[0];
            }

            @Override
            public String getSubject() {
                return "subject";
            }

            @Override
            public void ack() {
                acked.set(true);
            }

            @Override
            public void term() {
                termed.set(true);
            }

            @Override
            public void nak() {
                naked.set(true);
            }
        };

        consumer.processAdapter(adapter);

        assertFalse(acked.get());
        assertFalse(termed.get());
        assertTrue(naked.get());
    }
}
