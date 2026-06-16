package io.iterating.s3.nats.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import io.iterating.s3.nats.messaging.MessageHandler;
import io.iterating.s3.nats.messaging.NatsJetStreamConsumer;
import io.nats.client.Connection;

@Configuration
@Import(NatsConfiguration.class)
public class NatsConsumerConfiguration {

    @Bean
    NatsJetStreamConsumer natsJetStreamConsumer(
            Connection connection,
            NatsProperties properties,
            MessageHandler messageHandler) {
        return new NatsJetStreamConsumer(connection, properties, messageHandler);
    }
}
