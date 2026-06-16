package io.iterating.s3.consumer.service;

public class S3IsolationException extends RuntimeException {

    public S3IsolationException(String message) {
        super(message);
    }

    public S3IsolationException(String message, Throwable cause) {
        super(message, cause);
    }
}