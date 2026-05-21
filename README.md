# bigdata-test

Composable Testcontainers-based fixtures for local big-data integration tests.

## Modules

- `core`: container builder and endpoint/property model
- `junit5`: `@BigDataTest` extension for JUnit 5 tests
- `bigdata-test-spring-boot-autoconfigure`: Spring Boot auto-configuration
- `bigdata-test-spring-boot-starter`: starter that brings in the auto-configuration
- `example:spring`: Spring Boot local-development example
- `example:junit`: JUnit 5 integration-test examples

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

Kerberos can be enabled per service:

```kotlin
@BigDataTest(
    kerberos = true,
    hiveMetastore = true,
    hiveMetastoreKerberos = true,
    kafka = true,
    kafkaKerberos = true,
    schemaRegistry = true,
    schemaRegistryKerberos = true,
)
class MyKerberosIntegrationTest
```

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
