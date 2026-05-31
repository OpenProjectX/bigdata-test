# bigdata-test

Composable Testcontainers fixtures for local big-data integration tests.

`bigdata-test` starts only the services a test asks for and exposes their
connection properties through a small Kotlin/JUnit API. Heavier setup such as
S3 JCEKS generation, bucket creation, and Kafka Avro seeding lives in the
optional `extensions` module so `core` and `junit5` stay lightweight.

## Modules

- `core`: container builder, service options, endpoints, and log routing
- `junit5`: `@BigDataTest` extension and parameter injection
- `extensions`: config-driven setup hooks for JCEKS, buckets, Kafka Avro, and Kerberos material
- `bigdata-test-spring-boot-autoconfigure`: Spring Boot auto-configuration
- `bigdata-test-spring-boot-starter`: Spring Boot starter
- `example:junit`: plain JUnit 5 examples
- `example:spring`: Spring Boot example
- `example:spark`: Spark, HMS, Kafka, S3, GCS, Iceberg, and Kerberos smoke tests

## Quick Start

Use `@BigDataTest` in a JUnit 5 test and request `BigDataTestKit` as a parameter:

```kotlin
@BigDataTest(
    hiveMetastore = true,
    kafka = true,
    schemaRegistry = true,
    localStackS3 = true,
)
class MyIntegrationTest {
    @Test
    fun test(kit: BigDataTestKit) {
        val metastoreUri = kit.endpoint(BigDataService.HIVE_METASTORE)
            .property("hive.metastore.uris")
        val bootstrapServers = kit.endpoint(BigDataService.KAFKA)
            .property("bootstrap.servers")
    }
}
```

For declarative test setup, add `@BigDataExtensions` with TOML config:

```kotlin
@BigDataExtensions("classpath:bigdata-extensions.toml")
@BigDataTest(hdfs = true, kafka = true, schemaRegistry = true, localStackS3 = true)
class MyIntegrationTest
```

```toml
[s3Jceks]
enabled = true
hdfsDir = "/bigdata-test/demo"
fileName = "s3.jceks"

[kafkaAvro]
enabled = true

[[kafkaAvro.topics]]
name = "events"
schema = "classpath:schemas/event.avsc"
records = [
  { key = "alpha", value = { id = 1, name = "alpha" } },
]
```

## Run Examples

Use the shared Gradle home when running this repository locally:

```bash
GRADLE_USER_HOME=/data/.gradle ./gradlew check
```

Run the Spark smoke test:

```bash
GRADLE_USER_HOME=/data/.gradle ./gradlew :example:spark:test
```

Run the Spark HMS/Kerberos matrix:

```bash
GRADLE_USER_HOME=/data/.gradle ./gradlew :example:spark:sparkBigDataMatrixTest
```

Individual matrix cells are also available:

```bash
GRADLE_USER_HOME=/data/.gradle ./gradlew :example:spark:sparkApacheHmsTest
GRADLE_USER_HOME=/data/.gradle ./gradlew :example:spark:sparkApacheHmsKerberosTest
GRADLE_USER_HOME=/data/.gradle ./gradlew :example:spark:sparkClouderaHmsTest
GRADLE_USER_HOME=/data/.gradle ./gradlew :example:spark:sparkClouderaHmsKerberosTest
```

## Documentation

- Detailed usage: [doc/user-guide.adoc](doc/user-guide.adoc)
- Contributing: [CONTRIBUTING.md](CONTRIBUTING.md)
- Current Hive Docker HMS notes: [doc/hive-docker-hms-issues.adoc](doc/hive-docker-hms-issues.adoc)
