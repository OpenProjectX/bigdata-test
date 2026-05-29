plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

description = "Config-driven bigdata-test extensions for Hadoop credential providers, Kafka, and Avro"

dependencies {
    api(project(":junit5"))
    api(libs.hadoopClientApi)
    api(libs.hadoopClientRuntime)
    api(libs.kafkaAvroSerializer)
    api(libs.avro)
    api(libs.kotlinxSerialization)

    implementation(libs.kafkaSchemaRegistryClient)
}
