package io.iterating.s3.consumer.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.iterating.s3.consumer.service.S3IsolationResult;
import io.iterating.s3.consumer.service.S3IsolationService;
import io.iterating.s3.nats.messaging.InvalidMessageException;
import io.iterating.s3.nats.messaging.MessageHandler;

public class S3ObjectEventMessageHandler implements MessageHandler {

    private static final Logger log = LoggerFactory.getLogger(S3ObjectEventMessageHandler.class);

    private final ObjectMapper objectMapper;
    private final S3IsolationService isolationService;

    public S3ObjectEventMessageHandler(ObjectMapper objectMapper, S3IsolationService isolationService) {
        this.objectMapper = objectMapper;
        this.isolationService = isolationService;
    }

    @Override
    public void handle(byte[] payload, String subject) throws InvalidMessageException {
        try {
            S3ObjectEvent event = objectMapper.readValue(payload, S3ObjectEvent.class);
            S3IsolationResult result = isolationService.isolate(event);
            log.info("S3 object isolated subject={} sourceBucket={} sourceKey={} backupBucket={} backupKey={}",
                    subject, result.sourceBucket(), result.sourceKey(), result.backupBucket(), result.backupKey());
        } catch (JsonProcessingException | InvalidS3ObjectEventException exception) {
            throw new InvalidMessageException("Invalid S3 object event", exception);
        } catch (java.io.IOException exception) {
            throw new InvalidMessageException("Failed to parse S3 object event", exception);
        }
    }
}
