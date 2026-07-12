package org.openprojectx.bigdata.test.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class BigDataContainerLogLevelsTest {
    @Test
    fun `maps kafka log level to kafka environment`() {
        val environment = BigDataContainerLogLevels.environment(BigDataService.KAFKA, "debug")

        assertEquals("DEBUG", environment["KAFKA_LOG4J_ROOT_LOGLEVEL"])
        assertEquals("DEBUG", environment["KAFKA_TOOLS_LOG4J_LOGLEVEL"])
    }

    @Test
    fun `maps S3 log level to Floci environment`() {
        val environment = BigDataContainerLogLevels.environment(BigDataService.S3, "TRACE")

        assertEquals("TRACE", environment["QUARKUS_LOG_LEVEL"])
    }

    @Test
    fun `rejects unsupported log level`() {
        assertThrows(IllegalArgumentException::class.java) {
            BigDataContainerLogLevels.environment(BigDataService.HDFS, "verbose")
        }
    }
}
