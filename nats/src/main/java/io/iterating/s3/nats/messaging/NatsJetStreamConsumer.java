package io.iterating.s3.nats.messaging;

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
    private final ExecutorService executor = Executors.newSingleThreadExecutor(Thread.ofVirtual()
            .name("nats-s3-consumer-", 0)
            .factory());
    private final AtomicBoolean running = new AtomicBoolean(false);

    public NatsJetStreamConsumer(
            Connection connection,
            NatsProperties properties,
            MessageHandler messageHandler) {
        this.connection = connection;
        this.properties = properties;
        this.messageHandler = messageHandler;
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
        try {
            messageHandler.handle(message.getData(), message.getSubject());
            message.ack();
        } catch (InvalidMessageException exception) {
            terminate(message, exception);
        } catch (Exception exception) {
            retry(message, exception);
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
