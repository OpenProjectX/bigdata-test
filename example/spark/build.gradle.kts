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
    testImplementation(project(":extensions"))
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
        "--add-opens=java.base/java.net=ALL-UNNAMED",
    )
}

val sparkBigDataTestClass = "org.openprojectx.bigdata.test.example.spark.SparkBigDataTestExample"
val sparkCommonConfig = "classpath:spark-bigdata-test-common.toml"

fun registerSparkMatrixTest(
    name: String,
    descriptionText: String,
    variantConfig: String,
) = tasks.register<Test>(name) {
    description = descriptionText
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    filter.includeTestsMatching(sparkBigDataTestClass)
    systemProperty("bigdata.test.config.replace", "true")
    systemProperty("bigdata.test.config", "$sparkCommonConfig,$variantConfig")
}

val sparkApacheHmsTest = registerSparkMatrixTest(
    name = "sparkApacheHmsTest",
    descriptionText = "Runs the Spark example with open-source Hive 3 HMS and plaintext Kafka.",
    variantConfig = "classpath:spark-bigdata-test-apache-hms.toml",
)

val sparkApacheHmsKerberosTest = registerSparkMatrixTest(
    name = "sparkApacheHmsKerberosTest",
    descriptionText = "Runs the Spark example with open-source Hive 3 HMS and Kafka Kerberos.",
    variantConfig = "classpath:spark-bigdata-test-apache-hms-kerberos.toml",
)
sparkApacheHmsKerberosTest.configure {
    mustRunAfter(sparkApacheHmsTest)
}

val sparkClouderaHmsTest = registerSparkMatrixTest(
    name = "sparkClouderaHmsTest",
    descriptionText = "Runs the Spark example with Cloudera HMS and plaintext Kafka.",
    variantConfig = "classpath:spark-bigdata-test-cloudera-hms.toml",
)
sparkClouderaHmsTest.configure {
    mustRunAfter(sparkApacheHmsKerberosTest)
}

val sparkClouderaHmsKerberosTest = registerSparkMatrixTest(
    name = "sparkClouderaHmsKerberosTest",
    descriptionText = "Runs the Spark example with Cloudera HMS and Kafka Kerberos.",
    variantConfig = "classpath:spark-bigdata-test-cloudera-hms-kerberos.toml",
)
sparkClouderaHmsKerberosTest.configure {
    mustRunAfter(sparkClouderaHmsTest)
}

tasks.register("sparkBigDataMatrixTest") {
    description = "Runs all Spark HMS/Kerberos matrix combinations."
    group = "verification"
    dependsOn(
        sparkApacheHmsTest,
        sparkApacheHmsKerberosTest,
        sparkClouderaHmsTest,
        sparkClouderaHmsKerberosTest,
    )
}
