package io.iterating.s3.consumer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityRequest;

@Configuration
public class S3Configuration {

    private static final Logger log = LoggerFactory.getLogger(S3Configuration.class);

    @Bean
    S3Client s3Client(S3ReconciliationProperties properties, AwsCredentialsProvider credentialsProvider) {
        var builder = S3Client.builder().region(Region.of(properties.region()));

        // If an endpoint override is configured (for local S3/MinIO or custom endpoints),
        // enable path-style access so the client does not try to resolve bucket-specific DNS names
        // like <bucket>.<endpoint-host> which often do not exist for private endpoints.
        properties.endpointOverrideUri().ifPresent(uri -> {
            builder.endpointOverride(uri);
            var svcCfg = software.amazon.awssdk.services.s3.S3Configuration.builder()
                    .pathStyleAccessEnabled(true)
                    .build();
            builder.serviceConfiguration(svcCfg);
        });

        builder.credentialsProvider(credentialsProvider);

        S3Client s3 = builder.build();

        // Log which credentials provider is in use and a masked access key id (no secrets printed)
        try {
            AwsCredentials creds = credentialsProvider.resolveCredentials();
            String accessKeyId = (creds == null) ? null : creds.accessKeyId();
            String masked = maskAccessKeyId(accessKeyId);
            boolean hasSession = (creds instanceof AwsSessionCredentials);
            log.info("AWS credentials provider={}, accessKeyIdMasked={}, sessionCredentials={}",
                    credentialsProvider.getClass().getName(), masked, hasSession);
        } catch (Exception e) {
            log.warn("Unable to resolve AWS credentials from provider {}: {}",
                    credentialsProvider.getClass().getName(), e.getMessage());
        }

        // Try to log the AWS caller identity using the provided credentials provider
        try (StsClient sts = StsClient.builder().region(Region.of(properties.region())).credentialsProvider(credentialsProvider).build()) {
            var resp = sts.getCallerIdentity(GetCallerIdentityRequest.builder().build());
            log.info("AWS caller identity: account={}, arn={}", resp.account(), resp.arn());
        } catch (Exception e) {
            log.warn("Unable to determine AWS caller identity: {}", e.getMessage());
        }

        return s3;
    }

    private static String maskAccessKeyId(String key) {
        if (key == null || key.isBlank()) {
            return "<none>";
        }
        int len = key.length();
        if (len <= 8) {
            return "****";
        }
        return key.substring(0, 4) + "..." + key.substring(len - 4);
    }
}
