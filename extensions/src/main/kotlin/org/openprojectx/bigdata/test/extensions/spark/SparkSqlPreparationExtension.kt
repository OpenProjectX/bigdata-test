package org.openprojectx.bigdata.test.extensions.spark

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.security.UserGroupInformation
import org.apache.spark.sql.SparkSession
import org.openprojectx.bigdata.test.core.BigDataService
import org.openprojectx.bigdata.test.extensions.core.BigDataExtension
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionContext
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionEvent
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionProvider
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionResourceLoader

class SparkSqlPreparationExtension(
    override val id: String = "spark-sql-prep",
    private val appName: String = "bigdata-test-spark-sql-prep",
    private val master: String = "local[2]",
    private val enableHiveSupport: Boolean = true,
    private val stopAfterRun: Boolean = true,
    private val clearSparkSessions: Boolean = true,
    private val closeHadoopFileSystems: Boolean = true,
    private val useKitEndpoints: Boolean = true,
    private val configs: Map<String, String> = emptyMap(),
    private val sql: List<SparkSqlPreparationStatement> = emptyList(),
) : BigDataExtension {
    override val events: Set<BigDataExtensionEvent> = setOf(BigDataExtensionEvent.AFTER_KIT_START)

    override fun onEvent(event: BigDataExtensionEvent, context: BigDataExtensionContext) {
        if (event !in events) return
        val previousKrb5Conf = System.getProperty("java.security.krb5.conf")
        var spark: SparkSession? = null
        try {
            val builder = SparkSession.builder()
                .appName(appName)
                .master(master)
                .config("spark.ui.enabled", "false")
                .config(
                    "spark.executor.extraJavaOptions",
                    "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED " +
                        "--add-opens=java.base/java.nio=ALL-UNNAMED " +
                        "--add-opens=java.base/java.net=ALL-UNNAMED",
                )
            if (useKitEndpoints) {
                configureFromKit(builder, context)
            }
            configs.forEach { (key, value) -> builder.config(key, value) }
            spark = if (enableHiveSupport) builder.enableHiveSupport().getOrCreate() else builder.getOrCreate()
            loginFromKeytabIfNeeded(spark, context)

            var count = 0
            sql.forEach { statement ->
                val text = statement.resolve(context.resources)
                splitSql(text).forEach { executable ->
                    spark.sql(executable).collect()
                    count++
                }
            }
            context.putOutput("$id.engine", "in-process")
            context.putOutput("$id.executed-statements", count.toString())
        } finally {
            if (stopAfterRun) {
                spark?.stop()
            }
            if (clearSparkSessions) {
                SparkSession.clearActiveSession()
                SparkSession.clearDefaultSession()
            }
            if (closeHadoopFileSystems) {
                FileSystem.closeAll()
            }
            restoreSystemProperty("java.security.krb5.conf", previousKrb5Conf)
        }
    }

    private fun configureFromKit(builder: SparkSession.Builder, context: BigDataExtensionContext) {
        context.kit.endpoints()[BigDataService.HIVE_METASTORE]?.let { endpoint ->
            endpoint.properties["hive.metastore.uris"]?.let { uri ->
                builder.config("hive.metastore.uris", uri)
            }
            endpoint.properties.forEach { (key, value) ->
                if (key.startsWith("hive.metastore.")) {
                    builder.config(key, value)
                    builder.config("spark.hadoop.$key", value)
                }
            }
        }
        context.kit.endpoints()[BigDataService.HDFS]?.let { endpoint ->
            endpoint.properties["fs.defaultFS"]?.let { builder.config("spark.hadoop.fs.defaultFS", it) }
            endpoint.properties["dfs.client.use.datanode.hostname"]?.let {
                builder.config("spark.hadoop.dfs.client.use.datanode.hostname", it)
            }
            endpoint.properties.forEach { (key, value) ->
                if (key == "hadoop.security.authentication" || key.startsWith("dfs.")) {
                    builder.config("spark.hadoop.$key", value)
                }
            }
        }
        context.kit.endpoints()[BigDataService.S3]?.let { endpoint ->
            endpoint.properties["aws.endpoint-url.s3"]?.let { builder.config("spark.hadoop.fs.s3a.endpoint", it) }
            endpoint.properties["aws.accessKeyId"]?.let { builder.config("spark.hadoop.fs.s3a.access.key", it) }
            endpoint.properties["aws.secretAccessKey"]?.let { builder.config("spark.hadoop.fs.s3a.secret.key", it) }
            endpoint.properties["aws.region"]?.let { builder.config("spark.hadoop.fs.s3a.endpoint.region", it) }
            builder.config("spark.hadoop.fs.s3a.path.style.access", "true")
            builder.config("spark.hadoop.fs.s3a.connection.ssl.enabled", "false")
            builder.config(
                "spark.hadoop.fs.s3a.aws.credentials.provider",
                "org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider",
            )
        }
        context.kit.endpoints()[BigDataService.FAKE_GCS]?.let { endpoint ->
            endpoint.properties["google.cloud.storage.host"]?.trimEnd('/')?.let { url ->
                builder.config("spark.hadoop.fs.gs.storage.root.url", "$url/")
            }
            builder.config("spark.hadoop.fs.gs.impl", "com.google.cloud.hadoop.fs.gcs.GoogleHadoopFileSystem")
            builder.config("spark.hadoop.fs.AbstractFileSystem.gs.impl", "com.google.cloud.hadoop.fs.gcs.GoogleHadoopFS")
            builder.config("spark.hadoop.fs.gs.project.id", "bigdata-test")
            builder.config("spark.hadoop.fs.gs.storage.service.path", "storage/v1/")
            builder.config("spark.hadoop.fs.gs.client.type", "HTTP_API_CLIENT")
            builder.config("spark.hadoop.fs.gs.auth.type", "UNAUTHENTICATED")
            builder.config("spark.hadoop.fs.gs.status.parallel.enable", "false")
            builder.config("spark.hadoop.fs.gs.create.items.conflict.check.enable", "false")
            builder.config("spark.hadoop.fs.gs.implicit.dir.repair.enable", "false")
            builder.config("spark.hadoop.fs.gs.hierarchical.namespace.folders.enable", "false")
        }
        context.kit.endpoints()[BigDataService.KERBEROS]?.let { endpoint ->
            endpoint.properties["bigdata.test.kerberos.krb5-conf"]?.let {
                System.setProperty("java.security.krb5.conf", it)
                builder.config("spark.hadoop.java.security.krb5.conf", it)
            }
            endpoint.properties["bigdata.test.kerberos.client-principal"]?.let {
                builder.config("spark.hadoop.bigdata.test.kerberos.client.principal", it)
            }
            endpoint.properties["bigdata.test.kerberos.client-keytab"]?.let {
                builder.config("spark.hadoop.bigdata.test.kerberos.client.keytab", it)
            }
        }
    }

    private fun splitSql(text: String): List<String> =
        text.splitToSequence(';')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()

    private fun restoreSystemProperty(name: String, previous: String?) {
        if (previous == null) {
            System.clearProperty(name)
        } else {
            System.setProperty(name, previous)
        }
    }

    private fun loginFromKeytabIfNeeded(spark: SparkSession, context: BigDataExtensionContext) {
        val kerberos = context.kit.endpoints()[BigDataService.KERBEROS]?.properties ?: return
        val principal = kerberos["bigdata.test.kerberos.client-principal"] ?: return
        val keytab = kerberos["bigdata.test.kerberos.client-keytab"] ?: return
        val conf = spark.sparkContext().hadoopConfiguration()
        UserGroupInformation.setConfiguration(conf)
        UserGroupInformation.loginUserFromKeytab(principal, keytab)
    }
}

