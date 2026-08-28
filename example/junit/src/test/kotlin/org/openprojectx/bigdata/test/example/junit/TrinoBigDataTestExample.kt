package org.openprojectx.bigdata.test.example.junit

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.openprojectx.bigdata.test.core.BigDataService
import org.openprojectx.bigdata.test.core.BigDataTestKit
import org.openprojectx.bigdata.test.junit5.BigDataTest

@BigDataTest(config = ["classpath:trino-bigdata-test.toml"])
class TrinoBigDataTestExample {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    @Test
    fun `queries the managed hive metastore through Trino`(kit: BigDataTestKit) {
        val result = executeTrino(
            kit.endpoint(BigDataService.TRINO).property("trino.url"),
            "SHOW SCHEMAS FROM hive",
        )

        assertFalse(result.contains("\"error\""), result)
        assertTrue(result.contains("information_schema"), result)
        assertTrue(result.contains("default"), result)
    }

    private fun executeTrino(baseUrl: String, sql: String): String {
        var request = HttpRequest.newBuilder(URI.create("$baseUrl/v1/statement"))
            .header("X-Trino-User", "bigdata-test")
            .POST(HttpRequest.BodyPublishers.ofString(sql))
            .build()
        val result = StringBuilder()

        while (true) {
            val response = http.send(request, HttpResponse.BodyHandlers.ofString())
            check(response.statusCode() == 200) {
                "Trino returned HTTP ${response.statusCode()}: ${response.body()}"
            }
            result.appendLine(response.body())
            val nextUri = NEXT_URI.find(response.body())?.groupValues?.get(1) ?: break
            request = HttpRequest.newBuilder(URI.create(nextUri))
                .header("X-Trino-User", "bigdata-test")
                .GET()
                .build()
        }
        return result.toString()
    }

    private companion object {
        val NEXT_URI = Regex("\\\"nextUri\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
    }
}
