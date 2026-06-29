package io.iterating.s3.consumer.service;

import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.iterating.s3.consumer.config.S3ReconciliationProperties;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Exception;

public class S3ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(S3ReconciliationService.class);
    private static final long VERIFY_BACKUP_DELAY_MILLIS = 3_000L;

    private final S3Client s3Client;
    private final S3ReconciliationProperties properties;

    public S3ReconciliationService(S3Client s3Client, S3ReconciliationProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    public S3ReconciliationResult backup(String sourceBucket, String sourceKey) {
        log.info("Reconciling S3 object: {}/{} -> backupBucket={}", sourceBucket, sourceKey, properties.backupBucket());

        String backupKey = backupKey(sourceBucket, sourceKey);
        HeadObjectResponse sourceHead = headSource(sourceBucket, sourceKey, backupKey);
        if (sourceHead == null) {
            return new S3ReconciliationResult(sourceBucket, sourceKey, properties.backupBucket(), backupKey, false);
        }
        copyToBackup(sourceBucket, sourceKey, backupKey);
        verifyBackup(sourceBucket, sourceKey, backupKey, sourceHead.eTag(), sourceHead.contentLength());
        deleteSource(sourceBucket, sourceKey);

        return new S3ReconciliationResult(sourceBucket, sourceKey, properties.backupBucket(), backupKey, true);
    }

    /**
     * Reconcile all objects under a given prefix, excluding any keys present in
     * the provided {@code excludeKeys} set. If {@code prefix} is null/empty,
     * operate on the whole bucket. This method handles pagination and will
     * iterate over all keys under the prefix.
     */
    public void reconcile(String bucket, String prefix, Set<String> excludeKeys) {
        String normalizedPrefix = (prefix == null || prefix.isBlank()) ? "" : (prefix.endsWith("/") ? prefix : prefix + "/");
        String continuationToken = null;
        int processed = 0;

        while (true) {
            var builder = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(normalizedPrefix)
                    .maxKeys(1000);
            if (continuationToken != null && !continuationToken.isBlank()) {
                builder.continuationToken(continuationToken);
            }
            ListObjectsV2Request req = builder.build();
            ListObjectsV2Response resp = s3Client.listObjectsV2(req);

            for (var obj : resp.contents()) {
                String key = obj.key();
                if (excludeKeys != null && excludeKeys.contains(key)) {
                    log.info("Skipping excluded key {}/{}", bucket, key);
                    continue;
                }
                try {
                    backup(bucket, key);
                    processed++;
                } catch (Exception e) {
                    log.error("Failed to reconcile object " + bucket + "/" + key + ": " + e.getMessage(), e);
                }
            }

            String next = resp.nextContinuationToken();
            if (next == null || next.isBlank()) {
                break;
            }
            continuationToken = next;
        }

        log.info("Reconciled {} objects under {}/{} (excluded {} keys)", processed, bucket, normalizedPrefix,
                (excludeKeys == null) ? 0 : excludeKeys.size());
    }

    private HeadObjectResponse headSource(String bucket, String key, String backupKey) {
        try {
            return readObjectMetadata(bucket, key, "source");
        } catch (S3Exception exception) {
            int status = exception.statusCode();
            try {
                log.error("Failed to read source object metadata for {}/{} (backupKey={}), status={}, requestId={}, message={}",
                        bucket, key, backupKey, status, exception.requestId(), exception.getMessage());
            } catch (Exception logEx) {
                log.error("Failed to read source object metadata and additionally failed to log details: {}", logEx.getMessage());
            }

            if (status == 404) {
                try {
                    if (backupExists(backupKey)) {
                        return null;
                    }
                } catch (S3Exception bex) {
                    throw new S3ReconciliationException("Failed to read source object metadata and failed to check backup existence for "
                            + backupKey + ": " + bex.getMessage(), bex);
                }
            }

            if (status == 403) {
                String guidance = "Access denied (403) reading source object " + bucket + "/" + key
                        + ". Possible causes: incorrect AWS credentials, missing IAM permissions (s3:GetObject, s3:HeadObject),"
                        + " bucket policy or ACL denying access, or cross-account restrictions. Suggested checks: run 'aws sts get-caller-identity'"
                        + " and 'aws s3api head-object' with the same credentials; verify IAM policies include s3:GetObject/s3:HeadObject/s3:PutObject/s3:DeleteObject"
                        + " for the source and backup buckets, and rotate compromised keys. RequestId=" + exception.requestId();
                throw new S3ReconciliationException(guidance, exception);
            }

            throw new S3ReconciliationException("Failed to read source object metadata for " + bucket + "/" + key
                    + " (backupKey=" + backupKey + ", status=" + status + ", requestId=" + exception.requestId() + ") : "
                    + exception.getMessage(), exception);
        }
    }

    private void copyToBackup(String bucket, String key, String backupKey) {
        s3Client.copyObject(CopyObjectRequest.builder()
                .sourceBucket(bucket)
                .sourceKey(key)
                .bucket(properties.backupBucket())
                .key(backupKey)
                .build());
    }

    private void verifyBackup(String bucket, String key, String backupKey, String expectedETag, Long expectedContentLength) {
        try {
            log.info("Waiting {}ms before verifying backup object {}/{}", VERIFY_BACKUP_DELAY_MILLIS,
                    properties.backupBucket(), backupKey);
            Thread.sleep(VERIFY_BACKUP_DELAY_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new S3ReconciliationException("Interrupted while waiting to verify backup object "
                    + properties.backupBucket() + "/" + backupKey, exception);
        }

        HeadObjectResponse backupHead = readObjectMetadata(properties.backupBucket(), backupKey,
                "verify backup for source " + bucket + "/" + key);

        String backupETag = backupHead.eTag();
        if (expectedETag != null && backupETag != null) {
            String normExpected = normalizeETag(expectedETag);
            String normBackup = normalizeETag(backupETag);
            if (!normExpected.equals(normBackup)) {
                throw new S3ReconciliationException("Backup ETag does not match source for " + bucket + "/" + key
                        + " (expected=" + normExpected + ", actual=" + normBackup + ")");
            }
            return;
        }

        // Fallback to size check if ETag is not available
        log.warn("Unable to verify backup by ETag for {}/{}; falling back to size comparison", bucket, key);
        if (expectedContentLength != null && backupHead.contentLength() != expectedContentLength) {
            throw new S3ReconciliationException("Backup size does not match source size for " + bucket + "/" + key);
        }
    }

    private static String normalizeETag(String eTag) {
        if (eTag == null) {
            return null;
        }
        return eTag.replace("\"", "").trim().toLowerCase(Locale.ROOT);
    }

    private void deleteSource(String bucket, String key) {
        log.info("Attempting to delete source object {}/{}", bucket, key);
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
            log.info("Deleted source object {}/{}", bucket, key);
        } catch (S3Exception exception) {
            int status = exception.statusCode();
            try {
                log.error("Failed to delete source object {}/{} , status={}, requestId={}, message={}",
                        bucket, key, status, exception.requestId(), exception.getMessage());
            } catch (Exception logEx) {
                log.error("Failed to delete source object and additionally failed to log details: {}", logEx.getMessage());
            }
            if (status == 403) {
                String guidance = "Access denied (403) deleting source object " + bucket + "/" + key
                        + ". Possible causes: missing s3:DeleteObject permission, bucket policy/ACL denying delete, or object lock/retention."
                        + " Verify IAM policies and bucket settings. RequestId=" + exception.requestId();
                throw new S3ReconciliationException(guidance, exception);
            }
            throw new S3ReconciliationException("Failed to delete source object " + bucket + "/" + key
                    + " (status=" + status + ", requestId=" + exception.requestId() + "): " + exception.getMessage(), exception);
        }
    }

    private boolean backupExists(String backupKey) {
        try {
            readObjectMetadata(properties.backupBucket(), backupKey, "check backup existence");
            return true;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return false;
            }
            throw exception;
        }
    }

    private HeadObjectResponse readObjectMetadata(String bucket, String key, String context) {
        try {
            log.info("Calling S3 GET (range=0-0) {} bucket={} key={}", context, bucket, key);
            GetObjectRequest getReq = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .range("bytes=0-0")
                    .build();
            ResponseBytes<GetObjectResponse> getBytes = s3Client.getObject(getReq, ResponseTransformer.toBytes());
            GetObjectResponse getResp = getBytes.response();

            long resolvedContentLength = -1L;
            String contentRange = getResp.contentRange();
            if (contentRange != null && contentRange.contains("/")) {
                String total = contentRange.substring(contentRange.lastIndexOf('/') + 1).trim();
                try {
                    resolvedContentLength = Long.parseLong(total);
                } catch (NumberFormatException ignored) {
                }
            }

            long metadataContentLength = resolvedContentLength >= 0 ? resolvedContentLength : getResp.contentLength();
            HeadObjectResponse pseudoHead = HeadObjectResponse.builder()
                    .contentLength(metadataContentLength)
                    .eTag(getResp.eTag())
                    .build();
            log.info("GET succeeded for {} bucket={} key={}: eTag={}, contentLength={}",
                    context, bucket, key, getResp.eTag(), metadataContentLength);
            return pseudoHead;
        } catch (S3Exception exception) {
            log.error("GET failed for {} bucket={} key={}: status={}, requestId={}, message={}",
                    context, bucket, key, exception.statusCode(), exception.requestId(), exception.getMessage());
            throw exception;
        }
    }

    private String backupKey(String bucket, String key) {
        String normalizedKey = key.replaceAll("^/+", "");
        // Store backups under <backup-bucket>/<source-bucket>/<original/path>
        // If the configured backup bucket equals the source bucket, avoid duplicating
        // the bucket name in the object key (results like bucket/bucket/...).
        String backupBucket = properties.backupBucket();
        if (backupBucket != null && backupBucket.equals(bucket)) {
            log.warn("Configured backup-bucket '{}' is the same as source bucket '{}'; using original object key for backup to avoid duplicate bucket prefix", backupBucket, bucket);
            return normalizedKey;
        }
        return bucket + "/" + normalizedKey;
    }
}
