package org.openprojectx.bigdata.test.extensions.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
import org.openprojectx.bigdata.test.extensions.kafka.KafkaAvroRecordSeed
import org.openprojectx.bigdata.test.extensions.kafka.KafkaAvroSeedExtension
import org.openprojectx.bigdata.test.extensions.kafka.KafkaAvroTopicSeed
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
        val root = json.parseToJsonElement(resources.readText(location)).jsonObject
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

    private fun JsonObject.string(name: String): String =
        this[name]?.jsonPrimitive?.contentOrNull ?: error("Missing required extension config field '$name'")

    private fun JsonObject.string(name: String, default: String): String =
        this[name]?.jsonPrimitive?.contentOrNull ?: default

    private fun JsonObject.int(name: String, default: Int): Int =
        this[name]?.jsonPrimitive?.intOrNull ?: default

    private fun JsonObject.boolean(name: String, default: Boolean): Boolean =
        this[name]?.jsonPrimitive?.booleanOrNull ?: default

    private companion object {
        val builtInProviders = listOf<BigDataExtensionProvider>()
    }
}
