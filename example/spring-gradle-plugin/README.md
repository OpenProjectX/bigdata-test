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

The Gradle plugin starts Kerberos, HDFS, and LocalStack S3 before `bootRunLocal` launches the
Spring JVM, runs the TOML extensions, and injects endpoint/extension output as JVM system
properties and environment variables. The application reads the JVM properties from
`application-local.yaml`; non-Spring code can read equivalent env vars such as
`BIGDATA_TEST_ENDPOINT_HDFS_HOST`.
