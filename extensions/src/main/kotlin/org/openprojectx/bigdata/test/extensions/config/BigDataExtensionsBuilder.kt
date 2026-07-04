package org.openprojectx.bigdata.test.extensions.config

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.openprojectx.bigdata.test.extensions.core.BigDataExtension
import org.openprojectx.bigdata.test.extensions.hadoop.S3JceksExtension
import org.openprojectx.bigdata.test.extensions.kerberos.KerberosMaterialExtension
import org.openprojectx.bigdata.test.extensions.kafka.KafkaAvroRecordSeed
import org.openprojectx.bigdata.test.extensions.kafka.KafkaAvroSeedExtension
import org.openprojectx.bigdata.test.extensions.kafka.KafkaAvroTopicSeed
import org.openprojectx.bigdata.test.extensions.objectstore.GcsBucketExtension
import org.openprojectx.bigdata.test.extensions.objectstore.GcsUploadExtension
import org.openprojectx.bigdata.test.extensions.objectstore.ObjectStoreUploadSource
import org.openprojectx.bigdata.test.extensions.objectstore.S3BucketExtension
import org.openprojectx.bigdata.test.extensions.objectstore.S3UploadExtension
import org.openprojectx.bigdata.test.extensions.spark.SparkSqlPreparationExtension
import org.openprojectx.bigdata.test.extensions.spark.SparkSqlPreparationStatement
import java.util.function.Consumer

class BigDataExtensionsBuilder {
    private val extensions = mutableListOf<BigDataExtension>()

    fun extension(extension: BigDataExtension) {
        extensions += extension
    }

    fun s3Jceks(configure: S3JceksBuilder.() -> Unit = {}) {
        extensions += S3JceksBuilder().apply(configure).build()
    }

    fun s3Jceks(configure: Consumer<S3JceksBuilder>) {
        extensions += S3JceksBuilder().also { configure.accept(it) }.build()
    }

    fun kafkaAvro(configure: KafkaAvroBuilder.() -> Unit) {
        extensions += KafkaAvroBuilder().apply(configure).build()
    }

    fun kafkaAvro(configure: Consumer<KafkaAvroBuilder>) {
        extensions += KafkaAvroBuilder().also { configure.accept(it) }.build()
    }

    fun kerberosMaterial(id: String = "kerberos-material") {
        extensions += KerberosMaterialBuilder().apply { this.id = id }.build()
    }

    fun kerberosMaterial() {
        kerberosMaterial(id = "kerberos-material")
    }

    fun kerberosMaterial(configure: KerberosMaterialBuilder.() -> Unit) {
        extensions += KerberosMaterialBuilder().apply(configure).build()
    }

    fun kerberosMaterial(configure: Consumer<KerberosMaterialBuilder>) {
        extensions += KerberosMaterialBuilder().also { configure.accept(it) }.build()
    }

    fun s3Bucket(bucket: String, id: String = "s3-bucket-$bucket") {
        extensions += S3BucketExtension(id = id, bucket = bucket)
    }

    fun s3Bucket(bucket: String) {
        s3Bucket(bucket = bucket, id = "s3-bucket-$bucket")
    }

    fun gcsBucket(bucket: String, id: String = "gcs-bucket-$bucket", project: String = "bigdata-test") {
        extensions += GcsBucketExtension(id = id, bucket = bucket, project = project)
    }

    fun gcsBucket(bucket: String) {
        gcsBucket(bucket = bucket, id = "gcs-bucket-$bucket", project = "bigdata-test")
    }

    fun gcsBucket(bucket: String, id: String) {
        gcsBucket(bucket = bucket, id = id, project = "bigdata-test")
    }

    fun s3Upload(bucket: String, configure: ObjectStoreUploadBuilder.() -> Unit) {
        extensions += ObjectStoreUploadBuilder(bucket = bucket, id = "s3-upload-$bucket").apply(configure).buildS3()
    }

    fun s3Upload(bucket: String, configure: Consumer<ObjectStoreUploadBuilder>) {
        extensions += ObjectStoreUploadBuilder(bucket = bucket, id = "s3-upload-$bucket").also { configure.accept(it) }.buildS3()
    }

