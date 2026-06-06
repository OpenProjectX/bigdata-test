# Contributing

## Development Setup

This project is a Gradle multi-project build. Use Java 17 for the main build and keep Gradle's user home stable when running commands locally:

```bash
GRADLE_USER_HOME=/data/.gradle ./gradlew check
```

Docker is required for integration tests that start Testcontainers services. Some example tasks start multiple large containers, so prefer running focused tasks while developing.

## Project Boundaries

Keep the module split intentional:

- `core` owns service options, container construction, endpoint metadata, startup ordering, and log routing.
- `junit5` owns annotation parsing, config loading for startup-time options, lifecycle integration, and parameter resolution.
- `extensions` owns heavier setup that depends on Hadoop, Kafka clients, Avro, object-store clients, or other integration libraries.
- Spring Boot modules should adapt the core API to Spring configuration. They should not duplicate container logic.
- Example modules should demonstrate supported user workflows and catch real integration regressions.

Do not add Hadoop, Spark, Kafka client, or storage SDK dependencies to `core` or `junit5` unless the module boundary is intentionally changed and documented.

## Dependency Management

The root build imports the Testcontainers BOM once for all subprojects:

```text
org.testcontainers:testcontainers-bom:2.0.4
```

Subprojects should declare Testcontainers modules without repeating the BOM. External users should import the BOM in their own build if they also depend on Testcontainers directly.

When adding dependencies, keep them in the narrowest module that needs them. For example, Hadoop credential provider helpers belong in `extensions`, not `core`.

Heavy client dependencies in `extensions`, such as Hadoop, Kafka, Avro, Schema Registry, and serialization libraries, should stay as `implementation` unless their types intentionally become part of the public API. This avoids pushing those libraries onto a user's compile classpath. Do not switch them to `api` just because an internal extension implementation imports them.

## Configuration Model

Startup-time service configuration belongs to `@BigDataTest` and its TOML files. Examples include:

- enabled services
- service images
- fixed ports
- Kerberos service switches
- container log routing

Test-data and setup automation belongs to `@BigDataExtensions`. Examples include:

- creating buckets
- creating JCEKS files
- producing Kafka Avro records
- publishing generated Kerberos material for tests

Launcher-driven config should use the existing system-property hooks:

```text
bigdata.test.config
bigdata.test.config.replace
bigdata.extensions.config
bigdata.extensions.config.replace
```

Use replacement mode for matrix tasks that need to turn services on and off. Service booleans are additive when configs are appended.

## Extension Design

Prefer config-driven extension behavior when the user should only declare what they want. A good extension should:

- validate required services before doing work
- run at the narrowest lifecycle event that makes sense
- publish useful output through `BigDataExtensionResult`
- accept generated names and dynamic values through the programmatic builder
- keep service-specific client code inside `extensions`

For built-in extension types, update:

- config model and loader
- builder API
- result output keys
- user guide
- an example or focused test

For external extension modules, implement `BigDataExtensionProvider` and register it with `ServiceLoader`.

## Kerberos Changes

Kerberos is intentionally opt-in per service. `kerberos = true` starts the shared KDC, then service-specific switches decide which services use Kerberos.

Schema Registry should stay out of the Kerberos service switch model unless its own authentication is explicitly implemented. It can still run with Kafka Kerberos by using Kafka's internal plaintext listener.

Host-side Kafka Kerberos clients need a stable advertised host port. Existing Kerberos matrix tasks use fixed Kafka port `19092` for this reason.

## HMS Implementations

The project supports two Hive Metastore implementations:

- `hiveMetastore = true`: open-source Hive HMS image plus external PostgreSQL.
- `clouderaHms = true`: Cloudera HMS image with embedded PostgreSQL.

Keep their endpoint contract the same through `BigDataService.HIVE_METASTORE`. A test must enable only one HMS implementation.

The Spark example defaults to the Hive 3 open-source HMS image because Spark 3.x uses an older Hive metastore client. Keep Hive 4 compatibility notes in `doc/hive-docker-hms-issues.adoc`.

## Documentation Expectations

Update docs with every user-visible feature, breaking change, task, config key, image key, endpoint property, or behavior change.

Use this split:

- `README.md`: quick start, key commands, and links.
- `doc/user-guide.adoc`: detailed usage, configuration, examples, and troubleshooting.
- `CONTRIBUTING.md`: development workflow and project conventions.
- focused `doc/*.adoc` notes: current known issues or design-specific references.

## Testing

Run focused compile checks for ordinary API edits:

```bash
GRADLE_USER_HOME=/data/.gradle ./gradlew :core:compileKotlin :junit5:compileKotlin :extensions:compileKotlin
```

Run the Spark example when touching Spark, HMS, Kafka, object-store, extension, or Kerberos behavior:

```bash
GRADLE_USER_HOME=/data/.gradle ./gradlew :example:spark:test
```

Run the full Spark matrix when touching runtime config composition, HMS selection, or Kerberos behavior:

```bash
GRADLE_USER_HOME=/data/.gradle ./gradlew :example:spark:sparkBigDataMatrixTest
```

The Spark matrix has two kinds of axes. Container/service axes belong in TOML and can be selected with `bigdata.test.config`. Dependency axes belong in Gradle tasks because they require different resolved test JVM classpaths. Add a new dependency line by creating a resolvable runtime classpath in `example/spark/build.gradle.kts`, then pair it with the existing TOML service variants.

The Gradle configuration-cache warning from `net.researchgate.release` is currently expected and unrelated to test behavior.

## Release Publishing

The release workflow publishes library modules to Maven Central through Sonatype and to GitHub Packages. GitHub Packages uses the repository Maven endpoint:

```text
https://maven.pkg.github.com/OpenProjectX/bigdata-test
```

The workflow needs `packages: write` permission and passes `GITHUB_PACKAGES_USERNAME` plus `GITHUB_PACKAGES_TOKEN` to Gradle. Local dry runs can validate task selection without uploading:

```bash
GRADLE_USER_HOME=/data/.gradle ./gradlew publishAllPublicationsToGitHubPackagesRepository --dry-run
```

Push-triggered release workflow runs can be skipped with common commit-message markers:

```text
[skip ci]
[ci skip]
[no ci]
[skip actions]
[actions skip]
skip-checks:true
```

Manual `workflow_dispatch` releases ignore these markers and still run.

## Troubleshooting During Development

Use file logs when investigating container startup or service-side behavior:

```kotlin
@BigDataTest(
    kafka = true,
    containerLogMode = ContainerLogMode.FILE,
    containerLogDirectory = "build/container-logs",
)
class DebugTest
```

For port conflicts, check local listeners before switching tests to fixed ports. Dynamic ports are the default and should remain the default for general JUnit usage.
