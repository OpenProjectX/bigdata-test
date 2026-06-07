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
    fun `maps localstack debug level to localstack environment`() {
        val environment = BigDataContainerLogLevels.environment(BigDataService.LOCALSTACK_S3, "TRACE")

        assertEquals("trace", environment["LS_LOG"])
        assertEquals("1", environment["DEBUG"])
    }

    @Test
    fun `rejects unsupported log level`() {
        assertThrows(IllegalArgumentException::class.java) {
            BigDataContainerLogLevels.environment(BigDataService.HDFS, "verbose")
        }
    }
}
