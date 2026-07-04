# App Extension Example

This example shows how a project-specific JUnit 5 extension can consume the
`bigdata-test` context after `@BigDataTest` starts the kit.

Modules:

- `dummy-app-framework`: a small app framework with `start()` and `stop()`.
- `dummy-app-test-extension`: a reusable JUnit 5 extension that reads
  `BigDataJunitContext`, builds app config, starts/stops the app, and injects it
  into test methods.
- `usage`: a test module showing how an application test uses `@BigDataTest`,
  `@BigDataExtensions`, and `@DummyAppTest` together.

The `usage` module also starts a user-owned Elasticsearch Testcontainer with
`org.testcontainers:testcontainers-elasticsearch`. This demonstrates that user
Testcontainers can coexist with the containers managed by `bigdata-test`; keep
those containers in the test or application extension code and let the BigData
kit manage only the services declared through its own config.

Run it with:

```bash
GRADLE_USER_HOME=/data/.gradle ./gradlew :example:app-extension:usage:test
```
