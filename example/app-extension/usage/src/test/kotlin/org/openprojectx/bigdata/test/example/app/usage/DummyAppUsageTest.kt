package org.openprojectx.bigdata.test.example.app.usage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.openprojectx.bigdata.test.example.app.extension.DummyAppConfigCustomizer
import org.openprojectx.bigdata.test.example.app.extension.DummyAppTest
import org.openprojectx.bigdata.test.example.app.framework.DummyApp
import org.openprojectx.bigdata.test.example.app.framework.DummyAppConfig
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionResult
import org.openprojectx.bigdata.test.extensions.junit5.BigDataExtensions
import org.openprojectx.bigdata.test.core.BigDataTestKit
import org.openprojectx.bigdata.test.junit5.BigDataTest

@BigDataTest
@BigDataExtensions
@DummyAppTest
class DummyAppUsageTest : DummyAppConfigCustomizer {
    override fun customize(
        kit: BigDataTestKit,
        extensionResult: BigDataExtensionResult?,
        config: DummyAppConfig,
    ): DummyAppConfig =
        config.copy(
            properties = config.properties + ("dummy.project.name" to "usage-example"),
        )

    @Test
    fun `injects started app with config derived from bigdata context`(
        app: DummyApp,
        config: DummyAppConfig,
    ) {
        assertTrue(app.started)
        assertEquals("usage-example", app.property("dummy.project.name"))
        assertEquals(config, app.config)
    }
}
