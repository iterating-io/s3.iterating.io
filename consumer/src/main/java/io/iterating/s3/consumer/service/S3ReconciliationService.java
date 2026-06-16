package io.iterating.s3.consumer.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import io.iterating.s3.consumer.config.S3ReconciliationProperties;
import io.iterating.s3.consumer.messaging.InvalidS3ObjectEventException;
import io.iterating.s3.consumer.messaging.S3ObjectEvent;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

public class S3ReconciliationService {

    private final S3Client s3Client;
    private final S3ReconciliationProperties properties;

    public S3ReconciliationService(S3Client s3Client, S3ReconciliationProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    public S3ReconciliationResult reconcile(S3ObjectEvent event) {
        event.validate();
        if (!properties.acceptsSourceBucket(event.bucket())) {
            throw new InvalidS3ObjectEventException("Source bucket is not allowed: " + event.bucket());
        }

        String backupKey = backupKey(event);
        HeadObjectResponse sourceHead = headSource(event, backupKey);
        if (sourceHead == null) {
            return new S3ReconciliationResult(event.bucket(), event.key(), properties.backupBucket(), backupKey, false);
        }
        copyToBackup(event, backupKey);
        verifyBackup(event, backupKey, sourceHead.contentLength());
        deleteSource(event);

        return new S3ReconciliationResult(event.bucket(), event.key(), properties.backupBucket(), backupKey, true);
    }

    private HeadObjectResponse headSource(S3ObjectEvent event, String backupKey) {
        try {
            return s3Client.headObject(sourceHeadRequest(event));
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404 && backupExists(backupKey)) {
                return null;
            }
            throw new S3ReconciliationException("Failed to read source object metadata", exception);
        }
    }

    private void copyToBackup(S3ObjectEvent event, String backupKey) {
        CopyObjectRequest.Builder builder = CopyObjectRequest.builder()
                .sourceBucket(event.bucket())
                .sourceKey(event.key())
                .bucket(properties.backupBucket())
                .key(backupKey);

        if (hasText(event.versionId())) {
            builder.sourceVersionId(event.versionId());
        }

        s3Client.copyObject(builder.build());
    }

    private void verifyBackup(S3ObjectEvent event, String backupKey, Long expectedContentLength) {
        HeadObjectResponse backupHead = s3Client.headObject(HeadObjectRequest.builder()
                .bucket(properties.backupBucket())
                .key(backupKey)
                .build());

        if (expectedContentLength != null && backupHead.contentLength() != expectedContentLength) {
            throw new S3ReconciliationException("Backup size does not match source size for " + event.bucket() + "/" + event.key());
        }
    }

    private void deleteSource(S3ObjectEvent event) {
        DeleteObjectRequest.Builder builder = DeleteObjectRequest.builder()
                .bucket(event.bucket())
                .key(event.key());

        if (hasText(event.versionId())) {
            builder.versionId(event.versionId());
        }

        s3Client.deleteObject(builder.build());
    }

    private boolean backupExists(String backupKey) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.backupBucket())
                    .key(backupKey)
                    .build());
            return true;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return false;
            }
            throw exception;
        }
    }

    private HeadObjectRequest sourceHeadRequest(S3ObjectEvent event) {
        HeadObjectRequest.Builder builder = HeadObjectRequest.builder()
                .bucket(event.bucket())
                .key(event.key());

        if (hasText(event.versionId())) {
            builder.versionId(event.versionId());
        }

        return builder.build();
    }

    private String backupKey(S3ObjectEvent event) {
        String prefix = properties.backupPrefix().replaceAll("^/+|/+$", "");
        String digest = sha256(event.bucket() + "\n" + event.key() + "\n" + nullToEmpty(event.versionId()) + "\n" + nullToEmpty(event.eventId()));
        String fileName = event.key().substring(event.key().lastIndexOf('/') + 1);
        if (fileName.isBlank()) {
            fileName = "object";
        }
        return prefix + "/" + event.bucket() + "/" + digest + "/" + fileName;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}