data class SparkSqlPreparationStatement(
    val statement: String? = null,
    val resource: String? = null,
) {
    fun resolve(resources: BigDataExtensionResourceLoader): String =
        statement ?: resource?.let(resources::readText) ?: ""
}

object SparkSqlPreparationExtensionProvider : BigDataExtensionProvider {
    override val type: String = "spark-sql-prep"

    override fun create(config: JsonObject, resources: BigDataExtensionResourceLoader): BigDataExtension =
        SparkSqlPreparationExtension(
            id = config.string("id", "spark-sql-prep"),
            appName = config.string("appName", "bigdata-test-spark-sql-prep"),
            master = config.string("master", "local[2]"),
            enableHiveSupport = config.boolean("enableHiveSupport", true),
            stopAfterRun = config.boolean("stopAfterRun", true),
            clearSparkSessions = config.boolean("clearSparkSessions", true),
            closeHadoopFileSystems = config.boolean("closeHadoopFileSystems", true),
            useKitEndpoints = config.boolean("useKitEndpoints", true),
            configs = config["configs"]?.jsonObject?.toStringMap().orEmpty(),
            sql = config.sqlStatements(),
        )

    private fun JsonObject.sqlStatements(): List<SparkSqlPreparationStatement> {
        val statements = (this["statements"] as? JsonArray)
            ?.jsonArray
            ?.map { SparkSqlPreparationStatement(statement = it.jsonPrimitive.contentOrNull.orEmpty()) }
            .orEmpty()
        val scripts = (this["scripts"] as? JsonArray)
            ?.jsonArray
            ?.map { SparkSqlPreparationStatement(resource = it.jsonPrimitive.contentOrNull.orEmpty()) }
            .orEmpty()
        return statements + scripts
    }

    private fun JsonObject.toStringMap(): Map<String, String> =
        mapValues { (_, value) -> value.jsonPrimitive.contentOrNull.orEmpty() }

    private fun JsonObject.string(name: String, default: String): String =
        this[name]?.jsonPrimitive?.contentOrNull ?: default

    private fun JsonObject.boolean(name: String, default: Boolean): Boolean =
        this[name]?.jsonPrimitive?.booleanOrNull ?: default
}
