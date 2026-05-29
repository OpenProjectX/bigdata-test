package org.openprojectx.bigdata.test.extensions.kafka

import io.confluent.kafka.serializers.KafkaAvroSerializer
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.errors.TopicExistsException
import org.apache.kafka.common.serialization.StringSerializer
import java.util.Properties

data class AvroKafkaRecord(
    val key: String,
    val values: Map<String, Any?>,
)

object KafkaAvroProducers {
    fun produce(
        bootstrapServers: String,
        schemaRegistryUrl: String,
        topic: String,
        schemaJson: String,
        records: Iterable<AvroKafkaRecord>,
        partitions: Int = 1,
        replicationFactor: Short = 1,
    ) {
        createTopic(bootstrapServers, topic, partitions, replicationFactor)

        val schema = Schema.Parser().parse(schemaJson)
        val props = Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer::class.java.name)
            put("schema.registry.url", schemaRegistryUrl)
        }

        KafkaProducer<String, GenericRecord>(props).use { producer ->
            records.forEach { record ->
                producer.send(ProducerRecord(topic, record.key, record.toGenericRecord(schema))).get()
            }
            producer.flush()
        }
    }

    fun createTopic(
        bootstrapServers: String,
        topic: String,
        partitions: Int = 1,
        replicationFactor: Short = 1,
    ) {
        AdminClient.create(mapOf(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers)).use { admin ->
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
}
