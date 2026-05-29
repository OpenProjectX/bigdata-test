package org.openprojectx.bigdata.test.example.spark

import org.apache.spark.sql.SparkSession
import org.junit.jupiter.api.Test
import org.openprojectx.bigdata.test.addons.hadoop.HadoopCredentialProviders
import org.openprojectx.bigdata.test.addons.kafka.AvroKafkaRecord
import org.openprojectx.bigdata.test.addons.kafka.KafkaAvroProducers
import org.openprojectx.bigdata.test.core.BigDataService
import org.openprojectx.bigdata.test.core.BigDataTestKit
import org.openprojectx.bigdata.test.core.ContainerLogMode
import org.openprojectx.bigdata.test.junit5.BigDataTest
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Duration

@BigDataTest(
    hdfs = true,
    hiveMetastore = true,
    kafka = true,
    schemaRegistry = true,
    localStackS3 = true,
    fakeGcs = true,
    containerLogMode = ContainerLogMode.FILE,
)
class SparkBigDataTestExample {
    @Test
    fun writesAvroKafkaEventsToIcebergOnObjectStorage(kit: BigDataTestKit) {
        val hdfs = kit.endpoint(BigDataService.HDFS)
        val hiveMetastore = kit.endpoint(BigDataService.HIVE_METASTORE)
        val kafka = kit.endpoint(BigDataService.KAFKA)
        val schemaRegistry = kit.endpoint(BigDataService.SCHEMA_REGISTRY)
        val s3 = kit.endpoint(BigDataService.LOCALSTACK_S3)
        val gcs = kit.endpoint(BigDataService.FAKE_GCS)

        val runId = System.nanoTime().toString()
        val topic = "spark-avro-events-$runId"
        val s3Bucket = "spark-iceberg-s3-$runId"
        val gcsBucket = "spark-iceberg-gcs-$runId"
        val hdfsConfigDir = "/bigdata-test/spark/$runId"
        val s3CredentialProviderPath = HadoopCredentialProviders.hdfsJceksPath(hdfsConfigDir, "s3.jceks")
        createS3Bucket(s3.property("aws.endpoint-url.s3"), s3Bucket)
        createGcsBucket(gcs.property("google.cloud.storage.host"), gcsBucket)
        HadoopCredentialProviders.createHdfsJceks(
            hdfsUri = hdfs.property("fs.defaultFS"),
            configDir = hdfsConfigDir,
            providerPath = s3CredentialProviderPath,
            credentials = mapOf(
                "fs.s3a.access.key" to s3.property("aws.accessKeyId"),
                "fs.s3a.secret.key" to s3.property("aws.secretAccessKey"),
            ),
        )
        produceAvroEvents(
            bootstrapServers = kafka.property("bootstrap.servers"),
            schemaRegistryUrl = schemaRegistry.property("schema.registry.url"),
            topic = topic,
        )

        sparkSession(
            hdfsUri = hdfs.property("fs.defaultFS"),
            hiveMetastoreUri = hiveMetastore.property("hive.metastore.uris"),
            s3Endpoint = s3.property("aws.endpoint-url.s3"),
            s3CredentialProviderPath = s3CredentialProviderPath,
            s3Bucket = s3Bucket,
            gcsEndpoint = gcs.property("google.cloud.storage.host"),
            gcsBucket = gcsBucket,
        ).use { spark ->
            assertHdfsConfigStore(spark, hdfs.property("fs.defaultFS"), hdfsConfigDir)
            assertKafkaAvroInput(spark, kafka.property("bootstrap.servers"), topic)
            assertIcebergTable(spark, catalog = "s3", namespace = "demo_$runId", table = "events_s3", storageName = "s3")
            assertIcebergTable(
                spark,
                catalog = "gcs_local",
                namespace = "demo_$runId",
                table = "events_gcs",
                storageName = "gcs",
                dataPath = "gs://$gcsBucket/data/demo_$runId/events_gcs",
            )
        }
    }

