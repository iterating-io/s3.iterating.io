package io.iterating.s3.consumer.messaging;

public class InvalidS3ObjectEventException extends RuntimeException {

    public InvalidS3ObjectEventException(String message) {
        super(message);
    }
}