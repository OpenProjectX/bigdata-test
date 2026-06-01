package org.openprojectx.bigdata.test.example.spark

import org.apache.hadoop.hive.conf.HiveConf
import org.apache.hadoop.hive.metastore.IMetaStoreClient
import org.apache.hadoop.hive.metastore.HiveMetaStoreClient
import org.apache.spark.sql.SparkSession
import org.junit.jupiter.api.Test
import org.openprojectx.bigdata.test.core.BigDataService
import org.openprojectx.bigdata.test.core.BigDataTestKit
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionResult
import java.net.URI
import java.nio.file.Files

abstract class SparkBigDataScenario {
    protected abstract val runId: String
    protected abstract val s3BucketExtensionId: String
    protected abstract val gcsBucketExtensionId: String

    @Test
    fun runsSparkScenario(kit: BigDataTestKit, extensions: BigDataExtensionResult) {
        val environment = createEnvironment(kit, extensions)
        createSparkSession(environment).use { spark ->
            val context = SparkScenarioContext(environment, spark)
            infrastructureChecks().forEach { it.verify(context) }
            sourceChecks().forEach { it.verify(context) }
            targetChecks().forEach { it.verify(context) }
        }
    }

    protected open fun infrastructureChecks(): List<SparkScenarioCheck> =
        listOf(
            SparkScenarioCheck { context ->
                assertHdfsConfigStore(
                    spark = context.spark,
                    hdfsUri = context.environment.hdfsUri,
                    hdfsPath = context.environment.s3CredentialProviderHdfsPath,
                )
            },
        )

    protected open fun sourceChecks(): List<SparkScenarioCheck> =
        listOf(
            SparkScenarioCheck { context ->
                assertKafkaAvroInput(
                    spark = context.spark,
                    bootstrapServers = context.environment.kafkaBootstrapServers,
                    topic = kafkaAvroTopic(),
                    securityProtocol = context.environment.kafkaSecurityProtocol,
                    kerberosServiceName = context.environment.kafkaKerberosServiceName,
                    jaasConfig = context.environment.kafkaJaasConfig,
                )
            },
        )

    protected open fun targetChecks(): List<SparkScenarioCheck> =
        listOf(
            SparkScenarioCheck { context ->
                assertIcebergTable(
                    spark = context.spark,
                    catalog = "s3",
                    namespace = "demo_$runId",
                    table = "events_s3",
                    storageName = "s3",
                )
            },
            SparkScenarioCheck { context ->
                assertIcebergTable(
                    spark = context.spark,
                    catalog = "gcs_local",
                    namespace = "demo_$runId",
                    table = "events_gcs",
                    storageName = "gcs",
                    dataPath = context.environment.gcsIcebergDataPath,
                )
            },
            SparkScenarioCheck { context ->
                assertIcebergTable(
                    spark = context.spark,
                    catalog = "hms",
                    namespace = "hms_demo_$runId",
                    table = "events_hms",
                    storageName = "hms",
                )
                assertHiveMetastoreTable(
                    hiveMetastoreUri = context.environment.hiveMetastoreUri,
                    database = "hms_demo_$runId",
                    table = "events_hms",
                )
            },
            SparkScenarioCheck { context ->
                assertHiveExternalParquetTable(
                    spark = context.spark,
                    hiveMetastoreUri = context.environment.hiveMetastoreUri,
                    database = "hive_s3_demo_$runId",
                    table = "events_parquet_s3",
                    location = "s3a://${context.environment.s3Bucket}/hive-parquet/events_parquet_s3",
                    storageName = "hive-s3-parquet",
                )
            },
            SparkScenarioCheck { context ->
                assertHiveExternalParquetTable(
                    spark = context.spark,
                    hiveMetastoreUri = context.environment.hiveMetastoreUri,
                    database = "hive_gcs_demo_$runId",
                    table = "events_parquet_gcs",
                    location = context.environment.gcsIcebergDataPath,
                    storageName = "gcs",
                    seedData = false,
                )
            },
        )

    protected open fun kafkaAvroTopic(): String = "spark-avro-events"

    protected open fun configureSpark(
        builder: SparkSession.Builder,
        environment: SparkScenarioEnvironment,
    ): SparkSession.Builder = builder

