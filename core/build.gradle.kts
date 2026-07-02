plugins {
    id("buildsrc.convention.kotlin-jvm")
}


dependencies {
    implementation(libs.bouncycastlePkix)
    implementation(libs.bouncycastleProvider)
    implementation(libs.jtoml)
    api(libs.testcontainers)
    api(libs.testcontainersPostgresql)
    api(libs.testcontainersMysql)
    api(libs.testcontainersKafka)
    api(libs.hiveDockerTestcontainers)
    runtimeOnly(libs.mysqlConnector)
    testImplementation(libs.junitJupiterApi)
    testRuntimeOnly(libs.junitJupiterEngine)
    testRuntimeOnly(libs.junitPlatformLauncher)
}
