package io.iterating.s3.consumer.messaging;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class S3ObjectEventTest {

    @Test
    void validatesRequiredBucketAndKey() {
        S3ObjectEvent event = new S3ObjectEvent("source-bucket", "path/object.txt", null, null, null);

        assertThatCode(event::validate).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingBucket() {
        S3ObjectEvent event = new S3ObjectEvent("", "path/object.txt", null, null, null);

        assertThatThrownBy(event::validate)
                .isInstanceOf(InvalidS3ObjectEventException.class)
                .hasMessageContaining("bucket");
    }

    @Test
    void rejectsMissingKey() {
        S3ObjectEvent event = new S3ObjectEvent("source-bucket", " ", null, null, null);

        assertThatThrownBy(event::validate)
                .isInstanceOf(InvalidS3ObjectEventException.class)
                .hasMessageContaining("key");
    }
}