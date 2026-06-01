package org.openprojectx.bigdata.test.junit5

import org.junit.jupiter.api.extension.ExtensionContext
import org.openprojectx.bigdata.test.core.BigDataTestKit
import javax.net.ssl.SSLContext

object BigDataTestKitStore {
    private const val KIT_KEY = "kit"
    private const val SYSTEM_PROPERTIES_KEY = "systemProperties"
    private const val SSL_CONTEXT_KEY = "sslContext"

    fun put(context: ExtensionContext, kit: BigDataTestKit) {
        context.store.put(KIT_KEY, kit)
    }

    fun putSystemProperties(context: ExtensionContext, previousValues: Map<String, String?>) {
        context.store.put(SYSTEM_PROPERTIES_KEY, previousValues)
    }

    fun removeSystemProperties(context: ExtensionContext): Map<String, String?> =
        context.store.remove(SYSTEM_PROPERTIES_KEY, Map::class.java)
            ?.mapKeys { it.key as String }
            ?.mapValues { it.value as String? }
            .orEmpty()

    fun putSslContext(context: ExtensionContext, sslContext: SSLContext) {
        context.store.put(SSL_CONTEXT_KEY, sslContext)
    }

    fun removeSslContext(context: ExtensionContext): SSLContext? =
        context.store.remove(SSL_CONTEXT_KEY, SSLContext::class.java)

    fun get(context: ExtensionContext): BigDataTestKit =
        getOrNull(context) ?: error("BigDataTestKit has not been started")

    fun getOrNull(context: ExtensionContext): BigDataTestKit? =
        context.store.get(KIT_KEY, BigDataTestKit::class.java)

    fun remove(context: ExtensionContext): BigDataTestKit? =
        context.store.remove(KIT_KEY, BigDataTestKit::class.java)

    private val ExtensionContext.store: ExtensionContext.Store
        get() = getStore(ExtensionContext.Namespace.create(BigDataTestExtension::class.java, requiredTestClass))
}
