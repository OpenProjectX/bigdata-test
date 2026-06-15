package org.openprojectx.bigdata.test.junit5

import org.junit.jupiter.api.extension.ExtensionContext
import org.openprojectx.bigdata.test.core.BigDataTestKit

object BigDataJunitContext {
    @JvmStatic
    fun getKit(context: ExtensionContext): BigDataTestKit =
        BigDataTestKitStore.get(context)

    @JvmStatic
    fun getKitOrNull(context: ExtensionContext): BigDataTestKit? =
        BigDataTestKitStore.getOrNull(context)
}