    fun gcsUpload(bucket: String, configure: GcsUploadBuilder.() -> Unit) {
        extensions += GcsUploadBuilder(bucket = bucket, id = "gcs-upload-$bucket").apply(configure).buildGcs()
    }

    fun gcsUpload(bucket: String, configure: Consumer<GcsUploadBuilder>) {
        extensions += GcsUploadBuilder(bucket = bucket, id = "gcs-upload-$bucket").also { configure.accept(it) }.buildGcs()
    }

    fun sparkSqlPreparation(configure: SparkSqlPreparationBuilder.() -> Unit) {
        extensions += SparkSqlPreparationBuilder().apply(configure).build()
    }

    fun sparkSqlPreparation(configure: Consumer<SparkSqlPreparationBuilder>) {
        extensions += SparkSqlPreparationBuilder().also { configure.accept(it) }.build()
    }

    internal fun build(): List<BigDataExtension> = extensions.toList()
}

open class ObjectStoreUploadBuilder internal constructor(
    protected val bucket: String,
    var id: String,
) {
    var prefix: String = ""
    var createBucket: Boolean = true
    protected val sources = mutableListOf<ObjectStoreUploadSource>()

    fun file(source: String, key: String? = null, contentType: String? = null) {
        sources += ObjectStoreUploadSource(source = source, key = key, contentType = contentType)
    }

    fun file(source: String) {
        file(source = source, key = null, contentType = null)
    }

    fun file(source: String, key: String) {
        file(source = source, key = key, contentType = null)
    }

    fun directory(source: String, prefix: String = "", recursive: Boolean = true, contentType: String? = null) {
        sources += ObjectStoreUploadSource(
            source = source,
            prefix = prefix,
            recursive = recursive,
            contentType = contentType,
        )
    }

    fun directory(source: String) {
        directory(source = source, prefix = "", recursive = true, contentType = null)
    }

    internal fun buildS3(): S3UploadExtension =
        S3UploadExtension(
            id = id,
            bucket = bucket,
            prefix = prefix,
            createBucket = createBucket,
            sources = sources.toList(),
        )
}

class GcsUploadBuilder internal constructor(
    bucket: String,
    id: String,
) : ObjectStoreUploadBuilder(bucket = bucket, id = id) {
    var project: String = "bigdata-test"

    internal fun buildGcs(): GcsUploadExtension =
        GcsUploadExtension(
            id = id,
            bucket = bucket,
            prefix = prefix,
            project = project,
            createBucket = createBucket,
            sources = sources.toList(),
        )
}

class S3JceksBuilder {
    var id: String = "s3-jceks"
    var hdfsDir: String = "/bigdata-test/config"
    var fileName: String = "s3.jceks"
    var accessKeyAlias: String = "fs.s3a.access.key"
    var secretKeyAlias: String = "fs.s3a.secret.key"
    private val aliases = linkedMapOf<String, String>()

    fun alias(name: String, value: String) {
        aliases[name] = value
    }

    fun aliases(values: Map<String, String>) {
        aliases += values
    }

    internal fun build(): S3JceksExtension =
        S3JceksExtension(
            id = id,
            hdfsDir = hdfsDir,
            fileName = fileName,
            accessKeyAlias = accessKeyAlias,
            secretKeyAlias = secretKeyAlias,
            aliases = S3JceksExtension.defaultAliases + aliases,
        )
}

class KerberosMaterialBuilder {
    var id: String = "kerberos-material"
    private val localClientKeytabCopyPaths = mutableListOf<String>()
    private val localKrb5ConfCopyPaths = mutableListOf<String>()

    fun localClientKeytabCopyPath(path: String) {
        localClientKeytabCopyPaths += path
    }

    fun localClientKeytabCopyPaths(paths: Iterable<String>) {
        localClientKeytabCopyPaths += paths
    }

    fun localKrb5ConfCopyPath(path: String) {
        localKrb5ConfCopyPaths += path
    }

    fun localKrb5ConfCopyPaths(paths: Iterable<String>) {
        localKrb5ConfCopyPaths += paths
    }

