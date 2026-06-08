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
    compileOnly(libs.sparkSql)
    compileOnly(libs.sparkHive)
    implementation(libs.kotlinxSerialization)

    shadedRuntime(bootBom)
    shadedRuntime("org.springframework.boot:spring-boot")
    shadedRuntime("org.springframework.boot:spring-boot-autoconfigure")
    shadedRuntime("org.springframework:spring-context")
    shadedRuntime("org.springframework:spring-beans")
    shadedRuntime("org.springframework:spring-core")
    shadedRuntime(libs.hadoopClientApi)
    shadedRuntime(libs.hadoopClientRuntime)
    shadedRuntime(libs.hadoopAws)
    shadedRuntime(libs.kafkaAvroSerializer)
    shadedRuntime(libs.kafkaSchemaRegistryClient)
    shadedRuntime(libs.avro)
    shadedRuntime(libs.sparkSql)
    shadedRuntime(libs.sparkHive)
    shadedRuntime(libs.kotlinxSerialization)

    testImplementation(libs.junitJupiterApi)
    testImplementation(libs.hadoopClientApi)
    testImplementation(libs.hadoopClientRuntime)
    testImplementation(libs.kafkaAvroSerializer)
    testImplementation(libs.kafkaSchemaRegistryClient)
    testImplementation(libs.avro)
    testImplementation(libs.sparkSql)
    testImplementation(libs.sparkHive)
    testRuntimeOnly(libs.junitJupiterEngine)
    testRuntimeOnly(libs.junitPlatformLauncher)
    testRuntimeOnly(libs.slf4jSimple)
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveClassifier.set("runtime")
    configurations = listOf(shadedRuntime)
    isZip64 = true
    mergeServiceFiles()
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
