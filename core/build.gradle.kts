plugins {
    id("buildsrc.convention.kotlin-jvm")
}


dependencies {
    api(libs.testcontainers)
    api(libs.testcontainersPostgresql)
    api(libs.testcontainersKafka)
}
