import org.openprojectx.bigdata.test.core.ContainerLogMode

plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    id("org.springframework.boot") version "4.0.5"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.openprojectx.bigdata-test") version "0.1.9-SNAPSHOT"
}

group = "org.openprojectx.bigdata.test.example"
version = "0.1.9-SNAPSHOT"

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
    services {
        kerberos.set(true)
        hdfs.set(true)
        localStackS3.set(true)
    }
    kerberos {
        realm.set("EXAMPLE.COM")
        domain.set("example.com")
        debug.set(true)
    }
    hdfs {
        kerberosEnabled.set(true)
        dataNodeHostname.set("localhost")
    }
    ports {
        hdfsNameNode.set(8020)
        hdfsDataNode.set(9866)
        hdfsWeb.set(9870)
        localStackS3.set(4566)
    }
    containerLogs {
        mode.set(ContainerLogMode.STDOUT)
    }
    containerLogLevels.put("kerberos", "DEBUG")
    containerLogLevels.put("hdfs", "DEBUG")
    extensionConfig.add("classpath:spring-bigdata-extensions.toml")
}

tasks.register<org.springframework.boot.gradle.tasks.run.BootRun>("bootRunLocal") {
    group = "application"
    description = "Run the Spring example with Gradle-managed bigdata-test containers."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.openprojectx.bigdata.test.example.spring.BigDataTestExampleApplicationKt")
    systemProperty("spring.profiles.active", "local")
}
