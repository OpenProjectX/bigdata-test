plugins {
    id("buildsrc.convention.kotlin-jvm")
}

description = "Spark JUnit 5 example for bigdata-test"

dependencies {
    testImplementation(project(":junit5"))
    testImplementation(libs.sparkSql)
    testImplementation(libs.sparkHive)
    testImplementation(libs.sparkSqlKafka)
    testImplementation(libs.hadoopAws)
    testImplementation(libs.junitJupiterApi)
    testRuntimeOnly(libs.junitJupiterEngine)
    testRuntimeOnly(libs.junitPlatformLauncher)
}
