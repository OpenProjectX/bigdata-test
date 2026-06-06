plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
    alias(libs.plugins.javaDns)
}

description = "Config-driven bigdata-test extensions for Hadoop credential providers, Kafka, and Avro"

dependencies {
    api(project(":junit5"))

    implementation(libs.hadoopClientApi)
    implementation(libs.hadoopClientRuntime)
    implementation(libs.kafkaAvroSerializer)
    implementation(libs.kafkaSchemaRegistryClient)
    implementation(libs.avro)
    implementation(libs.kotlinxSerialization)

    testImplementation(libs.junitJupiterApi)
    testRuntimeOnly(libs.junitJupiterEngine)
    testRuntimeOnly(libs.junitPlatformLauncher)
    testRuntimeOnly(libs.slf4jSimple)
}

javadns {
    hosts.put("hdfs.test.local", "127.0.0.1")
    hosts.put("hdfs", "127.0.0.1")

}
