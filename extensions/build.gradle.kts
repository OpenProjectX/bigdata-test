plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
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
}
