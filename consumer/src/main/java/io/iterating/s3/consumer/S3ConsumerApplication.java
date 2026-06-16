package io.iterating.s3.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;

import io.iterating.s3.consumer.config.ConsumerConfiguration;

@SpringBootConfiguration
@EnableAutoConfiguration
@ConfigurationPropertiesScan(basePackages = {
    "io.iterating.s3.consumer",
    "io.iterating.s3.nats"
})
@Import(ConsumerConfiguration.class)
public class S3ConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(S3ConsumerApplication.class, args);
    }
}
