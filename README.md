# S3 Iterating Consumer

Java 25 worker service that consumes NATS JetStream messages and reconciles S3 objects. For each valid message, the service copies matching source objects to a backup location, verifies the backup metadata, deletes the original object, and acknowledges the NATS message only after the workflow succeeds.

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
    "path": "public/draft/21",
    "objects": ["keep-this.png", "public/draft/21/leave-this-too.jpg"]
}
```

- `bucket` is required.
- `path` is optional. When present, it scopes the listing prefix.
- `objects` is optional and is treated as an exclusion list. Objects listed here are skipped; all other objects under `bucket/path` are backed up and deleted.
- Unknown JSON fields are ignored so the producer can evolve the schema gradually.

## Configuration

Runtime configuration is bound from environment variables through `consumer/src/main/resources/application.yml`.

| Environment variable    | Default                 | Description                                                                                                       |
| ----------------------- | ----------------------- | ----------------------------------------------------------------------------------------------------------------- |
| `NATS_SERVERS`          | `nats://localhost:4222` | Comma-separated NATS server URLs                                                                                  |
| `NATS_CREDENTIALS_PATH` | empty                   | Path to NATS `.creds` file or inline credentials content. Multiline values and escaped `\n` values are supported. |
| `NATS_STREAM`           | `s3-events`             | JetStream stream name                                                                                             |
| `NATS_DURABLE`          | `s3-isolator`           | Durable consumer name                                                                                             |
| `NATS_FILTER_SUBJECT`   | `s3.object.delete`      | Subject/filter to consume                                                                                         |
| `NATS_BATCH_SIZE`       | `10`                    | Pull batch size                                                                                                   |
| `S3_BACKUP_BUCKET`      | empty                   | Required backup bucket; backups are stored as `<backup-bucket>/<source-bucket>/<original/path>`                   |
| `AWS_REGION`            | `ap-northeast-2`        | AWS region                                                                                                        |
| `AWS_ENDPOINT_URL`      | empty                   | Optional endpoint override for LocalStack or S3-compatible services                                               |
| `AWS_ACCESS_KEY_ID`     | empty                   | Optional static access key used instead of the default AWS credentials chain                                      |
| `AWS_SECRET_ACCESS_KEY` | empty                   | Optional static secret key used instead of the default AWS credentials chain                                      |
| `AWS_SESSION_TOKEN`     | empty                   | Optional session token for temporary credentials                                                                  |

**Security note:** The `consumer/src/main/resources/application.yml` file previously contained example AWS credentials. These have been removed; do not commit real credentials. For local testing, set `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, and optionally `AWS_SESSION_TOKEN` in your environment.

### Local profile (development)

For local development, you can use a profile-specific configuration. Copy the example file and enable the `local` profile. Do not commit the copied file if it contains real credentials.
By default the application activates the `local` profile when `SPRING_PROFILES_ACTIVE` is not set. Override this with `SPRING_PROFILES_ACTIVE` in CI or production environments.

```bash
cp consumer/src/main/resources/application-local.yml.example \
    consumer/src/main/resources/application-local.yml

# Run with the local profile active
SPRING_PROFILES_ACTIVE=local ./gradlew :consumer:bootRun

# Or run tests with the local profile
SPRING_PROFILES_ACTIVE=local ./gradlew :consumer:test
```

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
NATS_CREDENTIALS_PATH="/path/to/user.creds" \
./gradlew :consumer:bootRun
```

## NATS Behavior

The worker uses a durable JetStream pull consumer. Successful messages are acknowledged after S3 copy, backup verification, and source deletion finish. Invalid payloads are terminated. Retryable processing errors are negatively acknowledged so JetStream can redeliver them according to the consumer policy.

For S3-compatible backends that do not reliably support `HeadObject`, the consumer falls back to a ranged `GetObject` request (`bytes=0-0`) to obtain ETag and content-length metadata for verification.

## Required S3 Permissions

The runtime identity needs these permissions for the relevant buckets and prefixes:

- `s3:GetObject`
- `s3:PutObject` or copy permission to the backup bucket
- `s3:DeleteObject`

`s3:HeadObject` is recommended when the backend supports it, but the consumer can fall back to `GetObject` metadata reads for S3-compatible backends that reject `HeadObject`.

For versioned buckets, include permissions that cover object versions.

## Recent Changes

- **Date:** 2026-06-28
- **Summary:** Collected recent commits and current working-tree modifications for documentation.

### Recent commits

- `07c3230` (2026-06-21) — test(nats): improve consumer testability and add local test helpers
- `7769d15` (2026-06-16) — chore(session): remove temporary session file after review
- `bd6b2ce` (2026-06-16) — chore(session): add session-20260616-204532.md (temporary)
- `5cd657e` (2026-06-16) — feat(nats,consumer): extract NATS into :nats module; rename S3 isolation -> reconciliation
- `f6e0903` (2026-06-16) — stage
- `70e64ad` (2026-05-30) — Initial commit

### Working-tree (uncommitted) changes

- Modified: `README.md` (this document)
- Modified: `consumer/build.gradle.kts` (dependency / build tweaks)
- Modified: `consumer/src/main/java/io/iterating/s3/consumer/config/ConsumerConfiguration.java` (imports/config wiring)
- Modified: `consumer/src/main/java/io/iterating/s3/consumer/config/S3Configuration.java`
- Modified: `consumer/src/main/java/io/iterating/s3/consumer/config/S3ReconciliationProperties.java` (new/updated properties binding)
- Modified: `consumer/src/main/java/io/iterating/s3/consumer/config/S3Configuration.java`
- Modified: `consumer/src/main/java/io/iterating/s3/consumer/messaging/S3ObjectEvent.java` (validation/keys helper)
- Modified: `consumer/src/main/java/io/iterating/s3/consumer/messaging/S3ObjectEventMessageHandler.java` (event handling/exclusion logic)
- Modified: `consumer/src/main/java/io/iterating/s3/consumer/service/S3ReconciliationService.java` (backup, verify, delete flow; GET fallback metadata; improved error guidance)
- Modified: `consumer/src/main/resources/application.yml` (configuration defaults and local AWS credentials for testing)
- Modified: `consumer/src/test/java/...` (tests updated: `S3ObjectEventTest`, `S3IsolationServiceTest`)
- Modified: `nats-check.sh`, `nats/build.gradle.kts`, and `nats/src/...` (NATS module updates and tests)
- Deleted: `local-run.sh`, `local-test.sh`
- Untracked: `.github/sessions/session-20260625-120000.md`, `.github/sessions/session-20260628-000000.md` (session files)
- Untracked (new): `consumer/src/main/java/io/iterating/s3/consumer/config/AwsCredentialsConfiguration.java`, `consumer/src/main/java/io/iterating/s3/consumer/config/AwsProperties.java`

### Notes / Next steps

- Review uncommitted changes and sensitive values before committing (the `application.yml` contains local AWS credential placeholders).
- Decide whether to commit new `AwsCredentialsConfiguration`/`AwsProperties` files and the session files under `.github/sessions`.
- After review, commit the working-tree changes with a concise commit message (e.g. `docs(readme): add recent changes summary`).

If you want, I can (a) expand any file-specific summaries with excerpts, (b) create a CHANGELOG.md with structured entries, or (c) produce a suggested commit message and stage the changes.
