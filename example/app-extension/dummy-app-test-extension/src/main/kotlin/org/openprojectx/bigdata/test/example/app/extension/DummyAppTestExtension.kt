package org.openprojectx.bigdata.test.example.app.extension

import org.junit.jupiter.api.Order
import org.junit.jupiter.api.extension.AfterTestExecutionCallback
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.openprojectx.bigdata.test.example.app.framework.DummyApp
import org.openprojectx.bigdata.test.example.app.framework.DummyAppConfig
import org.openprojectx.bigdata.test.example.app.framework.DummyAppFactory
import org.openprojectx.bigdata.test.extensions.junit5.BigDataExtensionResultStore
import org.openprojectx.bigdata.test.junit5.BigDataJunitContext

@Order(200)
class DummyAppTestExtension : BeforeTestExecutionCallback, AfterTestExecutionCallback, ParameterResolver {
    override fun beforeTestExecution(context: ExtensionContext) {
        val annotation = context.requiredTestClass.getAnnotation(DummyAppTest::class.java)
            ?: error("@DummyAppTest is missing")
        val kit = BigDataJunitContext.getKit(context)
        val extensionResult = BigDataExtensionResultStore.getOrNull(context)
        val config = customizer(annotation, context).customize(
            kit,
            extensionResult,
            defaultConfig(kit.endpoints().size, kit.springProperties(), extensionResult?.outputs.orEmpty()),
        )
        val app = DummyAppFactory.create(config)
        if (annotation.autoStart) {
            app.start()
        }
        context.store.put(APP_KEY, app)
        context.store.put(CONFIG_KEY, config)
    }

    override fun afterTestExecution(context: ExtensionContext) {
        val annotation = context.requiredTestClass.getAnnotation(DummyAppTest::class.java)
            ?: return
        val app = context.store.remove(APP_KEY, DummyApp::class.java) ?: return
        if (annotation.autoStop) {
            app.stop()
        }
        context.store.remove(CONFIG_KEY, DummyAppConfig::class.java)
    }

    override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean =
        parameterContext.parameter.type == DummyApp::class.java ||
            parameterContext.parameter.type == DummyAppConfig::class.java

    override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any =
        when (parameterContext.parameter.type) {
            DummyApp::class.java -> extensionContext.store.get(APP_KEY, DummyApp::class.java)
                ?: error("DummyApp has not been created")
            DummyAppConfig::class.java -> extensionContext.store.get(CONFIG_KEY, DummyAppConfig::class.java)
                ?: error("DummyAppConfig has not been created")
            else -> error("Unsupported parameter ${parameterContext.parameter}")
        }

    private fun defaultConfig(
        serviceCount: Int,
        endpointProperties: Map<String, String>,
        extensionOutputs: Map<String, String>,
    ): DummyAppConfig {
        val properties = buildMap {
            put("dummy.bigdata.services", serviceCount.toString())
            endpointProperties.forEach { (key, value) ->
                put("dummy.bigdata.endpoint.$key", value)
            }
            extensionOutputs.forEach { (key, value) ->
                put("dummy.bigdata.extension.$key", value)
            }
        }
        val yaml = buildString {
            appendLine("dummy:")
            appendLine("  bigdata:")
            appendLine("    services: $serviceCount")
            if (extensionOutputs.isNotEmpty()) {
                appendLine("    extensionOutputs:")
                extensionOutputs.forEach { (key, value) ->
                    appendLine("      $key: \"$value\"")
                }
            }
        }
        return DummyAppConfig(yaml = yaml, properties = properties)
    }

    private fun customizer(
        annotation: DummyAppTest,
        context: ExtensionContext,
    ): DummyAppConfigCustomizer =
        explicitCustomizer(annotation)
            ?: companionCustomizer(context)
            ?: instanceCustomizer(context)
            ?: NoopDummyAppConfigCustomizer

    private fun explicitCustomizer(annotation: DummyAppTest): DummyAppConfigCustomizer? {
        val type = annotation.customizer.java
        if (type == NoopDummyAppConfigCustomizer::class.java) return null
        return type.getDeclaredConstructor().also { it.isAccessible = true }.newInstance()
    }

    private fun companionCustomizer(context: ExtensionContext): DummyAppConfigCustomizer? =
        runCatching {
            context.requiredTestClass.getDeclaredField("Companion")
                .also { it.isAccessible = true }
                .get(null) as? DummyAppConfigCustomizer
        }.getOrNull()

    private fun instanceCustomizer(context: ExtensionContext): DummyAppConfigCustomizer? =
        context.testInstance.orElse(null) as? DummyAppConfigCustomizer

    private val ExtensionContext.store: ExtensionContext.Store
        get() = getStore(ExtensionContext.Namespace.create(DummyAppTestExtension::class.java, requiredTestClass))

    private companion object {
        const val APP_KEY = "app"
        const val CONFIG_KEY = "config"
    }
}
