# Spring Gradle Plugin Example

This standalone example runs the same Spring Boot S3A REST app as `example:spring`, but the
`BigDataTestKit` is managed by Gradle instead of Spring Boot auto-configuration.

First publish the plugin and libraries to Maven local from the repository root:

```bash
GRADLE_USER_HOME=/data/.gradle ./gradlew publishToMavenLocal
```

Then run the example:

```bash
cd example/spring-gradle-plugin
GRADLE_USER_HOME=/data/.gradle ../../gradlew bootRunLocal
```

For manual troubleshooting without starting the Spring app, keep the containers running with:

```bash
GRADLE_USER_HOME=/data/.gradle ../../gradlew bigDataTestRun
```

For Testcontainers runtime environment variables, make sure the Gradle JVM sees them. With a reused Gradle daemon, a one-off shell prefix might not be picked up, so use `--no-daemon` or stop existing daemons first:

```bash
TESTCONTAINERS_RYUK_DISABLED=true GRADLE_USER_HOME=/data/.gradle ../../gradlew --no-daemon bigDataTestRun
```

The Gradle plugin starts Kerberos, HDFS, and LocalStack S3 before `bootRunLocal` launches the
Spring JVM, runs the TOML extensions, and injects endpoint/extension output as JVM system
properties and environment variables. The application reads the JVM properties from
`application-local.yaml`; non-Spring code can read equivalent env vars such as
`BIGDATA_TEST_ENDPOINT_HDFS_HOST`.

Spark SQL preparation runs inside the Gradle JVM. In this in-process mode, Spark logs are routed
through Gradle's logging bridge, so Log4j2 console pattern and color settings are not honored for
Spark extension logs. Use Gradle logging flags such as `--info` and the extension/Spark log-level
settings for verbosity. Full pattern/color control would require running Spark preparation in an
isolated JVM instead of inside the Gradle daemon.
