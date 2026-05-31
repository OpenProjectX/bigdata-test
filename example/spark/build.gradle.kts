import org.gradle.api.artifacts.component.ModuleComponentIdentifier

plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("org.openprojectx.spark.platform") version "0.1.38-SNAPSHOT"
}

description = "Spark JUnit 5 example for bigdata-test"

sparkPlatform {
    line.set("spark3")
    variants.set(listOf("iceberg"))
    addons.set(
        listOf(
            "hadoopAws",
            "hadoopGcs",
            "icebergAws",
        )
    )

}

dependencies {
    testImplementation(project(":junit5"))
    testImplementation(project(":extensions"))
    testImplementation("org.apache.spark:spark-sql_2.12")
    testImplementation("org.apache.spark:spark-hive_2.12")
    testImplementation("org.apache.spark:spark-sql-kafka-0-10_2.12")
    testImplementation("org.apache.spark:spark-avro_2.12")
    testImplementation("org.apache.hadoop:hadoop-aws")
    testImplementation("org.apache.iceberg:iceberg-spark-runtime-3.5_2.12")
    testImplementation("org.apache.iceberg:iceberg-aws-bundle")
    testImplementation("com.google.cloud.bigdataoss:gcs-connector")
    testImplementation("com.google.cloud.bigdataoss:gcsio")
    testImplementation("com.google.cloud.bigdataoss:util-hadoop")
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
