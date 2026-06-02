package org.openprojectx.bigdata.test.example.junit

import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.junit.jupiter.api.Test
import org.openprojectx.bigdata.test.core.BigDataService
import org.openprojectx.bigdata.test.core.BigDataTestKit
import org.openprojectx.bigdata.test.junit5.BigDataTest

@BigDataTest(kafkaTls = true, schemaRegistry = true)
class KafkaTlsExampleTest {
    @Test
    fun exposesSslClientProperties(kit: BigDataTestKit) {
        val kafka = kit.endpoint(BigDataService.KAFKA)

        check(kafka.property("security.protocol") == "SSL")
        check(kafka.property("ssl.truststore.type") == "PKCS12")
        check(kafka.property("ssl.truststore.location").isNotBlank())

        AdminClient.create(
            mapOf(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.property("bootstrap.servers"),
                "security.protocol" to kafka.property("security.protocol"),
                "ssl.truststore.location" to kafka.property("ssl.truststore.location"),
                "ssl.truststore.password" to kafka.property("ssl.truststore.password"),
                "ssl.truststore.type" to kafka.property("ssl.truststore.type"),
            ),
        ).use { admin ->
            admin.listTopics().names().get()
        }
    }
}
