package org.openprojectx.bigdata.test.extensions.kafka

import io.confluent.kafka.serializers.KafkaAvroSerializer
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord
import org.apache.avro.io.EncoderFactory
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.errors.TopicExistsException
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import java.io.ByteArrayOutputStream
import java.util.Properties

data class AvroKafkaRecord(
    val key: String,
    val values: Map<String, Any?>,
)

object KafkaAvroProducers {
    fun produce(
        bootstrapServers: String,
        schemaRegistryUrl: String?,
        topic: String,
        schemaJson: String,
        records: Iterable<AvroKafkaRecord>,
        partitions: Int = 1,
        replicationFactor: Short = 1,
        clientProperties: Map<String, String> = emptyMap(),
    ) {
        createTopic(bootstrapServers, topic, partitions, replicationFactor, clientProperties)

        val schema = Schema.Parser().parse(schemaJson)
        if (schemaRegistryUrl == null) {
            val props = producerProperties<String, ByteArray>(
                bootstrapServers = bootstrapServers,
                valueSerializer = ByteArraySerializer::class.java.name,
                clientProperties = clientProperties,
            )
            KafkaProducer<String, ByteArray>(props).use { producer ->
                records.forEach { record ->
                    producer.send(ProducerRecord(topic, record.key, record.toAvroBytes(schema))).get()
                }
                producer.flush()
            }
        } else {
            val props = producerProperties<String, GenericRecord>(
                bootstrapServers = bootstrapServers,
                valueSerializer = KafkaAvroSerializer::class.java.name,
                clientProperties = clientProperties,
            ).apply {
                put("schema.registry.url", schemaRegistryUrl)
            }
            KafkaProducer<String, GenericRecord>(props).use { producer ->
                records.forEach { record ->
                    producer.send(ProducerRecord(topic, record.key, record.toGenericRecord(schema))).get()
                }
                producer.flush()
            }
        }
    }

    fun createTopic(
        bootstrapServers: String,
        topic: String,
        partitions: Int = 1,
        replicationFactor: Short = 1,
        clientProperties: Map<String, String> = emptyMap(),
    ) {
        AdminClient.create(mapOf(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers) + clientProperties).use { admin ->
            try {
                admin.createTopics(listOf(NewTopic(topic, partitions, replicationFactor))).all().get()
            } catch (ex: Exception) {
                if (ex.cause !is TopicExistsException) throw ex
            }
        }
    }

    private fun AvroKafkaRecord.toGenericRecord(schema: Schema): GenericRecord =
        GenericData.Record(schema).also { generic ->
            values.forEach { (field, value) -> generic.put(field, value) }
        }

    private fun AvroKafkaRecord.toAvroBytes(schema: Schema): ByteArray {
        val output = ByteArrayOutputStream()
        val encoder = EncoderFactory.get().binaryEncoder(output, null)
        GenericData.get().createDatumWriter(schema).write(toGenericRecord(schema), encoder)
        encoder.flush()
        return output.toByteArray()
    }

    private fun <K, V> producerProperties(
        bootstrapServers: String,
        valueSerializer: String,
        clientProperties: Map<String, String>,
    ): Properties =
        Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, valueSerializer)
            putAll(clientProperties)
        }
}
