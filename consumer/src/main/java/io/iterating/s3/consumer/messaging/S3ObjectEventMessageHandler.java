package io.iterating.s3.consumer.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.iterating.s3.consumer.service.S3ReconciliationResult;
import io.iterating.s3.consumer.service.S3ReconciliationService;
import io.iterating.s3.nats.messaging.InvalidMessageException;
import io.iterating.s3.nats.messaging.MessageHandler;

public class S3ObjectEventMessageHandler implements MessageHandler {

    private static final Logger log = LoggerFactory.getLogger(S3ObjectEventMessageHandler.class);

    private final ObjectMapper objectMapper;
    private final S3ReconciliationService reconciliationService;

    public S3ObjectEventMessageHandler(ObjectMapper objectMapper, S3ReconciliationService reconciliationService) {
        this.objectMapper = objectMapper;
        this.reconciliationService = reconciliationService;
    }

    @Override
    public void handle(byte[] payload, String subject) throws InvalidMessageException {
        try {
            S3ObjectEvent event = objectMapper.readValue(payload, S3ObjectEvent.class);
            S3ReconciliationResult result = reconciliationService.reconcile(event);
            log.info("S3 object reconciled subject={} sourceBucket={} sourceKey={} backupBucket={} backupKey={}",
                    subject, result.sourceBucket(), result.sourceKey(), result.backupBucket(), result.backupKey());
        } catch (JsonProcessingException | InvalidS3ObjectEventException exception) {
            throw new InvalidMessageException("Invalid S3 object event", exception);
        } catch (java.io.IOException exception) {
            throw new InvalidMessageException("Failed to parse S3 object event", exception);
        }
    }
}
