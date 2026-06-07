import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage

plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("org.openprojectx.spark.platform") version "0.1.41"
    id("org.openprojectx.hadoop-native-loader") version "0.1.1"
    alias(libs.plugins.javaDns)

}

description = "Spark JUnit 5 example for bigdata-test"

sparkPlatform {
    line.set("cloudera")
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
    testImplementation(libs.hadoopClientApi)
    testImplementation(libs.hadoopClientRuntime)
    testImplementation(libs.kafkaAvroSerializer)
    testImplementation(libs.kafkaSchemaRegistryClient)
    testImplementation(libs.avro)
    testImplementation("org.apache.spark:spark-sql_2.12")
    testImplementation("org.apache.spark:spark-hive_2.12")
    testImplementation("org.apache.spark:spark-sql-kafka-0-10_2.12")
    testImplementation("org.apache.spark:spark-avro_2.12")
    testImplementation("org.apache.hadoop:hadoop-aws")
    testImplementation("org.apache.iceberg:iceberg-aws-bundle")
    testImplementation("com.google.cloud.bigdataoss:gcs-connector")
    testImplementation("com.google.cloud.bigdataoss:gcsio")
    testImplementation("com.google.cloud.bigdataoss:util-hadoop")
    testImplementation(libs.junitJupiterApi)
    testRuntimeOnly(libs.junitJupiterEngine)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

val apacheSparkVersion = libs.versions.spark.get()
val apacheHadoopVersion = libs.versions.hadoop.get()
val icebergVersion = libs.versions.iceberg.get()
val apacheSparkJacksonVersion = "2.15.2"
val clouderaSparkJacksonScalaModuleVersion = "2.12.7"
val clouderaSparkJacksonDatabindVersion = "2.12.7.1"
val testImplementationConfiguration = configurations.named("testImplementation")
val testRuntimeOnlyConfiguration = configurations.named("testRuntimeOnly")

fun createSparkRuntimeClasspath(name: String, dependencyLine: String) = configurations.create(name) {
    isCanBeConsumed = false
    isCanBeResolved = true
    extendsFrom(testImplementationConfiguration.get(), testRuntimeOnlyConfiguration.get())

    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class, Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class, Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements::class, LibraryElements.JAR))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling::class, Bundling.EXTERNAL))
    }

    if (dependencyLine == "apache") {
        resolutionStrategy.eachDependency {
            when (requested.group) {
                "org.apache.spark" -> useVersion(apacheSparkVersion)
                "org.apache.hadoop" -> useVersion(apacheHadoopVersion)
            }
            if (requested.group.startsWith("com.fasterxml.jackson")) {
                useVersion(apacheSparkJacksonVersion)
            }
        }
    } else if (dependencyLine == "cloudera") {
        resolutionStrategy.eachDependency {
            when {
                requested.group == "com.fasterxml.jackson.core" && requested.name == "jackson-databind" ->
                    useVersion(clouderaSparkJacksonDatabindVersion)

                requested.group == "com.fasterxml.jackson.module" && requested.name == "jackson-module-scala_2.12" ->
                    useVersion(clouderaSparkJacksonScalaModuleVersion)
            }
        }
    }
}

val apacheSparkRuntimeClasspath = createSparkRuntimeClasspath("apacheSparkRuntimeClasspath", "apache")
val clouderaSparkRuntimeClasspath = createSparkRuntimeClasspath("clouderaSparkRuntimeClasspath", "cloudera")

dependencies {
    add(apacheSparkRuntimeClasspath.name, "org.apache.iceberg:iceberg-spark-runtime-3.5_2.12:$icebergVersion")
    add(apacheSparkRuntimeClasspath.name, "org.apache.logging.log4j:log4j-slf4j-impl:2.20.0")
    add(clouderaSparkRuntimeClasspath.name, "org.apache.iceberg:iceberg-spark-runtime-3.3_2.12")
}

fun Test.useSparkRuntimeClasspath(runtimeClasspath: Configuration, dependencyLine: String) {
    classpath = sourceSets.test.get().output + sourceSets.main.get().output + runtimeClasspath
    systemProperty("bigdata.spark.dependency.line", dependencyLine)
}

