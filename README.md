# bigdata-test

Composable Testcontainers fixtures for local big-data integration tests.

`bigdata-test` starts only the services a test asks for and exposes their
connection properties through a small Kotlin/JUnit API. Heavier setup such as
S3 JCEKS generation, bucket creation, and Kafka Avro seeding lives in the
optional `extensions` module so `core` and `junit5` stay lightweight.
Open-source Hive Metastore uses PostgreSQL by default and can be switched to
MySQL with TOML `databaseType = "mysql"` under `[hiveMetastore]`. Its support
database uses a random host port by default; set `databaseHostPort` when you
need a stable local port.
Cloudera HMS defaults to `ghcr.io/openprojectx/cloudera-hms:0.1.74`; set
`[clouderaHms] databaseType = "mariadb"` for the `0.1.74-mariadb` image.
`[clouderaHms] databaseHostPort` can expose the embedded PostgreSQL/MariaDB
port on a stable local port for troubleshooting.

## Modules

- `core`: container builder, service options, endpoints, and log routing
- `junit5`: `@BigDataTest` extension and parameter injection
- `extensions`: config-driven setup hooks for JCEKS, buckets, Kafka Avro, and Kerberos material
- `gradle-plugin`: Gradle plugin that starts the test kit outside the application JVM
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

HTTP services can be exposed through an HAProxy TLS gateway from TOML:

```toml
[services]
localStackS3 = true

[localStackS3Tls]
enabled = true
domain = "localhost"
```

The endpoint properties then return HTTPS URLs and JVM truststore settings such as `javax.net.ssl.trustStore`.

For image-specific troubleshooting, containers can be customized from TOML:

```toml
[containers.hdfs.env]
HADOOP_OPTS = "-Dsun.security.krb5.debug=true"

[containers.hdfs.files]
"/tmp/message.txt" = "text:created by bigdata-test"
```

Programmatic tests can also call `customizeContainer(...)` for last-resort Testcontainers access.

For HDFS, avoid arbitrary env names whose second token is an Apache Hadoop image config format such as `ENV`, `CONF`, `XML`, or `SH`. For example, `TEST_ENV=TEST` is parsed by `apache/hadoop:3.5.0` as an env-to-config instruction and fails before HDFS starts. Use a non-reserved name such as `TEST_VALUE`, or use the image convention intentionally, for example `CORE_XML_fs_defaultFS=...`.

Optional CLI health checks can run after container startup:

```toml
[healthChecks]
hdfs = "cli"
localStackS3 = "cli"
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

Run the Spark dependency/HMS/Kerberos matrix:

```bash
GRADLE_USER_HOME=/data/.gradle ./gradlew :example:spark:sparkBigDataMatrixTest
```

Individual matrix cells are also available. The first axis selects the Spark/Hadoop dependency line, then HMS implementation, then Kerberos:

```bash
GRADLE_USER_HOME=/data/.gradle ./gradlew :example:spark:sparkApacheDepsApacheHmsTest
GRADLE_USER_HOME=/data/.gradle ./gradlew :example:spark:sparkApacheDepsApacheHmsKerberosTest
GRADLE_USER_HOME=/data/.gradle ./gradlew :example:spark:sparkApacheDepsClouderaHmsTest
GRADLE_USER_HOME=/data/.gradle ./gradlew :example:spark:sparkApacheDepsClouderaHmsKerberosTest
GRADLE_USER_HOME=/data/.gradle ./gradlew :example:spark:sparkClouderaDepsApacheHmsTest
GRADLE_USER_HOME=/data/.gradle ./gradlew :example:spark:sparkClouderaDepsApacheHmsKerberosTest
GRADLE_USER_HOME=/data/.gradle ./gradlew :example:spark:sparkClouderaDepsClouderaHmsTest
GRADLE_USER_HOME=/data/.gradle ./gradlew :example:spark:sparkClouderaDepsClouderaHmsKerberosTest
```

Cloudera HMS Kerberos cells are currently opt-in while the Cloudera HMS image-side auth configuration is being stabilized:

```bash
GRADLE_USER_HOME=/data/.gradle ./gradlew :example:spark:sparkBigDataMatrixTest \
  -Pbigdata.spark.enableClouderaHmsKerberos=true
```

Current matrix constraints:

- The default matrix covers open-source Hive HMS with Kerberos, including HDFS Kerberos, Kafka Kerberos, and S3/GCS table smoke tests.
- Cloudera HMS plaintext cells run by default.
- Cloudera HMS Kerberos cells are disabled by default because the current Cloudera HMS image path can fail during HMS server-side Kerberos transport setup before the thrift endpoint is usable.

## Documentation

- Detailed usage: [doc/user-guide.adoc](doc/user-guide.adoc)
- Contributing: [CONTRIBUTING.md](CONTRIBUTING.md)
- Current Hive Docker HMS notes: [doc/hive-docker-hms-issues.adoc](doc/hive-docker-hms-issues.adoc)
