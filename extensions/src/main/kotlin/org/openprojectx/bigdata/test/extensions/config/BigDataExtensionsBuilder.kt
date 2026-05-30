package org.openprojectx.bigdata.test.extensions.config

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.openprojectx.bigdata.test.extensions.core.BigDataExtension
import org.openprojectx.bigdata.test.extensions.hadoop.S3JceksExtension
import org.openprojectx.bigdata.test.extensions.kafka.KafkaAvroRecordSeed
import org.openprojectx.bigdata.test.extensions.kafka.KafkaAvroSeedExtension
import org.openprojectx.bigdata.test.extensions.kafka.KafkaAvroTopicSeed
import org.openprojectx.bigdata.test.extensions.objectstore.GcsBucketExtension
import org.openprojectx.bigdata.test.extensions.objectstore.S3BucketExtension
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

    internal fun build(): List<BigDataExtension> = extensions.toList()
}

class S3JceksBuilder {
    var id: String = "s3-jceks"
    var hdfsDir: String = "/bigdata-test/config"
    var fileName: String = "s3.jceks"
    var accessKeyAlias: String = "fs.s3a.access.key"
    var secretKeyAlias: String = "fs.s3a.secret.key"

    internal fun build(): S3JceksExtension =
        S3JceksExtension(
            id = id,
            hdfsDir = hdfsDir,
            fileName = fileName,
            accessKeyAlias = accessKeyAlias,
            secretKeyAlias = secretKeyAlias,
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
