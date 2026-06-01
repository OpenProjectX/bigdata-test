package org.openprojectx.bigdata.test.example.junit

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.openprojectx.bigdata.test.core.BigDataService
import org.openprojectx.bigdata.test.core.BigDataTestKit
import org.openprojectx.bigdata.test.junit5.BigDataTest

@BigDataTest(config = ["classpath:localstack-s3-tls.toml"])
class LocalStackS3TlsExampleTest {
    @Test
    fun `localstack s3 exposes an https endpoint through haproxy`(kit: BigDataTestKit) {
        val endpoint = kit.endpoint(BigDataService.LOCALSTACK_S3)
        val url = endpoint.property("aws.endpoint-url.s3")
        val client = HttpClient.newHttpClient()

        val response = client.send(
            HttpRequest.newBuilder(URI.create("$url/_localstack/health")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

        assertTrue(url.startsWith("https://"))
        assertTrue(endpoint.ports.containsKey("https"))
        assertEquals(endpoint.property("javax.net.ssl.trustStore"), System.getProperty("javax.net.ssl.trustStore"))
        assertEquals(200, response.statusCode())
    }
}
