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
            catalogName = "catalog_test"
            warehouse = "s3://catalog/warehouse"
            realm = "TEST_REALM"
            clientId = "test-client"
            clientSecret = "test-secret"
            scope = "PRINCIPAL_ROLE:ALL"
            s3RoleArn = "arn:aws:iam::000000000000:role/iceberg"
            s3ExternalId = "catalog-test"
            startupTimeoutSeconds = 240

            [ports]
            icebergRestCatalog = 19001

            [containers.icebergRestCatalog.env]
            CUSTOM_CATALOG_SETTING = "enabled"
            """.trimIndent(),
        )

        val options = BigDataTestConfigLoader().load(configFile.toString()).toTestKitOptions()

        assertTrue(options.icebergRestCatalog.enabled)
        assertEquals(DEFAULT_ICEBERG_REST_CATALOG_IMAGE, options.icebergRestCatalog.image)
        assertEquals("catalog_test", options.icebergRestCatalog.catalogName)
        assertEquals("s3://catalog/warehouse", options.icebergRestCatalog.warehouse)
        assertEquals("TEST_REALM", options.icebergRestCatalog.realm)
        assertEquals("test-client", options.icebergRestCatalog.clientId)
        assertEquals("test-secret", options.icebergRestCatalog.clientSecret)
        assertEquals("PRINCIPAL_ROLE:ALL", options.icebergRestCatalog.scope)
        assertEquals("arn:aws:iam::000000000000:role/iceberg", options.icebergRestCatalog.s3RoleArn)
        assertEquals("catalog-test", options.icebergRestCatalog.s3ExternalId)
        assertEquals(240, options.icebergRestCatalog.startupTimeoutSeconds)
        assertEquals(19001, options.portBindings.icebergRestCatalog)
        assertEquals(
            "enabled",
            options.containerCustomizations.getValue(BigDataService.ICEBERG_REST_CATALOG)
                .environment.getValue("CUSTOM_CATALOG_SETTING"),
        )
    }
}
