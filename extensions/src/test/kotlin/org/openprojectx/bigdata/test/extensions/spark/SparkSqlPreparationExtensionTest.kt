package org.openprojectx.bigdata.test.extensions.spark

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.apache.spark.sql.SparkSession
import org.openprojectx.bigdata.test.core.BigDataTestKit
import org.openprojectx.bigdata.test.extensions.config.BigDataExtensionsConfigLoader
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionContext
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionEvent

class SparkSqlPreparationExtensionTest {
    @Test
    fun `loading spark sql prep config does not require spark runtime classes`() {
        val extensions = BigDataExtensionsConfigLoader().load("classpath:spark-sql-prep-extension.toml")

        assertEquals(1, extensions.size)
        assertEquals("prepare-tables", extensions.single().id)
    }

    @Test
    fun `executes configured SQL with in process spark`() {
        val extension = SparkSqlPreparationExtension(
            id = "spark-prep-test",
            enableHiveSupport = false,
            useKitEndpoints = false,
            configs = mapOf("spark.sql.shuffle.partitions" to "1"),
            sql = listOf(SparkSqlPreparationStatement(statement = "SELECT 1; SELECT 2")),
        )
        val context = BigDataExtensionContext(kit = BigDataTestKit.builder().build())

        extension.onEvent(BigDataExtensionEvent.AFTER_KIT_START, context)

        assertEquals("in-process", context.outputs["spark-prep-test.engine"])
        assertEquals("2", context.outputs["spark-prep-test.executed-statements"])
    }

    @Test
    fun `executes SQL script resource and leaves expected data`() {
        val extension = SparkSqlPreparationExtension(
            id = "spark-script-test",
            enableHiveSupport = false,
            stopAfterRun = false,
            clearSparkSessions = false,
            closeHadoopFileSystems = false,
            useKitEndpoints = false,
            configs = mapOf("spark.sql.shuffle.partitions" to "1"),
            sql = listOf(SparkSqlPreparationStatement(resource = "classpath:sql/local_create_tables.sql")),
        )
        val context = BigDataExtensionContext(kit = BigDataTestKit.builder().build())

        try {
            extension.onEvent(BigDataExtensionEvent.AFTER_KIT_START, context)

            val spark = SparkSession.active()
            val count = spark.table("script_events")
                .where("storage = 'script-resource'")
                .count()
            assertEquals(2L, count)
            assertEquals("in-process", context.outputs["spark-script-test.engine"])
            assertEquals("2", context.outputs["spark-script-test.executed-statements"])
        } finally {
            SparkSession.active().stop()
            SparkSession.clearActiveSession()
            SparkSession.clearDefaultSession()
        }
    }
}