    private fun sparkSession(
        hdfsUri: String,
        hiveMetastoreUri: String,
        s3Endpoint: String,
        s3CredentialProviderPath: String,
        s3Bucket: String,
        gcsEndpoint: String,
        gcsBucket: String,
    ): SparkSession =
        SparkSession.builder()
            .appName("bigdata-test-spark-example")
            .master("local[2]")
            .config("spark.ui.enabled", "false")
            .config("spark.driver.extraJavaOptions", "--add-opens=java.base/java.nio=ALL-UNNAMED")
            .config("spark.executor.extraJavaOptions", "--add-opens=java.base/java.nio=ALL-UNNAMED")
            .config("spark.sql.extensions", "org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions")
            .config("spark.sql.catalog.s3", "org.apache.iceberg.spark.SparkCatalog")
            .config("spark.sql.catalog.s3.type", "hadoop")
            .config("spark.sql.catalog.s3.warehouse", "s3a://$s3Bucket/warehouse")
            .config("spark.sql.catalog.gcs_local", "org.apache.iceberg.spark.SparkCatalog")
            .config("spark.sql.catalog.gcs_local.type", "hadoop")
            .config("spark.sql.catalog.gcs_local.warehouse", "file:${Files.createTempDirectory("bigdata-test-gcs-iceberg-warehouse-")}")
            .config("spark.sql.warehouse.dir", "file:${Files.createTempDirectory("bigdata-test-spark-warehouse-")}")
            .config("hive.metastore.uris", hiveMetastoreUri)
            .config("spark.hadoop.fs.defaultFS", hdfsUri)
            .config("spark.hadoop.hadoop.security.credential.provider.path", s3CredentialProviderPath)
            .config("spark.hadoop.fs.s3a.endpoint", s3Endpoint)
            .config("spark.hadoop.fs.s3a.aws.credentials.provider", "org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider")
            .config("spark.hadoop.fs.s3a.path.style.access", "true")
            .config("spark.hadoop.fs.s3a.connection.ssl.enabled", "false")
            .config("spark.hadoop.fs.gs.impl", "com.google.cloud.hadoop.fs.gcs.GoogleHadoopFileSystem")
            .config("spark.hadoop.fs.AbstractFileSystem.gs.impl", "com.google.cloud.hadoop.fs.gcs.GoogleHadoopFS")
            .config("spark.hadoop.fs.gs.project.id", "bigdata-test")
            .config("spark.hadoop.fs.gs.storage.root.url", "${gcsEndpoint.trimEnd('/')}/")
            .config("spark.hadoop.fs.gs.storage.service.path", "storage/v1/")
            .config("spark.hadoop.fs.gs.client.type", "HTTP_API_CLIENT")
            .config("spark.hadoop.fs.gs.http.connect-timeout", "4000")
            .config("spark.hadoop.fs.gs.auth.type", "UNAUTHENTICATED")
            .config("spark.hadoop.fs.gs.create.items.conflict.check.enable", "false")
            .config("spark.hadoop.fs.gs.implicit.dir.repair.enable", "false")
            .config("spark.hadoop.fs.gs.hierarchical.namespace.folders.enable", "false")
            .enableHiveSupport()
            .getOrCreate()

    private fun assertHdfsConfigStore(spark: SparkSession, hdfsUri: String, configDir: String) {
        check(HadoopCredentialProviders.exists(hdfsUri, "$configDir/s3.jceks")) {
            "Expected S3 JCEKS file in HDFS for ${spark.sparkContext().appName()}"
        }
    }

    private fun assertKafkaAvroInput(spark: SparkSession, bootstrapServers: String, topic: String) {
        val rows = spark.read()
            .format("kafka")
            .option("kafka.bootstrap.servers", bootstrapServers)
            .option("subscribe", topic)
            .option("startingOffsets", "earliest")
            .option("endingOffsets", "latest")
            .load()
        check(rows.count() == 2L) { "Expected two Avro Kafka records in $topic" }
    }

    private fun assertIcebergTable(
        spark: SparkSession,
        catalog: String,
        namespace: String,
        table: String,
        storageName: String,
        dataPath: String? = null,
    ) {
        val identifier = "$catalog.$namespace.$table"
        spark.sql("CREATE NAMESPACE IF NOT EXISTS $catalog.$namespace")
        val tableProperties = dataPath?.let { " TBLPROPERTIES ('write.data.path'='$it')" }.orEmpty()
        spark.sql(
            """
            CREATE TABLE $identifier (
                id INT,
                name STRING,
                storage STRING
            ) USING iceberg$tableProperties
            """.trimIndent(),
        )
        spark.sql("INSERT INTO $identifier VALUES (1, 'alpha', '$storageName'), (2, 'beta', '$storageName')")
        val count = spark.table(identifier).where("storage = '$storageName'").count()
        check(count == 2L) { "Expected two Iceberg rows in $identifier" }
    }


    private fun produceAvroEvents(bootstrapServers: String, schemaRegistryUrl: String, topic: String) {
        KafkaAvroProducers.produce(
            bootstrapServers = bootstrapServers,
            schemaRegistryUrl = schemaRegistryUrl,
            topic = topic,
            schemaJson = """
            {
              "type": "record",
              "name": "SparkEvent",
              "namespace": "org.openprojectx.bigdata.test.example.spark",
              "fields": [
                {"name": "id", "type": "int"},
                {"name": "name", "type": "string"}
              ]
            }
            """.trimIndent(),
            records = listOf(
                AvroKafkaRecord("alpha", mapOf("id" to 1, "name" to "alpha")),
                AvroKafkaRecord("beta", mapOf("id" to 2, "name" to "beta")),
            ),
        )
    }

    private fun createS3Bucket(endpoint: String, bucket: String) {
        val request = HttpRequest.newBuilder(URI.create("$endpoint/$bucket"))
            .timeout(Duration.ofSeconds(10))
            .PUT(HttpRequest.BodyPublishers.noBody())
            .build()
        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding())
        check(response.statusCode() in setOf(200, 409)) { "Failed to create S3 bucket $bucket: HTTP ${response.statusCode()}" }
    }

    private fun createGcsBucket(endpoint: String, bucket: String) {
        val request = HttpRequest.newBuilder(URI.create("$endpoint/storage/v1/b?project=bigdata-test"))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("""{"name":"$bucket"}""", StandardCharsets.UTF_8))
            .build()
        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding())
        check(response.statusCode() in setOf(200, 201, 409)) { "Failed to create GCS bucket $bucket: HTTP ${response.statusCode()}" }
    }

}
