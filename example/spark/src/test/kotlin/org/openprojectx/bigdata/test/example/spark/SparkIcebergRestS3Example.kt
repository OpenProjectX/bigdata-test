package org.openprojectx.bigdata.test.example.spark

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import org.apache.spark.sql.SparkSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.openprojectx.bigdata.test.core.BigDataService
import org.openprojectx.bigdata.test.core.BigDataTestKit
import org.openprojectx.bigdata.test.extensions.junit5.BigDataExtensions
import org.openprojectx.bigdata.test.junit5.BigDataTest

@BigDataExtensions("classpath:spark-iceberg-rest-s3-extensions.toml")
@BigDataTest(config = ["classpath:spark-iceberg-rest-s3.toml"])
class SparkIcebergRestS3Example {
    @Test
    fun `writes and reads Iceberg data through REST catalog on S3`(kit: BigDataTestKit) {
        val restUri = kit.endpoint(BigDataService.ICEBERG_REST_CATALOG).property("iceberg.rest.uri")
        val s3Endpoint = kit.endpoint(BigDataService.S3).property("aws.endpoint-url.s3")
        val spark = createSparkSession(restUri, s3Endpoint)

        try {
            spark.sql("CREATE NAMESPACE IF NOT EXISTS rest.demo")
            spark.sql(
                """
                CREATE TABLE rest.demo.events (
                    id BIGINT,
                    payload STRING
                ) USING iceberg
                """.trimIndent(),
            )
            spark.sql(
                """
                INSERT INTO rest.demo.events VALUES
                    (1, 'alpha'),
                    (2, 'beta'),
                    (3, 'gamma')
                """.trimIndent(),
            )

            val rows = spark.sql("SELECT id, payload FROM rest.demo.events ORDER BY id").collectAsList()
            assertEquals(3, rows.size)
            assertEquals(listOf("alpha", "beta", "gamma"), rows.map { it.getString(1) })

            val objects = listS3Objects(s3Endpoint)
            assertTrue(objects.contains("warehouse/demo/events/metadata/"), objects)
            assertTrue(objects.contains("warehouse/demo/events/data/"), objects)
            assertTrue(objects.contains(".parquet"), objects)
        } finally {
            spark.stop()
            SparkSession.clearActiveSession()
            SparkSession.clearDefaultSession()
        }
    }

    private fun createSparkSession(restUri: String, s3Endpoint: String): SparkSession =
        SparkSession.builder()
            .appName("bigdata-test-iceberg-rest-s3-example")
            .master("local[2]")
            .config("spark.ui.enabled", "false")
            .config("spark.sql.extensions", "org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions")
            .config("spark.sql.catalog.rest", "org.apache.iceberg.spark.SparkCatalog")
            .config("spark.sql.catalog.rest.type", "rest")
            .config("spark.sql.catalog.rest.uri", restUri)
            .config("spark.sql.catalog.rest.io-impl", "org.apache.iceberg.aws.s3.S3FileIO")
            .config("spark.sql.catalog.rest.s3.endpoint", s3Endpoint)
            .config("spark.sql.catalog.rest.s3.access-key-id", "test")
            .config("spark.sql.catalog.rest.s3.secret-access-key", "test")
            .config("spark.sql.catalog.rest.s3.region", "us-east-1")
            .config("spark.sql.catalog.rest.s3.path-style-access", "true")
            .config("spark.sql.defaultCatalog", "rest")
            .config("spark.sql.warehouse.dir", "file:${System.getProperty("java.io.tmpdir")}/spark-iceberg-rest")
            .getOrCreate()

    private fun listS3Objects(s3Endpoint: String): String {
        val response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(
                URI.create("${s3Endpoint.trimEnd('/')}/spark-iceberg-rest?list-type=2&prefix=warehouse/demo/events"),
            ).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        assertEquals(200, response.statusCode(), response.body())
        return response.body()
    }
}
