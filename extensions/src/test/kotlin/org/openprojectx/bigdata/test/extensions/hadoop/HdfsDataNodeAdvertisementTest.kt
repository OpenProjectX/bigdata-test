package org.openprojectx.bigdata.test.extensions.hadoop

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.apache.hadoop.hdfs.DistributedFileSystem
import org.apache.hadoop.hdfs.protocol.DatanodeInfo
import org.apache.hadoop.hdfs.protocol.HdfsConstants
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.openprojectx.bigdata.test.core.BigDataService
import org.openprojectx.bigdata.test.core.BigDataTestKit
import org.openprojectx.bigdata.test.core.HdfsOptions
import org.openprojectx.bigdata.test.core.PortBindingOptions
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant

class HdfsDataNodeAdvertisementTest {
    private val advertisedDataNodeHost = "hdfs.test.local"
    private val expectedResolvedDataNodeAddress = "127.0.0.1"

    @Test
    fun `hdfs client sees configured datanode advertisement`() {
        val dataNodePort = findAvailablePort()
        val kit = BigDataTestKit.builder()
            .withHdfs(
                HdfsOptions(
                    dataNodePort = dataNodePort,
                    dataNodeHostname = advertisedDataNodeHost,
                ),
            )
            .withPortBindings(PortBindingOptions(hdfsDataNode = dataNodePort))
            .build()

        try {
            kit.start()

            val endpoint = kit.endpoint(BigDataService.HDFS)
            assertEquals(dataNodePort, endpoint.port("datanode"))
            assertEquals(advertisedDataNodeHost, endpoint.property("dfs.datanode.hostname"))
            assertEquals("true", endpoint.property("dfs.client.use.datanode.hostname"))

//            val resolvedAddresses = InetAddress.getAllByName(advertisedDataNodeHost).map { it.hostAddress }
//            println("Resolved $advertisedDataNodeHost to $resolvedAddresses")
//            assertEquals(listOf(expectedResolvedDataNodeAddress), resolvedAddresses)

            val hdfsUri = URI.create(endpoint.property("fs.defaultFS"))
            val configuration = Configuration().apply {
                endpoint.properties.forEach { (key, value) -> set(key, value) }
            }
            assertTrue(configuration.getBoolean("dfs.client.use.datanode.hostname", false))

            FileSystem.get(hdfsUri, configuration).use { fs ->
                val distributedFileSystem = fs as DistributedFileSystem
                val dataNodes = waitForLiveDataNodes(distributedFileSystem)

                assertEquals(1, dataNodes.size, "Expected exactly one live DataNode")
                val dataNode = dataNodes.single()
                assertEquals(advertisedDataNodeHost, dataNode.hostName)
                assertEquals(dataNodePort, dataNode.xferPort)
                assertEquals("$advertisedDataNodeHost:$dataNodePort", dataNode.getXferAddrWithHostname())
                assertEquals("$advertisedDataNodeHost:$dataNodePort", dataNode.getXferAddr(true))
                println(
                    "NameNode reports DataNode host=${dataNode.hostName}, xferPort=${dataNode.xferPort}, " +
                        "clientAddress=${dataNode.getXferAddr(true)}",
                )

                val path = Path("/tmp/bigdata-test-datanode-advertisement.txt")
                val payload = "advertised-datanode-ok".toByteArray(StandardCharsets.UTF_8)
                fs.create(path, true).use { output -> output.write(payload) }
                assertTrue(fs.exists(path))
                fs.open(path).use { input -> assertArrayEquals(payload, input.readAllBytes()) }
            }
        } finally {
            kit.close()
        }
    }

    private fun findAvailablePort(): Int =
        ServerSocket(0).use { socket ->
            socket.reuseAddress = true
            socket.localPort
        }

    private fun waitForLiveDataNodes(distributedFileSystem: DistributedFileSystem): Array<DatanodeInfo> {
        val deadline = Instant.now().plus(Duration.ofSeconds(30))
        var lastFailure: Exception? = null

        while (Instant.now().isBefore(deadline)) {
            try {
                val dataNodes = distributedFileSystem.getDataNodeStats(HdfsConstants.DatanodeReportType.LIVE)
                if (dataNodes.isNotEmpty()) return dataNodes
            } catch (exception: Exception) {
                lastFailure = exception
            }
            Thread.sleep(500)
        }

        lastFailure?.let { throw it }
        return emptyArray()
    }
}
