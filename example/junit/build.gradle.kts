plugins {
    id("buildsrc.convention.kotlin-jvm")
}

description = "JUnit 5 example for bigdata-test"

dependencies {
    testImplementation(project(":junit5"))
    testImplementation(project(":extensions"))
    testImplementation(libs.kafkaClients)
    testImplementation(libs.junitJupiterApi)
    testRuntimeOnly(libs.junitJupiterEngine)
    testRuntimeOnly(libs.junitPlatformLauncher)
}
