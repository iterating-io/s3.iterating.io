package io.iterating.s3.consumer.messaging;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record S3ObjectEvent(
        String bucket,
        String key,
        String versionId,
        String eventId,
        Map<String, Object> metadata) {

    public void validate() {
        if (bucket == null || bucket.isBlank()) {
            throw new InvalidS3ObjectEventException("S3 object event bucket is required");
        }
        if (key == null || key.isBlank()) {
            throw new InvalidS3ObjectEventException("S3 object event key is required");
        }
    }
}