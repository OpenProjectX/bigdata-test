package org.openprojectx.bigdata.test.core.config

import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.openprojectx.bigdata.test.core.BigDataService

class TrinoConfigTest {
    @Test
    fun `loads Trino image port catalog and customization options`() {
        val configFile = Files.createTempFile("bigdata-test-trino-", ".toml")
        Files.writeString(
            configFile,
            """
            [images]
            trino = "example/trino:test"

            [services]
            trino = true

            [trino]
            catalogName = "analytics"
            startupTimeoutSeconds = 240

            [trino.catalogProperties]
            "hive.metastore.thrift.client.read-timeout" = "90s"

            [ports]
            trino = 18180

            [containers.trino.env]
            CUSTOM_TRINO_SETTING = "enabled"
            """.trimIndent(),
        )

        val options = BigDataTestConfigLoader().load(configFile.toString()).toTestKitOptions()

        assertTrue(options.trino.enabled)
        assertTrue(options.hiveMetastore.enabled)
        assertEquals("example/trino:test", options.trino.image)
        assertEquals("analytics", options.trino.catalogName)
        assertEquals(240, options.trino.startupTimeoutSeconds)
        assertEquals("90s", options.trino.catalogProperties["hive.metastore.thrift.client.read-timeout"])
        assertEquals(18180, options.portBindings.trino)
        assertEquals(
            "enabled",
            options.containerCustomizations.getValue(BigDataService.TRINO)
                .environment.getValue("CUSTOM_TRINO_SETTING"),
        )
    }
}
