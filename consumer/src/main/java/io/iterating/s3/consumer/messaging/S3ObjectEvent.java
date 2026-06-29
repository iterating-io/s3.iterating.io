package io.iterating.s3.consumer.messaging;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record S3ObjectEvent(
        String bucket,
        String path,
        List<String> objects) {

    public void validate() {
        if (bucket == null || bucket.isBlank()) {
            throw new InvalidS3ObjectEventException("S3 object event bucket is required");
        }
        // `path` and `objects` are optional: an empty `objects` list means "operate on all
        // objects under the given path". `path` may be empty to indicate the bucket root.
    }

    public List<String> keys() {
        String normalizedPath = (path == null || path.isBlank()) ? "" : (path.endsWith("/") ? path : path + "/");
        if (objects == null || objects.isEmpty()) {
            return List.of();
        }
        return objects.stream()
                .map(object -> normalizedPath + object)
                .toList();
    }
}
