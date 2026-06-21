package io.iterating.s3.nats.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;

@Configuration
public class NatsConfiguration {

    @Bean(destroyMethod = "close")
    Connection natsConnection(NatsProperties properties) throws IOException, InterruptedException {
        Options.Builder builder = new Options.Builder()
                .servers(properties.serverList().toArray(String[]::new))
                .connectionName(properties.connectionName())
                .maxReconnects(-1)
                .authHandler(Nats.staticCredentials(normalizedCredentials(properties).getBytes(StandardCharsets.UTF_8)));

        return Nats.connectReconnectOnConnect(builder.build());
    }

    private static String normalizedCredentials(NatsProperties properties) {
        return properties.credentials().replace("\\n", "\n");
    }
}
