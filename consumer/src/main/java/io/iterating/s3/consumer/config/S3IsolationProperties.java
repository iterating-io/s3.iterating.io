package io.iterating.s3.consumer.config;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

@Validated
@ConfigurationProperties(prefix = "s3-isolation")
public record S3IsolationProperties(
        @NotBlank String backupBucket,
        @NotBlank String backupPrefix,
        List<String> allowedSourceBuckets,
        @NotBlank String region,
        String endpointOverride) {

    public boolean acceptsSourceBucket(String bucket) {
        return allowedSourceBuckets == null || allowedSourceBuckets.isEmpty() || allowedSourceBuckets.contains(bucket);
    }

    public Optional<URI> endpointOverrideUri() {
        if (endpointOverride == null || endpointOverride.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(URI.create(endpointOverride));
    }
}