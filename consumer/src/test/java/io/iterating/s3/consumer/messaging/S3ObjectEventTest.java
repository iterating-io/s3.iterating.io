package io.iterating.s3.consumer.messaging;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class S3ObjectEventTest {

    @Test
    void validatesRequiredFields() {
        S3ObjectEvent event = new S3ObjectEvent("source-bucket", "public/draft/21/", List.of("file.png"));

        assertThatCode(event::validate).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingBucket() {
        S3ObjectEvent event = new S3ObjectEvent("", "public/draft/21/", List.of("file.png"));

        assertThatThrownBy(event::validate)
                .isInstanceOf(InvalidS3ObjectEventException.class)
                .hasMessageContaining("bucket");
    }

    @Test
    void allowsMissingPath() {
        S3ObjectEvent event = new S3ObjectEvent("source-bucket", " ", List.of("file.png"));

        assertThatCode(event::validate).doesNotThrowAnyException();
    }

    @Test
    void allowsEmptyObjects() {
        S3ObjectEvent event = new S3ObjectEvent("source-bucket", "public/draft/21/", List.of());

        assertThatCode(event::validate).doesNotThrowAnyException();
    }

    @Test
    void keysJoinsPathAndObjects() {
        S3ObjectEvent event = new S3ObjectEvent("source-bucket", "public/draft/21/", List.of("a.png", "b.png"));

        assertThat(event.keys()).containsExactly("public/draft/21/a.png", "public/draft/21/b.png");
    }

    @Test
    void keysAddsTrailingSlashToPathIfMissing() {
        S3ObjectEvent event = new S3ObjectEvent("source-bucket", "public/draft/21", List.of("a.png"));

        assertThat(event.keys()).containsExactly("public/draft/21/a.png");
    }
}