    private fun createEnvironment(kit: BigDataTestKit, extensions: BigDataExtensionResult): SparkScenarioEnvironment {
        val hdfs = kit.endpoint(BigDataService.HDFS)
        val hiveMetastore = kit.endpoint(BigDataService.HIVE_METASTORE)
        val kafka = kit.endpoint(BigDataService.KAFKA)
        val s3 = kit.endpoint(BigDataService.LOCALSTACK_S3)
        val gcs = kit.endpoint(BigDataService.FAKE_GCS)
        val gcsBucket = extensions.required("$gcsBucketExtensionId.bucket")
        return SparkScenarioEnvironment(
            runId = runId,
            hdfsUri = hdfs.property("fs.defaultFS"),
            hiveMetastoreUri = hiveMetastore.property("hive.metastore.uris"),
            kafkaBootstrapServers = kafka.property("bootstrap.servers"),
            s3Endpoint = s3.property("aws.endpoint-url.s3"),
            s3Bucket = extensions.required("$s3BucketExtensionId.bucket"),
            gcsEndpoint = gcs.property("google.cloud.storage.host"),
            gcsBucket = gcsBucket,
            gcsIcebergDataPath = "${extensions.required("$gcsBucketExtensionId.gs.uri")}/data/demo_$runId/events_gcs",
            s3CredentialProviderPath = extensions.required("s3-jceks.credential-provider.path"),
            s3CredentialProviderHdfsPath = extensions.required("s3-jceks.hdfs.path"),
            kerberosClientPrincipal = extensions.optional("kerberos-material.client.principal"),
            kerberosClientPassword = extensions.optional("kerberos-material.client.password"),
            kerberosClientKeytab = extensions.optional("kerberos-material.client.keytab"),
            krb5Conf = extensions.optional("kerberos-material.krb5-conf")
                ?: kafka.properties["java.security.krb5.conf.local"],
            kafkaSecurityProtocol = kafka.properties["security.protocol"],
            kafkaKerberosServiceName = extensions.optional("kerberos-material.kafka.service-name")
                ?: kafka.properties["sasl.kerberos.service.name"],
            kafkaJaasConfig = kafka.properties["sasl.jaas.config"],
        )
    }

    private fun createSparkSession(environment: SparkScenarioEnvironment): SparkSession {
        environment.krb5Conf?.let { System.setProperty("java.security.krb5.conf", it) }
        return configureSpark(
            SparkSession.builder()
                .appName("bigdata-test-spark-example")
                .master("local[2]")
                .config("spark.ui.enabled", "false")
                .config(
                    "spark.driver.extraJavaOptions",
                    "--add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/java.net=ALL-UNNAMED"
                )
                .config(
                    "spark.executor.extraJavaOptions",
                    "--add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/java.net=ALL-UNNAMED"
                )
                .config("spark.sql.extensions", "org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions")
                .config("spark.sql.catalog.s3", "org.apache.iceberg.spark.SparkCatalog")
                .config("spark.sql.catalog.s3.type", "hadoop")
                .config("spark.sql.catalog.s3.warehouse", "s3a://${environment.s3Bucket}/warehouse")
                .config("spark.sql.catalog.gcs_local", "org.apache.iceberg.spark.SparkCatalog")
                .config("spark.sql.catalog.gcs_local.type", "hadoop")
                .config(
                    "spark.sql.catalog.gcs_local.warehouse",
                    "file:${Files.createTempDirectory("bigdata-test-gcs-iceberg-warehouse-")}"
                )
                .config("spark.sql.catalog.hms", "org.apache.iceberg.spark.SparkCatalog")
                .config("spark.sql.catalog.hms.type", "hive")
                .config("spark.sql.catalog.hms.uri", environment.hiveMetastoreUri)
                .config(
                    "spark.sql.catalog.hms.warehouse",
                    "file:${Files.createTempDirectory("bigdata-test-hms-warehouse-")}"
                )
                .config("spark.sql.warehouse.dir", "file:${Files.createTempDirectory("bigdata-test-spark-warehouse-")}")
                .config("spark.sql.statistics.size.autoUpdate.enabled", "false")
                .config("hive.metastore.uris", environment.hiveMetastoreUri)
                .config("spark.hadoop.fs.defaultFS", environment.hdfsUri)
                .config("spark.hadoop.hadoop.security.credential.provider.path", environment.s3CredentialProviderPath)
                .config("spark.hadoop.fs.s3a.endpoint", environment.s3Endpoint)
                .config(
                    "spark.hadoop.fs.s3a.aws.credentials.provider",
                    "org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider"
                )
                .config("spark.hadoop.fs.s3a.access.key", "test")
                .config("spark.hadoop.fs.s3a.secret.key", "test")
                .config("spark.hadoop.fs.s3a.path.style.access", "true")
                .config("spark.hadoop.fs.s3a.connection.ssl.enabled", "false")
                .config("spark.hadoop.fs.gs.impl", "com.google.cloud.hadoop.fs.gcs.GoogleHadoopFileSystem")
                .config("spark.hadoop.fs.AbstractFileSystem.gs.impl", "com.google.cloud.hadoop.fs.gcs.GoogleHadoopFS")
                .config("spark.hadoop.fs.gs.project.id", "bigdata-test")
                .config("spark.hadoop.fs.gs.storage.root.url", "${environment.gcsEndpoint.trimEnd('/')}/")
                .config("spark.hadoop.fs.gs.storage.service.path", "storage/v1/")
                .config("spark.hadoop.fs.gs.client.type", "HTTP_API_CLIENT")
                .config("spark.hadoop.fs.gs.http.connect-timeout", "4000")
                .config("spark.hadoop.fs.gs.auth.type", "UNAUTHENTICATED")
                .config("spark.hadoop.fs.gs.status.parallel.enable", "false")
                .config("spark.hadoop.fs.gs.create.items.conflict.check.enable", "false")
                .config("spark.hadoop.fs.gs.implicit.dir.repair.enable", "false")
                .config("spark.hadoop.fs.gs.hierarchical.namespace.folders.enable", "false")
                .configureKerberos(environment),
            environment,
        ).enableHiveSupport()
            .getOrCreate()
    }

