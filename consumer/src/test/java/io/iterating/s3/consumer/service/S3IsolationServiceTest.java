package io.iterating.s3.consumer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.iterating.s3.consumer.config.S3IsolationProperties;
import io.iterating.s3.consumer.messaging.S3ObjectEvent;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

class S3IsolationServiceTest {

    private final S3Client s3Client = org.mockito.Mockito.mock(S3Client.class);
    private final S3IsolationService service = new S3IsolationService(
            s3Client,
            new S3IsolationProperties("backup-bucket", "isolated", List.of("source-bucket"), "ap-northeast-2", null));

    @Test
    void copiesVerifiesAndDeletesSourceObject() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(12L).build())
                .thenReturn(HeadObjectResponse.builder().contentLength(12L).build());
        when(s3Client.copyObject(any(CopyObjectRequest.class))).thenReturn(CopyObjectResponse.builder().build());
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenReturn(DeleteObjectResponse.builder().build());

        S3IsolationResult result = service.isolate(new S3ObjectEvent("source-bucket", "path/object.txt", null, "event-1", null));

        assertThat(result.sourceDeleted()).isTrue();
        assertThat(result.backupBucket()).isEqualTo("backup-bucket");
        assertThat(result.backupKey()).startsWith("isolated/source-bucket/").endsWith("/object.txt");

        InOrder inOrder = inOrder(s3Client);
        inOrder.verify(s3Client).headObject(any(HeadObjectRequest.class));
        inOrder.verify(s3Client).copyObject(any(CopyObjectRequest.class));
        inOrder.verify(s3Client).headObject(any(HeadObjectRequest.class));
        inOrder.verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void treatsMissingSourceAsSuccessWhenBackupAlreadyExists() {
        NoSuchKeyException notFound = NoSuchKeyException.builder().statusCode(404).message("not found").build();
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(notFound)
                .thenReturn(HeadObjectResponse.builder().contentLength(12L).build());

        S3IsolationResult result = service.isolate(new S3ObjectEvent("source-bucket", "path/object.txt", null, "event-1", null));

        assertThat(result.sourceDeleted()).isFalse();
        assertThat(result.backupKey()).startsWith("isolated/source-bucket/");

        verify(s3Client, never()).copyObject(any(CopyObjectRequest.class));
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }
}