tasks.withType<Test>().configureEach {
    outputs.upToDateWhen { false }
    minHeapSize = "2048m"
    maxHeapSize = "8192m"
    jvmArgs(
        "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens=java.base/java.nio=ALL-UNNAMED",
        "--add-opens=java.base/java.net=ALL-UNNAMED",
    )
}

javadns {
    hosts.put("hdfs", "127.0.0.1")
}

tasks.named<Test>("test") {
    useSparkRuntimeClasspath(clouderaSparkRuntimeClasspath, "cloudera")
}

val sparkBigDataTestClass = "org.openprojectx.bigdata.test.example.spark.SparkBigDataTestExample"
val sparkCommonConfig = "classpath:spark-bigdata-test-common.toml"

fun registerSparkMatrixTest(
    name: String,
    descriptionText: String,
    dependencyLine: String,
    hmsDistribution: String,
    runtimeClasspath: Configuration,
    variantConfig: String,
) = tasks.register<Test>(name) {
    description = descriptionText
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    useSparkRuntimeClasspath(runtimeClasspath, dependencyLine)
    useJUnitPlatform()
    filter.includeTestsMatching(sparkBigDataTestClass)
    systemProperty("bigdata.test.config.replace", "true")
    systemProperty("bigdata.test.config", "$sparkCommonConfig,$variantConfig")
    systemProperty("bigdata.spark.hms.distribution", hmsDistribution)
}

val sparkApacheDepsApacheHmsTest = registerSparkMatrixTest(
    name = "sparkApacheDepsApacheHmsTest",
    descriptionText = "Runs the Spark example with Apache Spark/Hadoop deps, open-source Hive 3 HMS, and plaintext Kafka.",
    dependencyLine = "apache",
    hmsDistribution = "apache",
    runtimeClasspath = apacheSparkRuntimeClasspath,
    variantConfig = "classpath:spark-bigdata-test-apache-hms.toml",
)

val sparkApacheDepsApacheHmsKerberosTest = registerSparkMatrixTest(
    name = "sparkApacheDepsApacheHmsKerberosTest",
    descriptionText = "Runs the Spark example with Apache Spark/Hadoop deps, open-source Hive 3 HMS, HDFS Kerberos, and Kafka Kerberos.",
    dependencyLine = "apache",
    hmsDistribution = "apache",
    runtimeClasspath = apacheSparkRuntimeClasspath,
    variantConfig = "classpath:spark-bigdata-test-apache-hms-kerberos.toml",
)

val sparkApacheDepsClouderaHmsTest = registerSparkMatrixTest(
    name = "sparkApacheDepsClouderaHmsTest",
    descriptionText = "Runs the Spark example with Apache Spark/Hadoop deps, Cloudera HMS, and plaintext Kafka.",
    dependencyLine = "apache",
    hmsDistribution = "cloudera",
    runtimeClasspath = apacheSparkRuntimeClasspath,
    variantConfig = "classpath:spark-bigdata-test-cloudera-hms.toml",
)

val sparkApacheDepsClouderaHmsKerberosTest = registerSparkMatrixTest(
    name = "sparkApacheDepsClouderaHmsKerberosTest",
    descriptionText = "Runs the Spark example with Apache Spark/Hadoop deps, Cloudera HMS, HDFS Kerberos, and Kafka Kerberos.",
    dependencyLine = "apache",
    hmsDistribution = "cloudera",
    runtimeClasspath = apacheSparkRuntimeClasspath,
    variantConfig = "classpath:spark-bigdata-test-cloudera-hms-kerberos.toml",
)

val sparkClouderaDepsApacheHmsTest = registerSparkMatrixTest(
    name = "sparkClouderaDepsApacheHmsTest",
    descriptionText = "Compatibility task: Cloudera Spark/Hadoop deps always run with Cloudera HMS and plaintext Kafka.",
    dependencyLine = "cloudera",
    hmsDistribution = "cloudera",
    runtimeClasspath = clouderaSparkRuntimeClasspath,
    variantConfig = "classpath:spark-bigdata-test-cloudera-hms.toml",
)

val sparkClouderaDepsApacheHmsKerberosTest = registerSparkMatrixTest(
    name = "sparkClouderaDepsApacheHmsKerberosTest",
    descriptionText = "Compatibility task: Cloudera Spark/Hadoop deps always run with Cloudera HMS, HDFS Kerberos, and Kafka Kerberos.",
    dependencyLine = "cloudera",
    hmsDistribution = "cloudera",
    runtimeClasspath = clouderaSparkRuntimeClasspath,
    variantConfig = "classpath:spark-bigdata-test-cloudera-hms-kerberos.toml",
)

