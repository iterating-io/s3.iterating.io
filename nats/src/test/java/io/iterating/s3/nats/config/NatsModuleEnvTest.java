package io.iterating.s3.nats.config;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import io.nats.client.Connection;

public class NatsModuleEnvTest {

    @Test
    void connectsUsingEnvironment() throws Exception {
        String servers = System.getenv("NATS_SERVERS");
        String credentials = System.getenv("NATS_CREDENTIALS");
        String connectionName = System.getenv("NATS_CONNECTION_NAME");

        // Require servers and credentials to be present; otherwise skip this integration test
        Assumptions.assumeTrue(servers != null && !servers.isBlank(), "NATS_SERVERS not set");
        Assumptions.assumeTrue(credentials != null && !credentials.isBlank(), "NATS_CREDENTIALS not set");

        NatsProperties props = new NatsProperties(
                servers,
                credentials,
                connectionName != null && !connectionName.isBlank() ? connectionName : "test-connection",
                "test-stream",
                "test-durable",
                "test-subject",
                1,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1));

        try (Connection nc = new NatsConfiguration().natsConnection(props)) {
            assertNotNull(nc);
            assertEquals(io.nats.client.Connection.Status.CONNECTED, nc.getStatus());
        }
    }
}
