package io.iterating.s3.nats.messaging;

public interface MessageHandler {

    void handle(byte[] payload, String subject) throws InvalidMessageException, Exception;
}
