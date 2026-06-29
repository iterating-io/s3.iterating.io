package io.iterating.s3.nats.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;

@Configuration
public class NatsConfiguration {

    @Bean(destroyMethod = "close")
    Connection natsConnection(NatsProperties properties) throws IOException, InterruptedException {
        String[] servers = properties.serverList().toArray(String[]::new);
        Options.Builder builder = new Options.Builder()
                .servers(servers)
                .connectionName(properties.connectionName())
                .maxReconnects(-1);

        String creds = normalizedCredentials(properties);
        boolean authConfigured = creds != null && !creds.isBlank();
        if (authConfigured) {
            builder.authHandler(Nats.staticCredentials(creds.getBytes(StandardCharsets.UTF_8)));
        }

        log.info("NATS connection: servers='{}', connectionName='{}', authConfigured={}",
                String.join(",", properties.serverList()), properties.connectionName(), authConfigured);

        return Nats.connectReconnectOnConnect(builder.build());
    }

    private static final Logger log = LoggerFactory.getLogger(NatsConfiguration.class);

    private static String normalizedCredentials(NatsProperties properties) throws IOException {
        String raw = properties.credentialsPath();
        if (raw == null || raw.isBlank()) {
            log.debug("No NATS credentials provided (credentialsPath is blank)");
            return "";
        }
        // Interpret the provided value as a path first (support relative paths).
        Path p = Path.of(raw);
        Path abs = p.toAbsolutePath().normalize();
        boolean existsAtGiven = Files.exists(p);
        boolean existsAtAbs = Files.exists(abs);
        log.debug("NATS credentials path raw='{}', resolved='{}', existsAtGiven={}, existsAtResolved={}",
                raw, abs.toString(), existsAtGiven, existsAtAbs);

        // Prefer the resolved absolute path if it exists; otherwise fall back to inline handling
        if (existsAtAbs) {
            try {
                String content = Files.readString(abs, StandardCharsets.UTF_8).replace("\r\n", "\n");
                int len = content.length();
                String filename = abs.getFileName() != null ? abs.getFileName().toString() : abs.toString();
                log.info("NATS credentials loaded from file '{}' ({} bytes)", filename, len);
                return content;
            } catch (IOException e) {
                log.warn("Failed to read NATS credentials file '{}' ({}). Will treat as inline. Error: {}",
                        abs.toString(), e.getClass().getSimpleName(), e.getMessage());
                throw e;
            }
        }
        // Additional heuristic: if the configured path accidentally repeats the current
        // module name (e.g. running with cwd=/.../consumer and configured "consumer/.creds"),
        // try stripping the leading cwd segment and resolve the file there.
        try {
            Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
            String cwdName = cwd.getFileName() != null ? cwd.getFileName().toString() : "";
            if (!cwdName.isBlank() && raw.startsWith(cwdName + "/")) {
                Path alt = cwd.resolve(raw.substring(cwdName.length() + 1)).normalize();
                if (Files.exists(alt)) {
                    String content = Files.readString(alt, StandardCharsets.UTF_8).replace("\r\n", "\n");
                    int len = content.length();
                    log.info("NATS credentials loaded from adjusted path '{}' ({} bytes)", alt.toString(), len);
                    return content;
                } else {
                    log.debug("Adjusted credentials path '{}' not found", alt.toString());
                }
            }
        } catch (Exception e) {
            log.debug("Error while attempting adjusted credentials resolution: {}", e.getMessage());
        }
        // otherwise treat the value as credentials content (allow escaped newlines)
        String inline = raw.replace("\\n", "\n");
        log.info("NATS credentials provided inline ({} bytes)", inline.length());
        return inline;
    }
}
