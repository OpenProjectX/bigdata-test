package org.openprojectx.bigdata.test.example.spark

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
    fun `queries Trino views over S3 Hive and Iceberg tables and GCS Iceberg tables`(
        kit: BigDataTestKit,
        extensions: BigDataExtensionResult,
    ) {
        assertEquals("5", extensions.required("$SPARK_EXTENSION_ID.executed-statements"))
        assertEquals("2", extensions.required("$TRINO_EXTENSION_ID.executed-statements"))

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
                    listOf(3L, "gamma", "spark-hive"),
                    listOf(4L, "delta", "spark-hive"),
                ),
                result.rows,
            )
            val gcsResult = trino.execute("SELECT id, name, storage FROM events_gcs_view ORDER BY id")
            assertEquals(
                listOf(
                    listOf(5L, "epsilon", "spark-iceberg-gcs"),
                    listOf(6L, "zeta", "spark-iceberg-gcs"),
                ),
                gcsResult.rows,
            )
            val files = trino.execute("SELECT file_path FROM \"events_gcs\$files\"")
            assertTrue(files.rows.isNotEmpty())
            assertTrue(
                files.rows.all { it.single().toString().startsWith("gs://$GCS_BUCKET/") },
                "Expected Trino to read Iceberg data files stored on GCS: ${files.rows}",
            )
        }
    }

    companion object : BigDataExtensionsConfigurer {
        private const val CATALOG = "iceberg"
        private const val NAMESPACE = "spark_trino_demo"
        private const val BUCKET = "spark-trino-iceberg"
        private const val GCS_BUCKET = "spark-trino-iceberg-gcs"
        private const val SPARK_EXTENSION_ID = "spark-create-iceberg-table"
        private const val TRINO_EXTENSION_ID = "trino-create-view"

        override fun configure(extensions: BigDataExtensionsBuilder) {
            extensions.s3Bucket(BUCKET)
            extensions.gcsBucket(GCS_BUCKET)
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
                statement(
                    """
                    CREATE EXTERNAL TABLE $NAMESPACE.events_hive (
                        id BIGINT,
                        name STRING,
                        storage STRING
                    )
                    STORED AS PARQUET
                    LOCATION 's3a://$BUCKET/warehouse/$NAMESPACE.db/events_hive'
                    """.trimIndent(),
                )
                statement(
                    """
                    INSERT OVERWRITE TABLE $NAMESPACE.events_hive
                    SELECT * FROM VALUES
                        (CAST(3 AS BIGINT), 'gamma', 'spark-hive'),
                        (CAST(4 AS BIGINT), 'delta', 'spark-hive')
                        AS events_hive(id, name, storage)
                    """.trimIndent(),
                )
                statement(
                    """
                    CREATE OR REPLACE TABLE $NAMESPACE.events_gcs
                    USING iceberg
                    LOCATION 'gs://$GCS_BUCKET/warehouse/$NAMESPACE/events_gcs'
                    AS SELECT * FROM VALUES
                        (CAST(5 AS BIGINT), 'epsilon', 'spark-iceberg-gcs'),
                        (CAST(6 AS BIGINT), 'zeta', 'spark-iceberg-gcs')
                        AS events_gcs(id, name, storage)
                    """.trimIndent(),
                )
            }
            extensions.trinoSqlPreparation {
                id = TRINO_EXTENSION_ID
                catalog = CATALOG
                schema = NAMESPACE
                statement(
                    """
                    CREATE OR REPLACE VIEW $CATALOG.$NAMESPACE.events_view AS
                    SELECT id, name, storage FROM $CATALOG.$NAMESPACE.events
                    UNION ALL
                    SELECT id, name, storage FROM hive.$NAMESPACE.events_hive
                    """.trimIndent(),
                )
                statement(
                    """
                    CREATE OR REPLACE VIEW $CATALOG.$NAMESPACE.events_gcs_view AS
                    SELECT id, name, storage FROM $CATALOG.$NAMESPACE.events_gcs
                    """.trimIndent(),
                )
            }
        }
    }
}
