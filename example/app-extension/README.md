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

Run it with:

```bash
GRADLE_USER_HOME=/data/.gradle ./gradlew :example:app-extension:usage:test
```
