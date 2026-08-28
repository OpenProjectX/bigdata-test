package org.openprojectx.bigdata.test.extensions.trino

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.openprojectx.bigdata.test.core.BigDataService
import org.openprojectx.bigdata.test.extensions.config.BigDataExtensionsBuilder
import org.openprojectx.bigdata.test.extensions.config.BigDataExtensionsConfigLoader

class TrinoSqlPreparationExtensionTest {
    @Test
    fun `loads TOML and programmatic Trino SQL extensions`() {
        val configured = BigDataExtensionsConfigLoader().load("classpath:trino-sql-prep-extension.toml").single()
        val builder = BigDataExtensionsBuilder()
        builder.trinoSqlPreparation {
            id = "programmatic-trino"
            statement("CREATE VIEW demo.events_view AS SELECT * FROM demo.events")
        }
        val programmatic = builder.build().single()

        assertEquals("prepare-trino-views", configured.id)
        assertEquals(setOf(BigDataService.TRINO), configured.requiredServices)
        assertEquals("programmatic-trino", programmatic.id)
        assertTrue(programmatic is TrinoSqlPreparationExtension)
    }
}
