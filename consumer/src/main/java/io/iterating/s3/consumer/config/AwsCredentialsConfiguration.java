package io.iterating.s3.consumer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

@Configuration
public class AwsCredentialsConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AwsCredentialsConfiguration.class);

    @Bean
    public AwsCredentialsProvider awsCredentialsProvider(AwsProperties props) {
        String accessKey = props != null ? props.getAccessKey() : null;
        String secretKey = props != null ? props.getSecretKey() : null;
        if (accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank()) {
            log.info("Using AWS credentials from configuration (aws.access-key present)");
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        }
        log.info("No static AWS credentials found in configuration; using DefaultCredentialsProvider chain");
        return DefaultCredentialsProvider.create();
    }
}
