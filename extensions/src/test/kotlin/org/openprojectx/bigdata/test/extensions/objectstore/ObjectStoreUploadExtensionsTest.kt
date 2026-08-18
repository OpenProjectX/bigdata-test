package org.openprojectx.bigdata.test.extensions.objectstore

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.openprojectx.bigdata.test.core.BigDataEndpoint
import org.openprojectx.bigdata.test.core.BigDataService
import org.openprojectx.bigdata.test.core.BigDataServiceId
import org.openprojectx.bigdata.test.core.BigDataTestKit
import org.openprojectx.bigdata.test.extensions.config.BigDataExtensionsConfigLoader
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionContext
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionEvent
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class ObjectStoreUploadExtensionsTest {
    private val servers = mutableListOf<HttpServer>()

    @AfterEach
    fun stopServers() {
        servers.forEach { it.stop(0) }
        servers.clear()
    }

    @Test
    fun `loads upload extensions with inline sources from toml`() {
        val extensions = BigDataExtensionsConfigLoader().load(
            """
            [[s3Uploads]]
            id = "seed-s3"
            bucket = "demo"
            prefix = "input"
            sources = [
              { source = "classpath:data/a.txt", key = "input/a.txt", contentType = "text/plain" },
              { source = "file:build/seed", prefix = "nested" },
            ]

            [[gcsUploads]]
            id = "seed-gcs"
            bucket = "demo-gcs"
            project = "demo-project"
            createBucket = false
            sources = [
              { source = "classpath:data/b.txt" },
            ]
            """.trimIndent(),
        )

        assertEquals(2, extensions.size)
        assertTrue(extensions[0] is S3UploadExtension)
        assertTrue(extensions[1] is GcsUploadExtension)
        assertEquals("seed-s3", extensions[0].id)
        assertEquals("seed-gcs", extensions[1].id)
    }

    @Test
    fun `loads upload extensions with source table arrays from toml`() {
        val extensions = BigDataExtensionsConfigLoader().load(
            """
            [[s3Uploads]]
            id = "seed-s3"
            bucket = "demo"
            prefix = "input"

            [[s3Uploads.sources]]
            source = "classpath:data/a.txt"
            key = "input/a.txt"
            contentType = "text/plain"

            [[s3Uploads.sources]]
            source = "file:build/seed"
            prefix = "nested"
            """.trimIndent(),
        )

        assertEquals(1, extensions.size)
        assertTrue(extensions.single() is S3UploadExtension)
        assertEquals("seed-s3", extensions.single().id)
    }

    @Test
    fun `uploads files and directories to s3`(@TempDir tempDir: Path) {
        Files.writeString(tempDir.resolve("single.txt"), "single")
        Files.createDirectories(tempDir.resolve("folder/nested"))
        Files.writeString(tempDir.resolve("folder/root.txt"), "root")
        Files.writeString(tempDir.resolve("folder/nested/child.txt"), "child")
        val requests = mutableListOf<CapturedRequest>()
        val endpoint = startServer(requests)
        val context = contextWithEndpoint(
            BigDataService.S3,
            "aws.endpoint-url.s3",
            endpoint,
        )
        val extension = S3UploadExtension(
            id = "seed-s3",
            bucket = "demo",
            prefix = "seed",
            sources = listOf(
                ObjectStoreUploadSource("file:${tempDir.resolve("single.txt")}", key = "explicit/single.txt"),
                ObjectStoreUploadSource("file:${tempDir.resolve("folder")}", prefix = "dir"),
            ),
        )

        extension.onEvent(BigDataExtensionEvent.AFTER_KIT_START, context)

        assertEquals("3", context.outputs["seed-s3.uploaded-count"])
        assertEquals(
            listOf(
                "PUT /demo",
                "PUT /demo/explicit/single.txt",
                "PUT /demo/seed/dir/nested/child.txt",
                "PUT /demo/seed/dir/root.txt",
            ),
            requests.map { "${it.method} ${it.path}" },
        )
        assertTrue(requests[1].body.contains("single"))
        assertTrue(requests[2].body.contains("child"))
        assertTrue(requests[3].body.contains("root"))
    }

    @Test
    fun `uploads files to gcs`(@TempDir tempDir: Path) {
        Files.writeString(tempDir.resolve("seed.json"), """{"ok":true}""")
        val requests = mutableListOf<CapturedRequest>()
        val endpoint = startServer(requests)
        val context = contextWithEndpoint(
            BigDataService.FAKE_GCS,
            "google.cloud.storage.host",
            endpoint,
        )
        val extension = GcsUploadExtension(
            id = "seed-gcs",
            bucket = "demo-gcs",
            prefix = "data",
            project = "project-a",
            sources = listOf(
                ObjectStoreUploadSource("file:${tempDir.resolve("seed.json")}", contentType = "application/json"),
            ),
        )

        extension.onEvent(BigDataExtensionEvent.AFTER_KIT_START, context)

        assertEquals("1", context.outputs["seed-gcs.uploaded-count"])
        assertEquals(
            listOf(
                "POST /storage/v1/b",
                "POST /upload/storage/v1/b/demo-gcs/o",
            ),
            requests.map { "${it.method} ${it.path}" },
        )
        assertTrue(requests[0].query.contains("project=project-a"))
        assertTrue(requests[1].query.contains("uploadType=multipart"))
    }

    private fun startServer(requests: MutableList<CapturedRequest>): String {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests += exchange.capture()
            val response = exchange.responseBody()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
            exchange.close()
        }
        server.start()
        servers += server
        return "http://127.0.0.1:${server.address.port}"
    }

    private fun HttpExchange.capture(): CapturedRequest =
        CapturedRequest(
            method = requestMethod,
            path = requestURI.path,
            query = requestURI.query.orEmpty(),
            body = requestBody.readAllBytes().toString(StandardCharsets.UTF_8),
        )

    private fun HttpExchange.responseBody(): ByteArray {
        val name = when {
            requestURI.path.contains("/upload/storage/v1/b/") ->
                requestURI.query.orEmpty().substringAfter("name=", "object").substringBefore('&')
            requestURI.path == "/storage/v1/b" -> "demo-gcs"
            else -> "ok"
        }
        val bucket = requestURI.path.substringAfter("/upload/storage/v1/b/", "").substringBefore("/o", "")
        return if (bucket.isNotBlank()) {
            """{"bucket":"$bucket","name":"$name"}"""
        } else {
            """{"name":"$name"}"""
        }.toByteArray(StandardCharsets.UTF_8)
    }

    private fun contextWithEndpoint(
        service: BigDataService,
        property: String,
        endpoint: String,
    ): BigDataExtensionContext {
        val kit = BigDataTestKit.builder().build()
        val endpoints = BigDataTestKit::class.java.getDeclaredField("endpoints")
        endpoints.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (endpoints.get(kit) as MutableMap<BigDataServiceId, BigDataEndpoint>)[BigDataServiceId(service)] =
            BigDataEndpoint(service, "127.0.0.1", emptyMap(), mapOf(property to endpoint))
        return BigDataExtensionContext(kit)
    }

    private data class CapturedRequest(
        val method: String,
        val path: String,
        val query: String,
        val body: String,
    )
}
