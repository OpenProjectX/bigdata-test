package org.openprojectx.bigdata.test.core

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KafkaReadinessTest {
    @Test
    fun `uses a protocol probe when kafka console lifecycle logs are unavailable`() {
        val kit = BigDataTestKit.builder()
            .withKafka(KafkaOptions(enabled = true, startupTimeoutSeconds = 60))
            .withContainerLogLevel(BigDataService.KAFKA, "INFO")
            .build()

        kit.use {
            it.start()
            assertTrue(it.endpoint(BigDataService.KAFKA).property("bootstrap.servers").isNotBlank())
        }
    }
}
