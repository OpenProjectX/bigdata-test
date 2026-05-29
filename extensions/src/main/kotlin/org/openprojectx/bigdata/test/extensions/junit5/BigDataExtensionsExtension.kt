package org.openprojectx.bigdata.test.extensions.junit5

import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.openprojectx.bigdata.test.extensions.config.BigDataExtensionsConfigLoader
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
        val extensions = BigDataExtensionsConfigLoader(resources).load(annotation.value.asIterable())
        val runner = BigDataExtensionRunner(extensions, resources)
        val kit = BigDataTestKitStore.get(context)
        val result = runner.fire(BigDataExtensionEvent.AFTER_KIT_START, kit)
        val state = State(runner, result)
        context.store.put(STATE_KEY, state)
        context.store.put(RESULT_KEY, result)
        return result
    }

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
