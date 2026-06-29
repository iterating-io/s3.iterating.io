package io.iterating.s3.consumer.config;

import java.net.URI;
import java.util.Optional;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

@Validated
@ConfigurationProperties(prefix = "s3")
public record S3ReconciliationProperties(
        @NotBlank
        String backupBucket,
        @NotBlank
        String region,
        String endpointOverride) {

    public Optional<URI> endpointOverrideUri() {
        if (endpointOverride == null || endpointOverride.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(URI.create(endpointOverride));
    }
}
