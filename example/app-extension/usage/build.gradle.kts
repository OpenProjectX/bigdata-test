plugins {
    id("buildsrc.convention.kotlin-jvm")
}

description = "Usage example for an application-specific JUnit extension consuming bigdata-test context"

dependencies {
    testImplementation(project(":junit5"))
    testImplementation(project(":extensions"))
    testImplementation(project(":example:app-extension:dummy-app-framework"))
    testImplementation(project(":example:app-extension:dummy-app-test-extension"))
    testImplementation(libs.junitJupiterApi)
    testImplementation(libs.testcontainersElasticsearch)
    testRuntimeOnly(libs.junitJupiterEngine)
    testRuntimeOnly(libs.junitPlatformLauncher)
}
