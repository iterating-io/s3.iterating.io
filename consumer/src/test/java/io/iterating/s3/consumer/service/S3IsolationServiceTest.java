package io.iterating.s3.consumer.service;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InOrder;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.iterating.s3.consumer.config.S3ReconciliationProperties;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

class S3ReconciliationServiceTest {

    private final S3Client s3Client = org.mockito.Mockito.mock(S3Client.class);
    private final S3ReconciliationService service = new S3ReconciliationService(
            s3Client,
            new S3ReconciliationProperties("backup-bucket", "ap-northeast-2", null));

    @Test
    void copiesVerifiesAndDeletesSourceObject() {
        GetObjectResponse getResp = GetObjectResponse.builder().contentLength(12L).build();
        when(s3Client.getObject(any(GetObjectRequest.class), any(ResponseTransformer.class)))
                .thenReturn(ResponseBytes.fromByteArray(getResp, new byte[0]))
                .thenReturn(ResponseBytes.fromByteArray(getResp, new byte[0]));
        when(s3Client.copyObject(any(CopyObjectRequest.class))).thenReturn(CopyObjectResponse.builder().build());
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenReturn(DeleteObjectResponse.builder().build());

        S3ReconciliationResult result = service.backup("source-bucket", "path/object.txt");

        assertThat(result.sourceDeleted()).isTrue();
        assertThat(result.backupBucket()).isEqualTo("backup-bucket");
        assertThat(result.backupKey()).startsWith("source-bucket/").endsWith("/object.txt");

        InOrder inOrder = inOrder(s3Client);
        inOrder.verify(s3Client).getObject(any(GetObjectRequest.class), any(ResponseTransformer.class));
        inOrder.verify(s3Client).copyObject(any(CopyObjectRequest.class));
        inOrder.verify(s3Client).getObject(any(GetObjectRequest.class), any(ResponseTransformer.class));
        inOrder.verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void treatsMissingSourceAsSuccessWhenBackupAlreadyExists() {
        NoSuchKeyException notFound = NoSuchKeyException.builder().statusCode(404).message("not found").build();
        GetObjectResponse getResp2 = GetObjectResponse.builder().contentLength(12L).build();
        when(s3Client.getObject(any(GetObjectRequest.class), any(ResponseTransformer.class)))
                .thenThrow(notFound)
                .thenReturn(ResponseBytes.fromByteArray(getResp2, new byte[0]));

        S3ReconciliationResult result = service.backup("source-bucket", "path/object.txt");

        assertThat(result.sourceDeleted()).isFalse();
        assertThat(result.backupKey()).startsWith("source-bucket/");

        verify(s3Client, never()).copyObject(any(CopyObjectRequest.class));
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }
}
