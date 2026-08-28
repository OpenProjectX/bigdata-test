package org.openprojectx.bigdata.test.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

class AuxiliaryContainerTest {
    @Test
    fun `attaches an arbitrary generic container and exposes internal service coordinates`() {
        val kit = BigDataTestKit.builder()
            .withHiveMetastore()
            .withInstance("analytics") { withHdfs(HdfsOptions(enabled = true, nameNodePort = 8120)) }
            .build()
        val container = GenericContainer<Nothing>(DockerImageName.parse("alpine:3.22"))

        assertSame(container, kit.attachContainer(container, "analytics", "query-engine"))
        assertTrue(container.networkAliases.contains("query-engine"))
        assertEquals(
            "hdfs://hdfs:8120",
            kit.containerEndpoint(BigDataService.HDFS, "analytics").uri("hdfs", "namenode"),
        )
        assertEquals(
            "thrift://hive-metastore:9083",
            kit.containerEndpoint(BigDataService.HIVE_METASTORE).uri("thrift", "thrift"),
        )
    }

    @Test
    fun `rejects endpoints for disabled services and cross-instance reattachment`() {
        val kit = BigDataTestKit.builder()
            .withInstance("analytics") { withS3() }
            .build()
        val container = GenericContainer<Nothing>(DockerImageName.parse("alpine:3.22"))

        kit.attachContainer(container, "analytics")

        assertThrows(IllegalStateException::class.java) {
            kit.containerEndpoint(BigDataService.S3)
        }
        assertThrows(IllegalArgumentException::class.java) {
            kit.attachContainer(container)
        }
    }
}
