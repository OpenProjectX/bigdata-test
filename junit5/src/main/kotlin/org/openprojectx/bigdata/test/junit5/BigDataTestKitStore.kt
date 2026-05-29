package org.openprojectx.bigdata.test.junit5

import org.junit.jupiter.api.extension.ExtensionContext
import org.openprojectx.bigdata.test.core.BigDataTestKit

object BigDataTestKitStore {
    private const val KIT_KEY = "kit"

    fun put(context: ExtensionContext, kit: BigDataTestKit) {
        context.store.put(KIT_KEY, kit)
    }

    fun get(context: ExtensionContext): BigDataTestKit =
        getOrNull(context) ?: error("BigDataTestKit has not been started")

    fun getOrNull(context: ExtensionContext): BigDataTestKit? =
        context.store.get(KIT_KEY, BigDataTestKit::class.java)

    fun remove(context: ExtensionContext): BigDataTestKit? =
        context.store.remove(KIT_KEY, BigDataTestKit::class.java)

    private val ExtensionContext.store: ExtensionContext.Store
        get() = getStore(ExtensionContext.Namespace.create(BigDataTestExtension::class.java, requiredTestClass))
}
