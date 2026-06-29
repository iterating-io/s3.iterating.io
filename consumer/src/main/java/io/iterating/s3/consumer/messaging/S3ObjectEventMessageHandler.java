package io.iterating.s3.consumer.messaging;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.iterating.s3.consumer.service.S3ReconciliationException;
import io.iterating.s3.consumer.service.S3ReconciliationService;
import io.iterating.s3.nats.messaging.InvalidMessageException;
import io.iterating.s3.nats.messaging.MessageHandler;
import software.amazon.awssdk.services.s3.model.S3Exception;

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
            event.validate();

            // Interpret `objects` as an exclusion list: items present in `objects`
            // must NOT be touched. Reconcile (backup+delete) all objects under the
            // given prefix except the ones explicitly listed in `objects`. Normalize
            // each provided object entry into a full S3 key within the prefix so
            // clients may send either bare filenames or full keys.
            List<String> objects = event.objects();
            String normalizedPrefix = (event.path() == null || event.path().isBlank()) ? "" : (event.path().endsWith("/") ? event.path() : event.path() + "/");
            List<String> excludeKeysList;
            if (objects == null || objects.isEmpty()) {
                excludeKeysList = List.of();
            } else {
                var tmp = new ArrayList<String>(objects.size());
                for (String o : objects) {
                    if (o == null || o.isBlank()) {
                        continue;
                    }
                    String cleaned = o.replaceAll("^/+", "");
                    if (!normalizedPrefix.isEmpty() && cleaned.startsWith(normalizedPrefix)) {
                        tmp.add(cleaned);
                    } else {
                        tmp.add(normalizedPrefix + cleaned);
                    }
                }
                excludeKeysList = List.copyOf(tmp);
            }
            Set<String> excludeKeys = new HashSet<>(excludeKeysList);

            log.info("S3 prefix reconcile requested subject={} sourceBucket={} prefix={} excludeCount={}",
                    subject, event.bucket(), event.path(), excludeKeys.size());
            if (!excludeKeys.isEmpty()) {
                log.info("Excluding keys from reconcile subject={} excludes={}", subject, excludeKeysList);
            }

            reconciliationService.reconcile(event.bucket(), event.path(), excludeKeys);
            log.info("S3 prefix reconciled subject={} sourceBucket={} prefix={} excluded={}",
                    subject, event.bucket(), event.path(), excludeKeys.size());
        } catch (JsonProcessingException | InvalidS3ObjectEventException exception) {
            throw new InvalidMessageException("Invalid S3 object event", exception);
        } catch (java.io.IOException exception) {
            throw new InvalidMessageException("Failed to parse S3 object event", exception);
        } catch (S3ReconciliationException exception) {
            // If the reconciliation failed due to an S3 403 (access denied), treat it as a permanent
            // processing error and terminate the message instead of scheduling retries.
            Throwable cause = exception.getCause();
            while (cause != null) {
                if (cause instanceof S3Exception) {
                    S3Exception s3ex = (S3Exception) cause;
                    if (s3ex.statusCode() == 403) {
                        throw new InvalidMessageException("Access denied while reconciling S3 object", exception);
                    }
                }
                cause = cause.getCause();
            }
            // Otherwise propagate runtime exception to trigger retry behavior
            throw exception;
        }
    }
}
