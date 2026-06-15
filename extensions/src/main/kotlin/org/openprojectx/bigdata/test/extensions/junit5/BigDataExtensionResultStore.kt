package org.openprojectx.bigdata.test.extensions.junit5

import org.junit.jupiter.api.extension.ExtensionContext
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionResult

object BigDataExtensionResultStore {
    private const val RESULT_KEY = "result"

    @JvmStatic
    fun put(context: ExtensionContext, result: BigDataExtensionResult) {
        context.store.put(RESULT_KEY, result)
    }

    @JvmStatic
    fun get(context: ExtensionContext): BigDataExtensionResult =
        getOrNull(context) ?: error("BigDataExtensionResult has not been created")

    @JvmStatic
    fun getOrNull(context: ExtensionContext): BigDataExtensionResult? =
        context.store.get(RESULT_KEY, BigDataExtensionResult::class.java)

    private val ExtensionContext.store: ExtensionContext.Store
        get() = getStore(ExtensionContext.Namespace.create(BigDataExtensionsExtension::class.java, requiredTestClass))
}
