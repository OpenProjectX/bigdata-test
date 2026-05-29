import org.gradle.api.artifacts.component.ModuleComponentIdentifier

plugins {
    id("buildsrc.convention.kotlin-jvm")
}

description = "Spark JUnit 5 example for bigdata-test"

configurations.all {
    resolutionStrategy.force("org.apache.kafka:kafka-clients:3.4.1")
    resolutionStrategy.capabilitiesResolution {
        withCapability("org.lz4:lz4-java") {
            select(candidates.first {
                val id = it.id
                id is ModuleComponentIdentifier && id.group == "at.yawk.lz4"
            })
            because("at.yawk.lz4:lz4-java 1.10.1 is the maintained fork with newer fixes")
        }
    }
}

dependencies {
    testImplementation(project(":junit5"))
    testImplementation(project(":addons"))
    testImplementation(libs.sparkSql)
    testImplementation(libs.sparkHive)
    testImplementation(libs.sparkSqlKafka)
    testImplementation(libs.sparkAvro)
    testImplementation(libs.hadoopAws)
    testImplementation(libs.icebergSparkRuntime)
    testImplementation(libs.icebergAwsBundle)
    testImplementation(libs.gcsConnector)
    testImplementation(libs.gcsio)
    testImplementation(libs.gcsUtilHadoop)
    testImplementation(libs.junitJupiterApi)
    testRuntimeOnly(libs.junitJupiterEngine)
    testRuntimeOnly(libs.junitPlatformLauncher)
}


tasks.withType<Test>().configureEach {
    minHeapSize = "2048m"
    maxHeapSize = "8192m"
    jvmArgs(
        "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens=java.base/java.nio=ALL-UNNAMED",
    )
}
