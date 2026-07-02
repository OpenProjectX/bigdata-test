plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
    alias(libs.plugins.javaDns)
    alias(libs.plugins.shadow)
}

description = "Config-driven bigdata-test extensions for Hadoop credential providers, Kafka, and Avro"

val shadedRuntime by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    resolutionStrategy.eachDependency {
        if (requested.group.startsWith("com.fasterxml.jackson")) {
            useVersion("2.15.2")
            because("Spark 3.5.x expects Jackson 2.15.x at runtime.")
        }
    }
    resolutionStrategy.capabilitiesResolution.withCapability("org.lz4:lz4-java") {
        select("at.yawk.lz4:lz4-java:${libs.versions.lz4.get()}")
        because("Kafka/Confluent and Spark publish different providers for the same lz4 capability.")
    }
}

dependencies {
    api(project(":junit5"))

    val bootBom = platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}")

    compileOnly(bootBom)
    testImplementation(bootBom)

    compileOnly("org.springframework.boot:spring-boot")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework:spring-context")
    compileOnly("org.springframework:spring-beans")
    compileOnly("org.springframework:spring-core")
    compileOnly(libs.hadoopClientApi)
    compileOnly(libs.hadoopClientRuntime)
    compileOnly(libs.kafkaAvroSerializer)
    compileOnly(libs.kafkaSchemaRegistryClient)
    compileOnly(libs.avro)
    compileOnly(libs.awsSdkS3)
    compileOnly(libs.googleCloudStorage)
    compileOnly(libs.sparkSql)
    compileOnly(libs.sparkHive)
    compileOnly(libs.servletApi)
    implementation(libs.kotlinxSerialization)
    implementation(libs.jtoml)

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
    shadedRuntime(libs.sparkSql) {
        exclude(group = "org.rocksdb", module = "rocksdbjni")
    }
    shadedRuntime(libs.sparkHive) {
        exclude(group = "org.rocksdb", module = "rocksdbjni")
    }
    shadedRuntime(libs.icebergSpark)
    shadedRuntime(libs.icebergSparkExtensions)
    shadedRuntime(libs.icebergHiveMetastore)
    shadedRuntime(libs.icebergAwsBundle)
    shadedRuntime(libs.servletApi)
    shadedRuntime(libs.kotlinxSerialization)
    shadedRuntime(libs.jtoml)

    testImplementation(libs.junitJupiterApi)
    testImplementation(libs.hadoopClientApi)
    testImplementation(libs.hadoopClientRuntime)
    testImplementation(libs.kafkaAvroSerializer)
    testImplementation(libs.kafkaSchemaRegistryClient)
    testImplementation(libs.avro)
    testImplementation(libs.awsSdkS3)
    testImplementation(libs.googleCloudStorage)
    testImplementation(libs.sparkSql)
    testImplementation(libs.sparkHive)
    testRuntimeOnly(libs.junitJupiterEngine)
    testRuntimeOnly(libs.junitPlatformLauncher)
    testRuntimeOnly(libs.slf4jSimple)
    testRuntimeOnly(libs.servletApi)
}

val runtimeShadowJar = tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveClassifier.set("runtime")
    configurations = listOf(shadedRuntime)
    isZip64 = true
    mergeServiceFiles()
    relocate("com.fasterxml.jackson", "org.openprojectx.bigdata.test.shaded.fasterxml.jackson")
}

tasks.withType<GenerateModuleMetadata>().configureEach {
    dependsOn(runtimeShadowJar)
}

configurations.matching { it.name.startsWith("test") }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group.startsWith("com.fasterxml.jackson")) {
            useVersion("2.15.2")
        }
    }
}

tasks.withType<Test>().configureEach {
    jvmArgs(
        "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens=java.base/java.nio=ALL-UNNAMED",
        "--add-opens=java.base/java.net=ALL-UNNAMED",
    )
}

javadns {
    hosts.put("hdfs.test.local", "127.0.0.1")
    hosts.put("hdfs", "127.0.0.1")

}
