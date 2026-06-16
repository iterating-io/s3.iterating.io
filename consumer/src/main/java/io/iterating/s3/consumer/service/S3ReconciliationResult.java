package io.iterating.s3.consumer.service;

public record S3ReconciliationResult(
        String sourceBucket,
        String sourceKey,
        String backupBucket,
        String backupKey,
        boolean sourceDeleted) {
}