    private fun SparkSession.Builder.configureKerberos(environment: SparkScenarioEnvironment): SparkSession.Builder {
        environment.krb5Conf?.let { config("spark.hadoop.java.security.krb5.conf", it) }
        environment.kerberosClientPrincipal?.let {
            config("spark.hadoop.bigdata.test.kerberos.client.principal", it)
        }
        environment.kerberosClientKeytab?.let {
            config("spark.hadoop.bigdata.test.kerberos.client.keytab", it)
        }
        environment.kafkaKerberosServiceName?.let {
            config("spark.hadoop.bigdata.test.kafka.service.name", it)
        }
        environment.kafkaJaasConfig?.let {
            config("spark.hadoop.bigdata.test.kafka.jaas.config", it)
        }
        return this
    }

    protected fun assertHdfsConfigStore(spark: SparkSession, hdfsUri: String, hdfsPath: String) {
        val exists =
            org.apache.hadoop.fs.FileSystem.get(URI.create(hdfsUri), spark.sparkContext().hadoopConfiguration())
                .use { fs -> fs.exists(org.apache.hadoop.fs.Path(hdfsPath)) }
        check(exists) { "Expected S3 JCEKS file in HDFS for ${spark.sparkContext().appName()}" }
    }

    protected fun assertKafkaAvroInput(
        spark: SparkSession,
        bootstrapServers: String,
        topic: String,
        securityProtocol: String?,
        kerberosServiceName: String?,
        jaasConfig: String?,
    ) {
        val reader = spark.read()
            .format("kafka")
            .option("kafka.bootstrap.servers", bootstrapServers)
            .option("subscribe", topic)
            .option("startingOffsets", "earliest")
            .option("endingOffsets", "latest")

        if (securityProtocol == "SASL_PLAINTEXT") {
            reader
                .option("kafka.security.protocol", securityProtocol)
                .option("kafka.sasl.mechanism", "GSSAPI")
                .option("kafka.sasl.kerberos.service.name", kerberosServiceName ?: "kafka")
                .option("kafka.sasl.jaas.config", jaasConfig ?: "")
        }

        val rows = reader.load()
        check(rows.count() == 2L) { "Expected two Avro Kafka records in $topic" }
    }

