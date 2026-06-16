package io.iterating.s3.consumer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class S3Configuration {

    @Bean
    S3Client s3Client(S3IsolationProperties properties) {
        var builder = S3Client.builder().region(Region.of(properties.region()));
        properties.endpointOverrideUri().ifPresent(builder::endpointOverride);
        return builder.build();
    }
}