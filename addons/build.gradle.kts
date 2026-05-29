plugins {
    id("buildsrc.convention.kotlin-jvm")
}

description = "Optional bigdata-test helpers for Hadoop credential providers, Kafka, and Avro"

dependencies {
    api(libs.hadoopClientApi)
    api(libs.hadoopClientRuntime)
    api(libs.kafkaAvroSerializer)
    api(libs.avro)

    implementation(libs.kafkaSchemaRegistryClient)
}
