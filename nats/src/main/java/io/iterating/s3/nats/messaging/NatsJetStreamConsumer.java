package io.iterating.s3.nats.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import io.iterating.s3.nats.config.NatsProperties;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.PullSubscribeOptions;

public class NatsJetStreamConsumer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(NatsJetStreamConsumer.class);

    private final Connection connection;
    private final NatsProperties properties;
    private final MessageHandler messageHandler;
    private final ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /*
     * Testability helpers: adapter interface and a method to submit runnables
     * to the consumer's executor. These are package-private so tests in the
     * same package can exercise processing and thread naming without heavy
     * external mocking.
     */
    interface MessageAdapter {

        byte[] getData();

        String getSubject();

        void ack();

        void term();

        void nak();
    }

    void submitToExecutor(Runnable r) {
        executor.submit(r);
    }

    public NatsJetStreamConsumer(
            Connection connection,
            NatsProperties properties,
            MessageHandler messageHandler) {
        this.connection = connection;
        this.properties = properties;
        this.messageHandler = messageHandler;
        String prefix = "nats-s3-consumer-";
        if (properties != null) {
            String connName = properties.connectionName();
            if (connName != null && !connName.isBlank()) {
                prefix = connName + "-consumer-";
            }
        }
        this.executor = Executors.newSingleThreadExecutor(Thread.ofVirtual()
                .name(prefix, 0)
                .factory());
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            executor.submit(this::consume);
        }
    }

    @Override
    public void stop() {
        running.set(false);
        executor.shutdownNow();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    private void consume() {
        try {
            JetStream jetStream = connection.jetStream();
            JetStreamSubscription subscription = subscribe(jetStream);
            log.info("Started NATS JetStream consumer stream={} durable={} filterSubject={}",
                    properties.stream(), properties.durable(), properties.filterSubject());

            while (running.get()) {
                List<Message> messages = subscription.fetch(properties.batchSize(), properties.fetchTimeout());
                if (messages.isEmpty()) {
                    pause(properties.idleDelay());
                    continue;
                }
                for (Message message : messages) {
                    process(message);
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            running.set(false);
            log.error("NATS JetStream consumer stopped after an unrecoverable error", exception);
        }
    }

    private JetStreamSubscription subscribe(JetStream jetStream) throws Exception {
        PullSubscribeOptions options = PullSubscribeOptions.builder()
                .stream(properties.stream())
                .durable(properties.durable())
                .build();
        JetStreamSubscription subscription = jetStream.subscribe(properties.filterSubject(), options);
        connection.flush(Duration.ofSeconds(5));
        return subscription;
    }

    private void process(Message message) {
        // Print/log the received message (subject + UTF-8 payload) for visibility.
        try {
            byte[] data = message.getData();
            String payload = data == null ? "" : new String(data, StandardCharsets.UTF_8);
            log.info("NATS message received subject='{}' payload='{}'", message.getSubject(), payload);
        } catch (Exception e) {
            log.info("NATS message received subject='{}' (payload unavailable: {})", message.getSubject(), e.getMessage());
        }

        // Delegate to the adapter-based processing for easier testing.
        processAdapter(new MessageAdapter() {
            @Override
            public byte[] getData() {
                return message.getData();
            }

            @Override
            public String getSubject() {
                return message.getSubject();
            }

            @Override
            public void ack() {
                message.ack();
            }

            @Override
            public void term() {
                message.term();
            }

            @Override
            public void nak() {
                message.nak();
            }
        });
    }

    // Package-private for tests
    void processAdapter(MessageAdapter message) {
        try {
            messageHandler.handle(message.getData(), message.getSubject());
            message.ack();
        } catch (InvalidMessageException exception) {
            // Log the invalid-message reason so the application can observe malformed inputs
            log.warn("Invalid NATS message subject={} error={}", message.getSubject(), exception.getMessage(), exception);
            try {
                message.term();
            } catch (Exception ackException) {
                log.error("Failed to terminate invalid NATS message subject={}", message.getSubject(), ackException);
            }
        } catch (Exception exception) {
            // Log processing errors so operators can see S3/AWS or other failures
            String subj = message.getSubject();
            log.error("Failed to process NATS message subject=" + subj + "; scheduling for retry", exception);
            try {
                message.nak();
            } catch (Exception ackException) {
                log.error("Failed to negatively acknowledge NATS message subject=" + subj, ackException);
            }
        }
    }

    private void terminate(Message message, Exception exception) {
        try {
            message.term();
            log.warn("Terminated invalid NATS message subject={}: {}", message.getSubject(), exception.getMessage());
        } catch (Exception ackException) {
            log.error("Failed to terminate invalid NATS message subject={}", message.getSubject(), ackException);
        }
    }

    private void retry(Message message, Exception exception) {
        try {
            message.nak();
            log.warn("Scheduled NATS message for retry subject={}: {}", message.getSubject(), exception.getMessage());
        } catch (Exception ackException) {
            log.error("Failed to negatively acknowledge NATS message subject={}", message.getSubject(), ackException);
        }
    }

    private static void pause(Duration duration) throws InterruptedException {
        if (duration != null && !duration.isZero() && !duration.isNegative()) {
            Thread.sleep(duration);
        }
    }
}