val sparkClouderaDepsClouderaHmsTest = registerSparkMatrixTest(
    name = "sparkClouderaDepsClouderaHmsTest",
    descriptionText = "Runs the Spark example with Cloudera Spark/Hadoop deps, Cloudera HMS, and plaintext Kafka.",
    dependencyLine = "cloudera",
    hmsDistribution = "cloudera",
    runtimeClasspath = clouderaSparkRuntimeClasspath,
    variantConfig = "classpath:spark-bigdata-test-cloudera-hms.toml",
)

val sparkClouderaDepsClouderaHmsKerberosTest = registerSparkMatrixTest(
    name = "sparkClouderaDepsClouderaHmsKerberosTest",
    descriptionText = "Runs the Spark example with Cloudera Spark/Hadoop deps, Cloudera HMS, HDFS Kerberos, and Kafka Kerberos.",
    dependencyLine = "cloudera",
    hmsDistribution = "cloudera",
    runtimeClasspath = clouderaSparkRuntimeClasspath,
    variantConfig = "classpath:spark-bigdata-test-cloudera-hms-kerberos.toml",
)

val clouderaHmsKerberosEnabled = providers.gradleProperty("bigdata.spark.enableClouderaHmsKerberos")
    .map(String::toBoolean)
    .getOrElse(false)

listOf(
    sparkApacheDepsClouderaHmsKerberosTest,
    sparkClouderaDepsApacheHmsKerberosTest,
    sparkClouderaDepsClouderaHmsKerberosTest,
).forEach { taskProvider ->
    taskProvider.configure {
        if (!clouderaHmsKerberosEnabled) {
            enabled = false
        }
    }
}

listOf(
    sparkApacheDepsApacheHmsKerberosTest to sparkApacheDepsApacheHmsTest,
    sparkApacheDepsClouderaHmsTest to sparkApacheDepsApacheHmsKerberosTest,
    sparkApacheDepsClouderaHmsKerberosTest to sparkApacheDepsClouderaHmsTest,
    sparkClouderaDepsApacheHmsTest to sparkApacheDepsClouderaHmsKerberosTest,
    sparkClouderaDepsApacheHmsKerberosTest to sparkClouderaDepsApacheHmsTest,
    sparkClouderaDepsClouderaHmsTest to sparkClouderaDepsApacheHmsKerberosTest,
    sparkClouderaDepsClouderaHmsKerberosTest to sparkClouderaDepsClouderaHmsTest,
).forEach { (task, predecessor) ->
    task.configure {
        mustRunAfter(predecessor)
    }
}

tasks.register("sparkBigDataMatrixTest") {
    description = "Runs all Spark dependency/HMS/Kerberos matrix combinations."
    group = "verification"
    dependsOn(
        sparkApacheDepsApacheHmsTest,
        sparkApacheDepsApacheHmsKerberosTest,
        sparkApacheDepsClouderaHmsTest,
        sparkApacheDepsClouderaHmsKerberosTest,
        sparkClouderaDepsApacheHmsTest,
        sparkClouderaDepsApacheHmsKerberosTest,
        sparkClouderaDepsClouderaHmsTest,
        sparkClouderaDepsClouderaHmsKerberosTest,
    )
}

tasks.register("sparkApacheHmsTest") {
    description = "Compatibility alias for sparkApacheDepsApacheHmsTest."
    group = "verification"
    dependsOn(sparkApacheDepsApacheHmsTest)
}

tasks.register("sparkApacheHmsKerberosTest") {
    description = "Compatibility alias for sparkApacheDepsApacheHmsKerberosTest."
    group = "verification"
    dependsOn(sparkApacheDepsApacheHmsKerberosTest)
}

tasks.register("sparkClouderaHmsTest") {
    description = "Compatibility alias for sparkClouderaDepsClouderaHmsTest."
    group = "verification"
    dependsOn(sparkClouderaDepsClouderaHmsTest)
}

tasks.register("sparkClouderaHmsKerberosTest") {
    description = "Compatibility alias for sparkClouderaDepsClouderaHmsKerberosTest."
    group = "verification"
    dependsOn(sparkClouderaDepsClouderaHmsKerberosTest)
}
