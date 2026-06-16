package io.iterating.s3.nats.messaging;

public class InvalidMessageException extends Exception {

    public InvalidMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
