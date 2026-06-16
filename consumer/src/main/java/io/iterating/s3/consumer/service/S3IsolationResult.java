package io.iterating.s3.consumer.service;

public record S3IsolationResult(
        String sourceBucket,
        String sourceKey,
        String backupBucket,
        String backupKey,
        boolean sourceDeleted) {
}