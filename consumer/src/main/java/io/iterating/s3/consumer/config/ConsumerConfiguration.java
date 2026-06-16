package io.iterating.s3.consumer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.iterating.s3.consumer.messaging.S3ObjectEventMessageHandler;
import io.iterating.s3.consumer.service.S3IsolationService;
import io.iterating.s3.nats.config.NatsConsumerConfiguration;
import io.iterating.s3.nats.messaging.MessageHandler;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@Import({NatsConsumerConfiguration.class, S3Configuration.class})
public class ConsumerConfiguration {

    @Bean
    S3IsolationService s3IsolationService(S3Client s3Client, S3IsolationProperties properties) {
        return new S3IsolationService(s3Client, properties);
    }

    @Bean
    MessageHandler messageHandler(ObjectMapper objectMapper, S3IsolationService isolationService) {
        return new S3ObjectEventMessageHandler(objectMapper, isolationService);
    }
}
