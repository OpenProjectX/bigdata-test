plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    id("org.springframework.boot") version "4.0.5"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.openprojectx.bigdata-test")
}

group = "org.openprojectx.bigdata.test.example"
version = providers.gradleProperty("bigdataTestPluginVersion")
    .orElse(providers.environmentVariable("BIGDATA_TEST_PLUGIN_VERSION"))
    .orElse(
        providers.provider {
            file("../../gradle.properties")
                .readLines()
                .map(String::trim)
                .firstOrNull { it.startsWith("version") && it.contains("=") }
                ?.substringAfter("=")
                ?.trim()
        },
    )
    .get()

kotlin {
    jvmToolchain(17)
}

sourceSets {
    main {
        kotlin.srcDir("../spring/src/main/kotlin")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.apache.hadoop:hadoop-client-api:3.4.2")
    implementation("org.apache.commons:commons-lang3:3.18.0")
    runtimeOnly("org.apache.hadoop:hadoop-client-runtime:3.4.2")
    runtimeOnly("org.apache.hadoop:hadoop-aws:3.4.2")
    implementation("software.amazon.awssdk:s3:2.41.5")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

bigDataTest {
    autoConfigureJavaExecTasks.set(true)
    config.add("classpath:spring-bigdata-test.toml")
    extensionConfig.add("classpath:spring-bigdata-extensions.toml")
    extensionRuntime {
        hadoopVersion.set("3.4.2")
        sparkVersion.set(
            providers.gradleProperty("bigDataTestSparkVersion")
                .orElse(providers.environmentVariable("BIGDATA_TEST_SPARK_VERSION"))
                .orElse("3.5.7"),
        )
    }
}

tasks.register<org.springframework.boot.gradle.tasks.run.BootRun>("bootRunLocal") {
    group = "application"
    description = "Run the Spring example with Gradle-managed bigdata-test containers."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.openprojectx.bigdata.test.example.spring.BigDataTestExampleApplicationKt")
    systemProperty("spring.profiles.active", "local")
}