    internal fun build(): KerberosMaterialExtension =
        KerberosMaterialExtension(
            id = id,
            localClientKeytabCopyPaths = localClientKeytabCopyPaths.toList(),
            localKrb5ConfCopyPaths = localKrb5ConfCopyPaths.toList(),
        )
}

class KafkaAvroBuilder {
    var id: String = "kafka-avro-seed"
    private val topics = mutableListOf<KafkaAvroTopicSeed>()

    fun topic(
        name: String,
        schema: String,
        partitions: Int = 1,
        replicationFactor: Short = 1,
        configure: KafkaAvroTopicBuilder.() -> Unit,
    ) {
        topics += KafkaAvroTopicBuilder(name, schema, partitions, replicationFactor).apply(configure).build()
    }

    fun topic(
        name: String,
        schema: String,
        configure: Consumer<KafkaAvroTopicBuilder>,
    ) {
        topics += KafkaAvroTopicBuilder(name, schema, 1, 1).also { configure.accept(it) }.build()
    }

    fun topic(
        name: String,
        schema: String,
        partitions: Int,
        replicationFactor: Short,
        configure: Consumer<KafkaAvroTopicBuilder>,
    ) {
        topics += KafkaAvroTopicBuilder(name, schema, partitions, replicationFactor).also { configure.accept(it) }.build()
    }

    internal fun build(): KafkaAvroSeedExtension =
        KafkaAvroSeedExtension(id = id, topics = topics.toList())
}

class KafkaAvroTopicBuilder internal constructor(
    private val name: String,
    private val schema: String,
    private val partitions: Int,
    private val replicationFactor: Short,
) {
    private val records = mutableListOf<KafkaAvroRecordSeed>()

    fun record(key: String, value: JsonObject) {
        records += KafkaAvroRecordSeed(key = key, value = value)
    }

    fun record(key: String, value: Map<String, Any?>) {
        record(key, value.toJsonObject())
    }

    internal fun build(): KafkaAvroTopicSeed =
        KafkaAvroTopicSeed(
            name = name,
            schema = schema,
            records = records.toList(),
            partitions = partitions,
            replicationFactor = replicationFactor,
        )
}

class SparkSqlPreparationBuilder {
    var id: String = "spark-sql-prep"
    var appName: String = "bigdata-test-spark-sql-prep"
    var master: String = "local[2]"
    var enableHiveSupport: Boolean = true
    var stopAfterRun: Boolean = true
    var clearSparkSessions: Boolean = true
    var closeHadoopFileSystems: Boolean = true
    var useKitEndpoints: Boolean = true
    private val configs = linkedMapOf<String, String>()
    private val sql = mutableListOf<SparkSqlPreparationStatement>()

    fun config(key: String, value: String) {
        configs[key] = value
    }

    fun statement(sql: String) {
        this.sql += SparkSqlPreparationStatement(statement = sql)
    }

    fun script(resource: String) {
        sql += SparkSqlPreparationStatement(resource = resource)
    }

    internal fun build(): SparkSqlPreparationExtension =
        SparkSqlPreparationExtension(
            id = id,
            appName = appName,
            master = master,
            enableHiveSupport = enableHiveSupport,
            stopAfterRun = stopAfterRun,
            clearSparkSessions = clearSparkSessions,
            closeHadoopFileSystems = closeHadoopFileSystems,
            useKitEndpoints = useKitEndpoints,
            configs = configs.toMap(),
            sql = sql.toList(),
        )
}

private fun Map<String, Any?>.toJsonObject(): JsonObject =
    JsonObject(mapValues { (_, value) -> value.toJsonElement() })

private fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is String -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Int -> JsonPrimitive(this)
    is Long -> JsonPrimitive(this)
    is Float -> JsonPrimitive(this)
    is Double -> JsonPrimitive(this)
    is Map<*, *> -> JsonObject(
        entries.associate { (key, value) ->
            require(key is String) { "Kafka Avro record map keys must be strings" }
            key to value.toJsonElement()
        },
    )
    is Iterable<*> -> JsonArray(map { it.toJsonElement() })
    is Array<*> -> JsonArray(map { it.toJsonElement() })
    else -> error("Unsupported Kafka Avro record value type: ${this::class}")
}
