package org.openprojectx.bigdata.test.extensions.kafka

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.openprojectx.bigdata.test.core.BigDataService
import org.openprojectx.bigdata.test.extensions.core.BigDataExtension
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionContext
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionEvent

data class KafkaAvroSeedExtension(
    override val id: String = "kafka-avro-seed",
    val topics: List<KafkaAvroTopicSeed>,
) : BigDataExtension {
    override val requiredServices: Set<BigDataService> = setOf(BigDataService.KAFKA)
    override val events: Set<BigDataExtensionEvent> = setOf(BigDataExtensionEvent.AFTER_KIT_START)

    override fun onEvent(event: BigDataExtensionEvent, context: BigDataExtensionContext) {
        val kafka = context.endpoint(BigDataService.KAFKA)
        val schemaRegistry = context.kit.endpoints()[BigDataService.SCHEMA_REGISTRY]
        kafka.properties["java.security.krb5.conf.local"]?.let { krb5Conf ->
            System.setProperty("java.security.krb5.conf", krb5Conf)
        }
        topics.forEach { topic ->
            KafkaAvroProducers.produce(
                bootstrapServers = kafka.property("bootstrap.servers"),
                schemaRegistryUrl = schemaRegistry?.property("schema.registry.url"),
                topic = topic.name,
                schemaJson = context.resources.readText(topic.schema),
                records = topic.records.map { AvroKafkaRecord(it.key, it.value.toAnyMap()) },
                partitions = topic.partitions,
                replicationFactor = topic.replicationFactor,
                clientProperties = kafka.clientProperties(),
            )
            context.putOutput("$id.${topic.name}.records", topic.records.size.toString())
        }
    }

    private fun JsonObject.toAnyMap(): Map<String, Any?> = mapValues { (_, value) -> value.toAnyValue() }

    private fun org.openprojectx.bigdata.test.core.BigDataEndpoint.clientProperties(): Map<String, String> =
        listOf(
            "security.protocol",
            "sasl.mechanism",
            "sasl.kerberos.service.name",
            "sasl.jaas.config",
        ).mapNotNull { key ->
            properties[key]?.let { key to it }
        }.toMap()

    private fun JsonElement.toAnyValue(): Any? = when (this) {
        is JsonObject -> toAnyMap()
        is kotlinx.serialization.json.JsonArray -> map { it.toAnyValue() }
        is JsonPrimitive -> when {
            isString -> content
            content == "null" -> null
            content.equals("true", ignoreCase = true) -> true
            content.equals("false", ignoreCase = true) -> false
            content.toIntOrNull() != null -> content.toInt()
            content.toLongOrNull() != null -> content.toLong()
            content.toDoubleOrNull() != null -> content.toDouble()
            else -> content
        }
    }
}

data class KafkaAvroTopicSeed(
    val name: String,
    val schema: String,
    val records: List<KafkaAvroRecordSeed>,
    val partitions: Int = 1,
    val replicationFactor: Short = 1,
)

data class KafkaAvroRecordSeed(
    val key: String,
    val value: JsonObject,
)
