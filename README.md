# bigdata-test

Composable Testcontainers-based fixtures for local big-data integration tests.

## Modules

- `core`: container builder and endpoint/property model
- `junit5`: `@BigDataTest` extension for JUnit 5 tests
- `bigdata-test-spring-boot-autoconfigure`: Spring Boot auto-configuration
- `bigdata-test-spring-boot-starter`: starter that brings in the auto-configuration
- `example:spring`: Spring Boot local-development example
- `example:junit`: JUnit 5 integration-test examples
- `example:spark`: Spark + JUnit 5 example that wires Spark to the container endpoints

## Core Usage

```kotlin
val kit = BigDataTestKit.builder()
    .withHiveMetastore()
    .withKafka(KafkaOptions(enabled = true, schemaRegistryEnabled = true, kafkaUiEnabled = true))
    .withLocalStackS3()
    .build()

kit.use {
    it.start()
    val metastoreUri = it.endpoint(BigDataService.HIVE_METASTORE).property("hive.metastore.uris")
    val bootstrapServers = it.endpoint(BigDataService.KAFKA).property("bootstrap.servers")
}
```

Container logs are disabled by default. Enable them when a container fails to start or you need service-side troubleshooting output:

```kotlin
val kit = BigDataTestKit.builder()
    .withKafka()
    .withContainerLogsToStdout()
    .build()
```

or write one file per service:

```kotlin
val kit = BigDataTestKit.builder()
    .withHiveMetastore()
    .withContainerLogsToDirectory("build/container-logs")
    .build()
```

## JUnit 5

```kotlin
@BigDataTest(hiveMetastore = true, kafka = true, localStackS3 = true)
class MyIntegrationTest {
    @Test
    fun test(kit: BigDataTestKit) {
        val properties = kit.springProperties()
    }
}
```

Kerberos can be enabled per service. Hadoop/HDFS, Hive Metastore, Kafka, Schema Registry, and Kafka UI expose dedicated Kerberos switches; set `kerberos = true` to start the shared KDC and then enable auth for the services that should use it.

```kotlin
@BigDataTest(
    kerberos = true,
    hdfs = true,
    hdfsKerberos = true,
    hiveMetastore = true,
    hiveMetastoreKerberos = true,
    kafka = true,
    kafkaKerberos = true,
    schemaRegistry = true,
    schemaRegistryKerberos = true,
)
class MyKerberosIntegrationTest
```

JUnit tests can also route container logs to the main test process console or to files:

```kotlin
@BigDataTest(
    kafka = true,
    schemaRegistry = true,
    containerLogMode = ContainerLogMode.FILE,
    containerLogDirectory = "build/container-logs",
)
class MyTroubleshootingTest
```

`ContainerLogMode.STDOUT` prefixes each line with the service name. `ContainerLogMode.FILE` writes files such as `kafka.log`, `schema-registry.log`, `hive-metastore.log`, and `hive-metastore-postgres.log`.

## Spring Boot

Add the starter and enable the kit in local-development or test configuration:

```yaml
bigdata:
  test:
    enabled: true
    hive-metastore:
      enabled: true
    kafka:
      enabled: true
      kerberos-enabled: true
      schema-registry-enabled: true
      schema-registry-kerberos-enabled: true
    localstack-s3:
      enabled: true
```

The auto-configuration exposes a started `BigDataTestKit` bean and closes it with the application context.

## Dependency Management

The build uses the Testcontainers BOM `org.testcontainers:testcontainers-bom:2.0.4`. In this repository it is applied once from the root `subprojects` block, so individual modules and examples should depend on Testcontainers modules without repeating the BOM.

For external consumers, import the same BOM in your own dependency-management setup before adding `bigdata-test` and any direct Testcontainers dependencies. With Testcontainers 2.x, module coordinates use the `testcontainers-` prefix, for example `org.testcontainers:testcontainers-junit-jupiter` and `org.testcontainers:testcontainers-postgresql`.

## Examples

Run the Spring example without starting containers:

```bash
GRADLE_USER_HOME=/data/.gradle ./gradlew :example:spring:check
```

Run the Spring example with the Kerberos profile when you want it to start containers:

```bash
GRADLE_USER_HOME=/data/.gradle ./gradlew :example:spring:bootRun --args='--spring.profiles.active=kerberos'
```

The JUnit examples in `example/junit` are annotated with `@Disabled`; remove that annotation from an example class to start the configured stack.

The Spark example in `example/spark` shows a JUnit test that creates a `SparkSession` from `BigDataTestKit` endpoints and configures HDFS, Hive Metastore, Kafka, S3A, and fake GCS settings. It is also disabled by default because it starts the full container stack:

```bash
GRADLE_USER_HOME=/data/.gradle ./gradlew :example:spark:test
```
