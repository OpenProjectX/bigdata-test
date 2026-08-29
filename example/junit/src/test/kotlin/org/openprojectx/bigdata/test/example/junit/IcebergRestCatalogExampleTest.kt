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
import org.openprojectx.bigdata.test.extensions.junit5.BigDataExtensions
import org.openprojectx.bigdata.test.junit5.BigDataTest

@BigDataExtensions("classpath:iceberg-rest-catalog-extensions.toml")
@BigDataTest(config = ["classpath:iceberg-rest-catalog.toml"])
class IcebergRestCatalogExampleTest {
    @Test
    fun `creates an Iceberg table in an S3 warehouse through the REST catalog`(kit: BigDataTestKit) {
        val endpoint = kit.endpoint(BigDataService.ICEBERG_REST_CATALOG)
        val uri = endpoint.property("iceberg.rest.uri")
        val catalog = endpoint.property("iceberg.rest.warehouse")
        val token = endpoint.property("iceberg.rest.token")
        val realm = endpoint.property("iceberg.rest.realm")
        val client = HttpClient.newHttpClient()
        val response = client.send(
            authorizedRequest("$uri/v1/config?warehouse=$catalog", token, realm).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains("defaults"))

        val create = client.send(
            authorizedRequest("$uri/v1/$catalog/namespaces", token, realm)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"namespace":["smoke"]}"""))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        assertEquals(200, create.statusCode(), create.body())

        val namespaces = client.send(
            authorizedRequest("$uri/v1/$catalog/namespaces", token, realm).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        assertEquals(200, namespaces.statusCode())
        assertTrue(namespaces.body().contains("smoke"))

        val createTable = client.send(
            authorizedRequest("$uri/v1/$catalog/namespaces/smoke/tables", token, realm)
                .header("Content-Type", "application/json")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        """
                        {
                          "name": "events",
                          "schema": {
                            "type": "struct",
                            "schema-id": 0,
                            "fields": [
                              {"id": 1, "name": "id", "required": true, "type": "long"},
                              {"id": 2, "name": "payload", "required": false, "type": "string"}
                            ]
                          },
                          "partition-spec": {"spec-id": 0, "fields": []},
                          "write-order": {"order-id": 0, "fields": []},
                          "stage-create": false,
                          "properties": {}
                        }
                        """.trimIndent(),
                    ),
                )
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        assertEquals(200, createTable.statusCode(), createTable.body())
        assertTrue(createTable.body().contains("s3://iceberg-rest-example/warehouse/smoke/events"))

        val loadTable = client.send(
            authorizedRequest("$uri/v1/$catalog/namespaces/smoke/tables/events", token, realm).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        assertEquals(200, loadTable.statusCode(), loadTable.body())
        assertTrue(loadTable.body().contains("s3://iceberg-rest-example/warehouse/smoke/events"))

        val s3Endpoint = kit.endpoint(BigDataService.S3).property("aws.endpoint-url.s3")
        val objects = client.send(
            HttpRequest.newBuilder(
                URI.create("${s3Endpoint.trimEnd('/')}/iceberg-rest-example?list-type=2&prefix=warehouse/smoke/events"),
            ).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        assertEquals(200, objects.statusCode(), objects.body())
        assertTrue(objects.body().contains("metadata"), objects.body())
    }

    private fun authorizedRequest(uri: String, token: String, realm: String): HttpRequest.Builder =
        HttpRequest.newBuilder(URI.create(uri))
            .header("Authorization", "Bearer $token")
            .header("Polaris-Realm", realm)
}
