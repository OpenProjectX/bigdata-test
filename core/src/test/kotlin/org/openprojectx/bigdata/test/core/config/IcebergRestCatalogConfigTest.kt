package org.openprojectx.bigdata.test.core.config

import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.openprojectx.bigdata.test.core.BigDataService
import org.openprojectx.bigdata.test.core.DEFAULT_ICEBERG_REST_CATALOG_IMAGE

class IcebergRestCatalogConfigTest {
    @Test
    fun `loads iceberg REST catalog options and customizations`() {
        val configFile = Files.createTempFile("bigdata-test-iceberg-rest-", ".toml")
        Files.writeString(
            configFile,
            """
            [services]
            icebergRestCatalog = true

            [icebergRestCatalog]
            warehouse = "s3://catalog/warehouse"
            ioImpl = "org.apache.iceberg.aws.s3.S3FileIO"

            [ports]
            icebergRestCatalog = 19001

            [containers.icebergRestCatalog.env]
            CUSTOM_CATALOG_SETTING = "enabled"
            """.trimIndent(),
        )

        val options = BigDataTestConfigLoader().load(configFile.toString()).toTestKitOptions()

        assertTrue(options.icebergRestCatalog.enabled)
        assertEquals(DEFAULT_ICEBERG_REST_CATALOG_IMAGE, options.icebergRestCatalog.image)
        assertEquals("s3://catalog/warehouse", options.icebergRestCatalog.warehouse)
        assertEquals("org.apache.iceberg.aws.s3.S3FileIO", options.icebergRestCatalog.ioImpl)
        assertEquals(19001, options.portBindings.icebergRestCatalog)
        assertEquals(
            "enabled",
            options.containerCustomizations.getValue(BigDataService.ICEBERG_REST_CATALOG)
                .environment.getValue("CUSTOM_CATALOG_SETTING"),
        )
    }
}
