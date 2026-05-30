package org.openprojectx.bigdata.test.extensions.junit5

import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.openprojectx.bigdata.test.extensions.config.BigDataExtensionsBuilder
import org.openprojectx.bigdata.test.extensions.config.BigDataExtensionsConfigLoader
import org.openprojectx.bigdata.test.extensions.config.BigDataExtensionsConfigurer
import org.openprojectx.bigdata.test.extensions.config.NoopBigDataExtensionsConfigurer
import org.openprojectx.bigdata.test.extensions.core.BigDataExtension
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionEvent
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionResult
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionRunner
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionResourceLoader
import org.openprojectx.bigdata.test.junit5.BigDataTestKitStore

class BigDataExtensionsExtension : BeforeTestExecutionCallback, AfterAllCallback, ParameterResolver {
    override fun beforeTestExecution(context: ExtensionContext) {
        ensureApplied(context)
    }

    override fun afterAll(context: ExtensionContext) {
        val state = context.store.remove(STATE_KEY, State::class.java) ?: return
        BigDataTestKitStore.getOrNull(context)?.let { kit ->
            val result = state.runner.fire(BigDataExtensionEvent.AFTER_ALL, kit, state.result)
            context.store.put(RESULT_KEY, result)
        }
    }

    override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean =
        parameterContext.parameter.type == BigDataExtensionResult::class.java

    override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any =
        ensureApplied(extensionContext)

    private fun ensureApplied(context: ExtensionContext): BigDataExtensionResult {
        context.store.get(RESULT_KEY, BigDataExtensionResult::class.java)?.let { return it }
        val annotation = context.requiredTestClass.getAnnotation(BigDataExtensions::class.java)
            ?: error("@BigDataExtensions is missing")
        val resources = BigDataExtensionResourceLoader(context.requiredTestClass.classLoader)
        val extensions = mergeById(
            BigDataExtensionsConfigLoader(resources).load(annotation.value.asIterable()) +
                programmaticExtensions(annotation, context),
        )
        val runner = BigDataExtensionRunner(extensions, resources)
        val kit = BigDataTestKitStore.get(context)
        val result = runner.fire(BigDataExtensionEvent.AFTER_KIT_START, kit)
        val state = State(runner, result)
        context.store.put(STATE_KEY, state)
        context.store.put(RESULT_KEY, result)
        return result
    }

    private fun programmaticExtensions(
        annotation: BigDataExtensions,
        context: ExtensionContext,
    ): List<BigDataExtension> {
        val builder = BigDataExtensionsBuilder()
        explicitConfigurer(annotation)?.configure(builder)
        companionConfigurer(context)?.configure(builder)
        instanceConfigurer(context)?.configure(builder)
        return builder.build()
    }

    private fun mergeById(extensions: List<BigDataExtension>): List<BigDataExtension> {
        val merged = linkedMapOf<String, BigDataExtension>()
        extensions.forEach { extension ->
            if (merged.containsKey(extension.id)) {
                merged.remove(extension.id)
            }
            merged[extension.id] = extension
        }
        return merged.values.toList()
    }

    private fun explicitConfigurer(annotation: BigDataExtensions): BigDataExtensionsConfigurer? {
        val type = annotation.configurer.java
        if (type == NoopBigDataExtensionsConfigurer::class.java) return null
        return type.getDeclaredConstructor().also { it.isAccessible = true }.newInstance()
    }

    private fun companionConfigurer(context: ExtensionContext): BigDataExtensionsConfigurer? =
        runCatching {
            context.requiredTestClass.getDeclaredField("Companion")
                .also { it.isAccessible = true }
                .get(null) as? BigDataExtensionsConfigurer
        }.getOrNull()

    private fun instanceConfigurer(context: ExtensionContext): BigDataExtensionsConfigurer? =
        context.testInstance.orElse(null) as? BigDataExtensionsConfigurer

    private val ExtensionContext.store: ExtensionContext.Store
        get() = getStore(ExtensionContext.Namespace.create(BigDataExtensionsExtension::class.java, requiredTestClass))

    private data class State(
        val runner: BigDataExtensionRunner,
        val result: BigDataExtensionResult,
    )

    private companion object {
        const val STATE_KEY = "state"
        const val RESULT_KEY = "result"
    }
}
