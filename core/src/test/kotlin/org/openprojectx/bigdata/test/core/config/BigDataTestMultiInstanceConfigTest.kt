package org.openprojectx.bigdata.test.core.config

import java.nio.file.Path
import kotlin.io.path.writeText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.openprojectx.bigdata.test.core.BigDataService
import org.openprojectx.bigdata.test.core.BigDataTestKit

class BigDataTestMultiInstanceConfigTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `loads named instances with the regular config schema`() {
        val configFile = directory.resolve("instances.toml")
        configFile.writeText(
            """
            [services]
            s3 = true

            [instances.analytics.services]
            kafka = true
            schemaRegistry = true

            [instances.analytics.containers.kafka.env]
            TEST_INSTANCE = "analytics"

            [instances.analytics.kafka]
            startupTimeoutSeconds = 47

            [instances.archive.services]
            fakeGcs = true
            """.trimIndent(),
        )

        val config = BigDataTestConfigLoader().load(configFile.toString())
        val options = config.toTestKitOptions()

        assertTrue(options.s3.enabled)
        assertEquals(setOf("analytics", "archive"), options.instances.keys)
        assertTrue(options.instances.getValue("analytics").kafka.enabled)
        assertTrue(options.instances.getValue("analytics").kafka.schemaRegistryEnabled)
        assertEquals(47, options.instances.getValue("analytics").kafka.startupTimeoutSeconds)
        assertEquals(
            "analytics",
            options.instances.getValue("analytics")
                .containerCustomizations.getValue(BigDataService.KAFKA)
                .environment.getValue("TEST_INSTANCE"),
        )
        assertTrue(options.instances.getValue("archive").fakeGcs.enabled)
        assertFalse(options.instances.getValue("archive").s3.enabled)
    }

    @Test
    fun `builds named instances programmatically`() {
        val options = BigDataTestKit.builder()
            .withInstance("analytics") {
                withKafka()
                withS3()
            }
            .options()

        val analytics = options.instances.getValue("analytics")
        assertTrue(analytics.kafka.enabled)
        assertTrue(analytics.s3.enabled)
        assertFalse(options.kafka.enabled)
        assertFalse(options.s3.enabled)
    }
}
