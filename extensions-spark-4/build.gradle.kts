plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.shadow)
}

description = "Isolated Spark 4.1 runtime for bigdata-test extensions"

val shadedRuntime by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    resolutionStrategy.eachDependency {
        if (requested.group.startsWith("com.fasterxml.jackson")) {
            useVersion(
                if (requested.name == "jackson-annotations") "2.20" else "2.20.0",
            )
            because("Spark 4.1 uses Jackson 2.20.x.")
        }
    }
    resolutionStrategy.capabilitiesResolution.withCapability("org.lz4:lz4-java") {
        select("at.yawk.lz4:lz4-java:${libs.versions.lz4.get()}")
        because("Kafka/Confluent and Spark publish different providers for the same lz4 capability.")
    }
}

dependencies {
    api(project(":extensions"))

    shadedRuntime(project(":extensions"))
    shadedRuntime(libs.hadoopClientApi)
    shadedRuntime(libs.hadoopClientRuntime)
    shadedRuntime(libs.hadoopAws) {
        exclude(group = "software.amazon.awssdk", module = "bundle")
    }
    shadedRuntime(libs.awsSdkS3)
    shadedRuntime(libs.awsSdkS3TransferManager)
    shadedRuntime(libs.googleCloudStorage)
    shadedRuntime(libs.kafkaAvroSerializer)
    shadedRuntime(libs.kafkaSchemaRegistryClient)
    shadedRuntime(libs.lz4Java)
    shadedRuntime(libs.avro)
    shadedRuntime(libs.spark4Sql) {
        exclude(group = "org.rocksdb", module = "rocksdbjni")
    }
    shadedRuntime(libs.spark4Hive) {
        exclude(group = "org.rocksdb", module = "rocksdbjni")
    }
    shadedRuntime(libs.icebergSpark4)
    shadedRuntime(libs.icebergSpark4Extensions)
    shadedRuntime(libs.icebergHiveMetastore)
    shadedRuntime(libs.icebergAwsBundle)
    shadedRuntime(libs.trinoJdbc)

    testImplementation(libs.junitJupiterApi)
    testImplementation(libs.spark4Sql)
    testImplementation(libs.spark4Hive)
    testRuntimeOnly(libs.junitJupiterEngine)
    testRuntimeOnly(libs.junitPlatformLauncher)
    testRuntimeOnly(libs.slf4jSimple)
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveClassifier.set("runtime")
    configurations = listOf(shadedRuntime)
    isZip64 = true
    mergeServiceFiles()
    relocate("com.fasterxml.jackson", "org.openprojectx.bigdata.test.shaded.fasterxml.jackson")
}

tasks.withType<GenerateModuleMetadata>().configureEach {
    dependsOn(tasks.named("shadowJar"))
}

configurations.matching { it.name.startsWith("test") }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group.startsWith("com.fasterxml.jackson")) {
            useVersion(if (requested.name == "jackson-annotations") "2.20" else "2.20.0")
        }
    }
}

tasks.withType<Test>().configureEach {
    jvmArgs(
        "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/java.net=ALL-UNNAMED",
        "--add-opens=java.base/java.nio=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
        "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
        "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens=java.base/sun.nio.cs=ALL-UNNAMED",
        "--add-opens=java.base/sun.security.action=ALL-UNNAMED",
        "--add-opens=java.base/sun.util.calendar=ALL-UNNAMED",
        "--add-opens=java.security.jgss/sun.security.krb5=ALL-UNNAMED",
    )
}
