package org.openprojectx.bigdata.test.example.spark

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.openprojectx.bigdata.test.core.BigDataService
import org.openprojectx.bigdata.test.core.BigDataTestKit
import org.openprojectx.bigdata.test.extensions.config.BigDataExtensionsBuilder
import org.openprojectx.bigdata.test.extensions.config.BigDataExtensionsConfigurer
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionResult
import org.openprojectx.bigdata.test.extensions.junit5.BigDataExtensions
import org.openprojectx.bigdata.test.extensions.trino.TrinoSqlClient
import org.openprojectx.bigdata.test.junit5.BigDataTest

@BigDataExtensions
@BigDataTest(config = ["classpath:spark-trino-iceberg-view.toml"])
class SparkTrinoIcebergViewExample {
    @Test
    fun `creates an Iceberg table with Spark and a view with Trino`(
        kit: BigDataTestKit,
        extensions: BigDataExtensionResult,
    ) {
        assertEquals("3", extensions.required("$SPARK_EXTENSION_ID.executed-statements"))
        assertEquals("1", extensions.required("$TRINO_EXTENSION_ID.executed-statements"))

        TrinoSqlClient(
            jdbcUrl = kit.endpoint(BigDataService.TRINO).property("trino.jdbc.url"),
            catalog = CATALOG,
            schema = NAMESPACE,
        ).use { trino ->
            val result = trino.execute("SELECT id, name, storage FROM events_view ORDER BY id")
            assertEquals(listOf("id", "name", "storage"), result.columns.map { it.name.lowercase() })
            assertEquals(
                listOf(
                    listOf(1L, "alpha", "spark-iceberg"),
                    listOf(2L, "beta", "spark-iceberg"),
                ),
                result.rows,
            )
        }
    }

    companion object : BigDataExtensionsConfigurer {
        private const val CATALOG = "iceberg"
        private const val NAMESPACE = "spark_trino_demo"
        private const val BUCKET = "spark-trino-iceberg"
        private const val SPARK_EXTENSION_ID = "spark-create-iceberg-table"
        private const val TRINO_EXTENSION_ID = "trino-create-view"

        override fun configure(extensions: BigDataExtensionsBuilder) {
            extensions.s3Bucket(BUCKET)
            extensions.sparkSqlPreparation {
                id = SPARK_EXTENSION_ID
                config("spark.sql.extensions", "org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions")
                config("spark.sql.catalog.spark_catalog", "org.apache.iceberg.spark.SparkSessionCatalog")
                config("spark.sql.catalog.spark_catalog.type", "hive")
                config("spark.sql.warehouse.dir", "s3a://$BUCKET/warehouse")
                statement("CREATE DATABASE IF NOT EXISTS $NAMESPACE LOCATION 's3a://$BUCKET/warehouse/$NAMESPACE.db'")
                statement(
                    """
                    CREATE OR REPLACE TABLE $NAMESPACE.events
                    USING iceberg
                    AS SELECT * FROM VALUES
                        (CAST(1 AS BIGINT), 'alpha', 'spark-iceberg'),
                        (CAST(2 AS BIGINT), 'beta', 'spark-iceberg')
                        AS events(id, name, storage)
                    """.trimIndent(),
                )
                statement("SELECT COUNT(*) FROM $NAMESPACE.events")
            }
            extensions.trinoSqlPreparation {
                id = TRINO_EXTENSION_ID
                catalog = CATALOG
                schema = NAMESPACE
                statement(
                    """
                    CREATE OR REPLACE VIEW $CATALOG.$NAMESPACE.events_view AS
                    SELECT id, name, storage FROM $CATALOG.$NAMESPACE.events
                    """.trimIndent(),
                )
            }
        }
    }
}
