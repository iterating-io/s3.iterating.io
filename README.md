# S3 Iterating Consumer

Java 25 worker service that consumes NATS JetStream messages and isolates S3 objects. For each valid message, the service copies the referenced object to a backup location, verifies the backup metadata, deletes the original object, and acknowledges the NATS message only after the workflow succeeds.

## Stack

- Java 25
- Gradle Kotlin DSL
- Spring Boot worker runtime
- NATS Java client
- AWS SDK for Java v2 S3
- JUnit 5, Mockito, Testcontainers

## Message Contract

The consumer expects JSON payloads like this:

```json
{
    "bucket": "source-bucket",
    "key": "path/object.txt",
    "versionId": "optional-version-id",
    "eventId": "optional-event-id",
    "metadata": {
        "reason": "isolation-request"
    }
}
```

`bucket` and `key` are required. Unknown JSON fields are ignored so the producer can evolve the schema gradually.

## Configuration

Runtime configuration is bound from environment variables through `consumer/src/main/resources/application.yml`.

| Environment variable        | Default                 | Description                                                                                   |
| --------------------------- | ----------------------- | --------------------------------------------------------------------------------------------- |
| `NATS_SERVERS`              | `nats://localhost:4222` | Comma-separated NATS server URLs                                                              |
| `NATS_CREDENTIALS`          | empty                   | Required NATS `.creds` file contents. Multiline values and escaped `\n` values are supported. |
| `NATS_STREAM`               | `s3-events`             | JetStream stream name                                                                         |
| `NATS_DURABLE`              | `s3-isolator`           | Durable consumer name                                                                         |
| `NATS_FILTER_SUBJECT`       | `s3.object.delete`      | Subject/filter to consume                                                                     |
| `NATS_BATCH_SIZE`           | `10`                    | Pull batch size                                                                               |
| `S3_BACKUP_BUCKET`          | empty                   | Required backup bucket                                                                        |
| `S3_BACKUP_PREFIX`          | `isolated`              | Backup object prefix                                                                          |
| `S3_ALLOWED_SOURCE_BUCKETS` | empty                   | Optional comma-separated source bucket allowlist                                              |
| `AWS_REGION`                | `ap-northeast-2`        | AWS region                                                                                    |
| `AWS_ENDPOINT_URL`          | empty                   | Optional endpoint override for LocalStack or S3-compatible services                           |

## Development

Run tests:

```bash
./gradlew :consumer:test
```

Build the application:

```bash
./gradlew :consumer:build
```

Run locally:

```bash
S3_BACKUP_BUCKET=my-backup-bucket \
NATS_SERVERS=nats://localhost:4222 \
NATS_CREDENTIALS="$(cat /path/to/user.creds)" \
./gradlew :consumer:bootRun
```

## NATS Behavior

The worker uses a durable JetStream pull consumer. Successful messages are acknowledged after S3 copy, backup verification, and source deletion finish. Invalid payloads are terminated. Retryable processing errors are negatively acknowledged so JetStream can redeliver them according to the consumer policy.

## Required S3 Permissions

The runtime identity needs these permissions for the relevant buckets and prefixes:

- `s3:GetObject`
- `s3:HeadObject`
- `s3:PutObject` or copy permission to the backup bucket
- `s3:DeleteObject`

For versioned buckets, include permissions that cover object versions.
