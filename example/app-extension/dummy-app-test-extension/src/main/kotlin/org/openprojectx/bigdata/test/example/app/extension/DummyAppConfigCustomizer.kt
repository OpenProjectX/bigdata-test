package org.openprojectx.bigdata.test.example.app.extension

import org.openprojectx.bigdata.test.core.BigDataTestKit
import org.openprojectx.bigdata.test.example.app.framework.DummyAppConfig
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionResult

fun interface DummyAppConfigCustomizer {
    fun customize(
        kit: BigDataTestKit,
        extensionResult: BigDataExtensionResult?,
        config: DummyAppConfig,
    ): DummyAppConfig
}
