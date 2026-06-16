# Consumer Module

This module is the application entry point for the S3 isolation consumer.
It does not contain NATS implementation details directly. Instead, it depends on the sibling `:nats` module for all JetStream client configuration and consumer wiring.

## Purpose

- Parse incoming NATS message payloads
- Validate and isolate S3 objects
- Enforce backup, verification, and deletion workflows

## Structure

- `config/ConsumerConfiguration.java` - application wiring and bean definitions
- `messaging/S3ObjectEventMessageHandler.java` - converts raw NATS payloads into `S3ObjectEvent` objects and delegates reconciliation
- `service/S3ReconciliationService.java` - S3 backup and deletion logic

## Build

```bash
./gradlew :consumer:build
```

## Run

The consumer uses Spring Boot and configuration from `consumer/src/main/resources/application.yml`.
Set required environment variables before running.
