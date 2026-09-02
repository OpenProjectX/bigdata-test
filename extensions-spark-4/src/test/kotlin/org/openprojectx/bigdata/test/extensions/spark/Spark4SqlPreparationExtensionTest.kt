package org.openprojectx.bigdata.test.extensions.spark

import org.apache.spark.sql.SparkSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.openprojectx.bigdata.test.core.BigDataTestKit
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionContext
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionEvent

class Spark4SqlPreparationExtensionTest {
    @Test
    fun `executes SQL using Spark 4_1_1`() {
        val extension = SparkSqlPreparationExtension(
            id = "spark-4-prep-test",
            enableHiveSupport = false,
            stopAfterRun = false,
            clearSparkSessions = false,
            closeHadoopFileSystems = false,
            useKitEndpoints = false,
            configs = mapOf("spark.sql.shuffle.partitions" to "1"),
            sql = listOf(
                SparkSqlPreparationStatement(
                    statement = "CREATE OR REPLACE TEMP VIEW spark4_values AS SELECT 41 + 1 AS value",
                ),
            ),
        )
        val context = BigDataExtensionContext(kit = BigDataTestKit.builder().build())

        try {
            extension.onEvent(BigDataExtensionEvent.AFTER_KIT_START, context)

            val spark = SparkSession.active()
            assertEquals("4.1.1", spark.version())
            assertEquals(42, spark.table("spark4_values").first().getInt(0))
            assertEquals("in-process", context.outputs["spark-4-prep-test.engine"])
            assertEquals("1", context.outputs["spark-4-prep-test.executed-statements"])
        } finally {
            runCatching { SparkSession.active().stop() }
            SparkSession.clearActiveSession()
            SparkSession.clearDefaultSession()
        }
    }
}