    protected fun assertIcebergTable(
        spark: SparkSession,
        catalog: String,
        namespace: String,
        table: String,
        storageName: String,
        dataPath: String? = null,
    ) {
        val identifier = "$catalog.$namespace.$table"
        spark.sql("CREATE NAMESPACE IF NOT EXISTS $catalog.$namespace")
        val tableProperties = icebergTableProperties(
            "write.data.path" to dataPath,
        )
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

    protected fun assertHiveMetastoreTable(hiveMetastoreUri: String, database: String, table: String) {
        val conf = HiveConf()
        conf.setVar(HiveConf.ConfVars.METASTOREURIS, hiveMetastoreUri)
        val client = hiveMetastoreClient(conf)
        try {
            check(client.getAllDatabases().contains(database)) { "Expected HMS database $database" }
            val hmsTable = client.getTable(database, table)
            check(hmsTable.dbName == database) { "Expected HMS table $database.$table, got ${hmsTable.dbName}.${hmsTable.tableName}" }
            check(hmsTable.tableName == table) { "Expected HMS table $database.$table, got ${hmsTable.dbName}.${hmsTable.tableName}" }
            check(hmsTable.parameters["table_type"] == "ICEBERG") {
                "Expected HMS table $database.$table to be an Iceberg table, parameters=${hmsTable.parameters}"
            }
            check(hmsTable.parameters.containsKey("metadata_location")) {
                "Expected HMS table $database.$table to contain Iceberg metadata_location"
            }
        } finally {
            client.close()
        }
    }

    protected fun assertHiveExternalParquetTable(
        spark: SparkSession,
        hiveMetastoreUri: String,
        database: String,
        table: String,
        location: String,
        storageName: String,
        seedData: Boolean = true,
    ) {
        val identifier = "$database.$table"
        spark.sql("CREATE DATABASE IF NOT EXISTS $database")
        if (seedData) {
            spark.sql(
                """
                SELECT 1 AS id, 'alpha' AS name, '$storageName' AS storage
                UNION ALL
                SELECT 2 AS id, 'beta' AS name, '$storageName' AS storage
                """.trimIndent(),
            ).write().mode("overwrite").parquet(location)
        }
        spark.sql(
            """
            CREATE EXTERNAL TABLE $identifier (
                id INT,
                name STRING,
                storage STRING
            ) STORED AS PARQUET
            LOCATION '$location'
            """.trimIndent(),
        )
        val count = spark.table(identifier).where("storage = '$storageName'").count()
        check(count == 2L) { "Expected two Hive external Parquet rows in $identifier" }
        assertParquetFiles(spark, location)
        assertHiveMetastoreExternalParquetTable(hiveMetastoreUri, database, table, location)
    }

    private fun assertParquetFiles(spark: SparkSession, location: String) {
        val files =
            org.apache.hadoop.fs.FileSystem.get(URI.create(location), spark.sparkContext().hadoopConfiguration())
                .use { fs -> fs.listStatus(org.apache.hadoop.fs.Path(location)).map { it.path.name } }
        check(files.any { it.endsWith(".parquet") }) { "Expected Parquet files under $location, got $files" }
    }

    private fun assertHiveMetastoreExternalParquetTable(
        hiveMetastoreUri: String,
        database: String,
        table: String,
        location: String
    ) {
        val conf = HiveConf()
        conf.setVar(HiveConf.ConfVars.METASTOREURIS, hiveMetastoreUri)
        val client = hiveMetastoreClient(conf)
        try {
            val hmsTable = client.getTable(database, table)
            check(hmsTable.dbName == database) { "Expected HMS table $database.$table, got ${hmsTable.dbName}.${hmsTable.tableName}" }
            check(hmsTable.tableName == table) { "Expected HMS table $database.$table, got ${hmsTable.dbName}.${hmsTable.tableName}" }
            check(hmsTable.sd.location == location) {
                "Expected HMS table $database.$table location $location, got ${hmsTable.sd.location}"
            }
            check(hmsTable.sd.inputFormat.contains("Parquet")) {
                "Expected HMS table $database.$table to be Parquet, inputFormat=${hmsTable.sd.inputFormat}"
            }
        } finally {
            client.close()
        }
    }

    private fun hiveMetastoreClient(conf: HiveConf): IMetaStoreClient {
        val clientClass = HiveMetaStoreClient::class.java
        val constructor = sequenceOf(HiveConf::class.java, org.apache.hadoop.conf.Configuration::class.java)
            .mapNotNull { parameterType ->
                runCatching { clientClass.getConstructor(parameterType) }.getOrNull()
            }
            .firstOrNull()
            ?: error("No supported HiveMetaStoreClient constructor found")
        return constructor.newInstance(conf) as IMetaStoreClient
    }

    private fun icebergTableProperties(vararg properties: Pair<String, String?>): String {
        val entries = properties.mapNotNull { (key, value) ->
            value?.let { "'$key'='$it'" }
        }
        return if (entries.isEmpty()) {
            ""
        } else {
            " TBLPROPERTIES (${entries.joinToString(", ")})"
        }
    }
}

fun interface SparkScenarioCheck {
    fun verify(context: SparkScenarioContext)
}

data class SparkScenarioContext(
    val environment: SparkScenarioEnvironment,
    val spark: SparkSession,
)

data class SparkScenarioEnvironment(
    val runId: String,
    val hdfsUri: String,
    val hiveMetastoreUri: String,
    val kafkaBootstrapServers: String,
    val s3Endpoint: String,
    val s3Bucket: String,
    val gcsEndpoint: String,
    val gcsBucket: String,
    val gcsIcebergDataPath: String,
    val s3CredentialProviderPath: String,
    val s3CredentialProviderHdfsPath: String,
    val kerberosClientPrincipal: String?,
    val kerberosClientPassword: String?,
    val kerberosClientKeytab: String?,
    val krb5Conf: String?,
    val kafkaSecurityProtocol: String?,
    val kafkaKerberosServiceName: String?,
    val kafkaJaasConfig: String?,
)
