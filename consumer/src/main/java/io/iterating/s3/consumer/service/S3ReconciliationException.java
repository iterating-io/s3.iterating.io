package io.iterating.s3.consumer.service;

public class S3ReconciliationException extends RuntimeException {

    public S3ReconciliationException(String message) {
        super(message);
    }

    public S3ReconciliationException(String message, Throwable cause) {
        super(message, cause);
    }
}