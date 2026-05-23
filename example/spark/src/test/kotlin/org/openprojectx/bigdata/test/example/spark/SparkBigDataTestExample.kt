package org.openprojectx.bigdata.test.example.spark

import org.apache.spark.sql.SparkSession
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.openprojectx.bigdata.test.core.BigDataService
import org.openprojectx.bigdata.test.core.BigDataTestKit
import org.openprojectx.bigdata.test.core.ContainerLogMode
import org.openprojectx.bigdata.test.junit5.BigDataTest

@Disabled("Example only. Remove @Disabled to start Spark against the configured Testcontainers stack.")
@BigDataTest(
    hdfs = true,
    hiveMetastore = true,
    kafka = true,
    schemaRegistry = true,
    localStackS3 = true,
    fakeGcs = true,
    containerLogMode = ContainerLogMode.STDOUT
)
class SparkBigDataTestExample {
    @Test
    fun connectsSparkToContainerServices(kit: BigDataTestKit) {
        val hdfs = kit.endpoint(BigDataService.HDFS)
        val hiveMetastore = kit.endpoint(BigDataService.HIVE_METASTORE)
        val kafka = kit.endpoint(BigDataService.KAFKA)
        val schemaRegistry = kit.endpoint(BigDataService.SCHEMA_REGISTRY)
        val s3 = kit.endpoint(BigDataService.LOCALSTACK_S3)
        val gcs = kit.endpoint(BigDataService.FAKE_GCS)

        val spark = SparkSession.builder()
            .appName("bigdata-test-spark-example")
            .master("local[2]")
            .config("spark.ui.enabled", "false")
            .config("spark.sql.warehouse.dir", "${hdfs.property("fs.defaultFS")}/user/hive/warehouse")
            .config("hive.metastore.uris", hiveMetastore.property("hive.metastore.uris"))
            .config("spark.hadoop.fs.defaultFS", hdfs.property("fs.defaultFS"))
            .config("spark.hadoop.fs.s3a.endpoint", s3.property("aws.endpoint-url.s3"))
            .config("spark.hadoop.fs.s3a.access.key", s3.property("aws.accessKeyId"))
            .config("spark.hadoop.fs.s3a.secret.key", s3.property("aws.secretAccessKey"))
            .config("spark.hadoop.fs.s3a.path.style.access", "true")
            .config("spark.hadoop.fs.s3a.connection.ssl.enabled", "false")
            .config("spark.hadoop.google.cloud.storage.host", gcs.property("google.cloud.storage.host"))
            .enableHiveSupport()
            .getOrCreate()

        spark.use { session ->
            session.sql("CREATE DATABASE IF NOT EXISTS bigdata_test_example")
            session.sql("CREATE TABLE IF NOT EXISTS bigdata_test_example.spark_smoke (id INT, name STRING) USING parquet")
            session.sql("INSERT INTO bigdata_test_example.spark_smoke VALUES (1, 'spark')")
            session.table("bigdata_test_example.spark_smoke").show(false)

            val kafkaReader = session.read()
                .format("kafka")
                .option("kafka.bootstrap.servers", kafka.property("bootstrap.servers"))
                .option("subscribe", "spark-smoke")
                .option("startingOffsets", "earliest")
                .option("endingOffsets", "latest")

            println("Configured Spark Kafka reader: $kafkaReader")
            println("Schema Registry: ${schemaRegistry.property("schema.registry.url")}")
        }
    }
}
