plugins {
    id("buildsrc.convention.kotlin-jvm")
}


dependencies {
    implementation(libs.bouncycastlePkix)
    implementation(libs.bouncycastleProvider)
    api(libs.testcontainers)
    api(libs.testcontainersPostgresql)
    api(libs.testcontainersKafka)
    api(libs.hiveDockerTestcontainers)
    testImplementation(libs.junitJupiterApi)
    testRuntimeOnly(libs.junitJupiterEngine)
    testRuntimeOnly(libs.junitPlatformLauncher)
}
