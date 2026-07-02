package org.openprojectx.bigdata.test.extensions.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.openprojectx.bigdata.test.extensions.core.BigDataExtension
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionProvider
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionResourceLoader
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
import org.openprojectx.bigdata.test.extensions.spark.SparkSqlPreparationExtensionProvider
import io.ous.jtoml.JToml
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.Date
import java.util.ServiceLoader

class BigDataExtensionsConfigLoader(
    private val resources: BigDataExtensionResourceLoader = BigDataExtensionResourceLoader(),
    providers: Iterable<BigDataExtensionProvider> = ServiceLoader.load(BigDataExtensionProvider::class.java),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val providers = (builtInProviders + providers).associateBy { it.type }

    fun load(locations: Iterable<String>): List<BigDataExtension> =
        locations.flatMap { load(it) }

    fun load(location: String): List<BigDataExtension> {
        val root = TomlConfigParser.parse(resources.readText(location))
        val extensions = mutableListOf<BigDataExtension>()
        root["s3Jceks"]?.jsonObject?.let { config ->
            if (config.boolean("enabled", default = true)) {
                extensions += S3JceksExtension(
                    id = config.string("id", "s3-jceks"),
                    hdfsDir = config.string("hdfsDir", "/bigdata-test/config"),
                    fileName = config.string("fileName", "s3.jceks"),
                    accessKeyAlias = config.string("accessKeyAlias", "fs.s3a.access.key"),
                    secretKeyAlias = config.string("secretKeyAlias", "fs.s3a.secret.key"),
                )
            }
        }
        root["kafkaAvro"]?.jsonObject?.let { config ->
            if (config.boolean("enabled", default = true)) {
                extensions += KafkaAvroSeedExtension(
                    id = config.string("id", "kafka-avro-seed"),
                    topics = config["topics"]?.jsonArray?.map { it.jsonObject.toKafkaAvroTopic() }.orEmpty(),
                )
            }
        }
        root["kerberosMaterial"]?.jsonObject?.let { config ->
            if (config.boolean("enabled", default = true)) {
                extensions += KerberosMaterialExtension(
                    id = config.string("id", "kerberos-material"),
                )
            }
        }
        root["s3Buckets"]?.jsonArray?.forEach { item ->
            val config = item.jsonObject
            if (config.boolean("enabled", default = true)) {
                val bucket = config.string("bucket")
                extensions += S3BucketExtension(
                    id = config.string("id", "s3-bucket-$bucket"),
                    bucket = bucket,
                )
            }
        }
        root["gcsBuckets"]?.jsonArray?.forEach { item ->
            val config = item.jsonObject
            if (config.boolean("enabled", default = true)) {
                val bucket = config.string("bucket")
                extensions += GcsBucketExtension(
                    id = config.string("id", "gcs-bucket-$bucket"),
                    bucket = bucket,
                    project = config.string("project", "bigdata-test"),
                )
            }
        }
        root["s3Uploads"]?.jsonArray?.forEach { item ->
            val config = item.jsonObject
            if (config.boolean("enabled", default = true)) {
                val bucket = config.string("bucket")
                extensions += S3UploadExtension(
                    id = config.string("id", "s3-upload-$bucket"),
                    bucket = bucket,
                    prefix = config.string("prefix", ""),
                    createBucket = config.boolean("createBucket", default = true),
                    sources = config.uploadSources(),
                )
            }
        }
        root["gcsUploads"]?.jsonArray?.forEach { item ->
            val config = item.jsonObject
            if (config.boolean("enabled", default = true)) {
                val bucket = config.string("bucket")
                extensions += GcsUploadExtension(
                    id = config.string("id", "gcs-upload-$bucket"),
                    bucket = bucket,
                    prefix = config.string("prefix", ""),
                    project = config.string("project", "bigdata-test"),
                    createBucket = config.boolean("createBucket", default = true),
                    sources = config.uploadSources(),
                )
            }
        }
        root["extensions"]?.jsonArray?.forEach { item ->
            val config = item.jsonObject
            val type = config.string("type")
            val provider = providers[type] ?: error("Unknown bigdata-test extension type '$type'")
            extensions += provider.create(config, resources)
        }
        return extensions
    }

    private fun JsonObject.toKafkaAvroTopic(): KafkaAvroTopicSeed {
        val inlineRecords = this["records"] as? JsonArray
        val resourceRecords = this["recordsResource"]?.jsonPrimitive?.contentOrNull
            ?.let { json.parseToJsonElement(resources.readText(it)).jsonArray }
        val records = (inlineRecords ?: resourceRecords ?: JsonArray(emptyList()))
            .map { record ->
                val obj = record.jsonObject
                KafkaAvroRecordSeed(
                    key = obj.string("key"),
                    value = obj["value"]?.jsonObject ?: error("Kafka Avro record requires object field 'value'"),
                )
            }
        return KafkaAvroTopicSeed(
            name = string("name"),
            schema = string("schema"),
            records = records,
            partitions = int("partitions", 1),
            replicationFactor = int("replicationFactor", 1).toShort(),
        )
    }

    private fun JsonObject.uploadSources(): List<ObjectStoreUploadSource> =
        this["sources"]?.jsonArray?.map { item ->
            val source = item.jsonObject
            ObjectStoreUploadSource(
                source = source.string("source"),
                key = source.optionalString("key"),
                prefix = source.string("prefix", ""),
                recursive = source.boolean("recursive", default = true),
                contentType = source.optionalString("contentType"),
            )
        }.orEmpty()

    private fun JsonObject.string(name: String): String =
        this[name]?.jsonPrimitive?.contentOrNull ?: error("Missing required extension config field '$name'")

    private fun JsonObject.string(name: String, default: String): String =
        this[name]?.jsonPrimitive?.contentOrNull ?: default

    private fun JsonObject.optionalString(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.int(name: String, default: Int): Int =
        this[name]?.jsonPrimitive?.intOrNull ?: default

    private fun JsonObject.boolean(name: String, default: Boolean): Boolean =
        this[name]?.jsonPrimitive?.booleanOrNull ?: default

    private companion object {
        val builtInProviders = listOf<BigDataExtensionProvider>(
            SparkSqlPreparationExtensionProvider,
        )
    }
}

private object TomlConfigParser {
    private val trailingArrayComma = Regex(",\\s*]")

    fun parse(text: String): JsonObject {
        val result = JToml.parseString(text.replace(trailingArrayComma, "\n]"))
        return result.toJsonObject()
    }

    private fun Map<*, *>.toJsonObject(): JsonObject =
        JsonObject(entries.associate { (key, value) ->
            require(key is String) { "Unsupported TOML key type: ${key?.javaClass}" }
            key to value.toJsonElement()
        })

    private fun Iterable<*>.toJsonArray(): JsonArray =
        JsonArray(map { value -> value.toJsonElement() })

    private fun Any?.toJsonElement(): JsonElement =
        when (this) {
            null -> JsonPrimitive("null")
            is String -> JsonPrimitive(this)
            is Boolean -> JsonPrimitive(this)
            is Int -> JsonPrimitive(this)
            is Long -> JsonPrimitive(this)
            is Float -> JsonPrimitive(this)
            is Double -> JsonPrimitive(this)
            is BigDecimal -> JsonPrimitive(this)
            is Map<*, *> -> toJsonObject()
            is Iterable<*> -> toJsonArray()
            is OffsetDateTime,
            is LocalDateTime,
            is LocalDate,
            is LocalTime,
            is Date,
            -> JsonPrimitive(toString())
            else -> error("Unsupported TOML value type: ${this::class}")
        }
}
