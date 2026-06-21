package io.iterating.s3.nats.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Validated
@ConfigurationProperties(prefix = "nats")
public record NatsProperties(
        @NotBlank
        String servers,
        @NotBlank
        String credentials,
        @NotBlank
        String connectionName,
        @NotBlank
        String stream,
        @NotBlank
        String durable,
        @NotBlank
        String filterSubject,
        @Min(1)
        int batchSize,
        Duration fetchTimeout,
        Duration idleDelay) {

    public List<String> serverList() {
        return List.of(servers.split(",")).stream()
                .map(String::trim)
                .filter(server -> !server.isBlank())
                .toList();
    }
}
