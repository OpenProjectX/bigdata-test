package org.openprojectx.bigdata.test.extensions.core

import org.openprojectx.bigdata.test.core.BigDataTestKit

class BigDataExtensionRunner(
    private val extensions: List<BigDataExtension>,
    private val resources: BigDataExtensionResourceLoader = BigDataExtensionResourceLoader(),
) {
    fun fire(event: BigDataExtensionEvent, kit: BigDataTestKit, previous: BigDataExtensionResult? = null): BigDataExtensionResult {
        val outputs = previous?.outputs?.toMutableMap() ?: linkedMapOf()
        extensions.filter { event in it.events }.forEach { extension ->
            validateServices(extension, kit)
            val context = BigDataExtensionContext(
                kit = kit,
                resources = resources,
                instance = extension.instance,
                mutableOutputs = outputs,
            )
            extension.onEvent(event, context)
        }
        return BigDataExtensionResult(outputs.toMap())
    }

    private fun validateServices(extension: BigDataExtension, kit: BigDataTestKit) {
        val available = kit.allEndpoints().keys
        val missing = extension.requiredServiceInstances - available
        check(missing.isEmpty()) {
            "BigData extension '${extension.id}' requires service instances $missing, but available service " +
                "instances are $available"
        }
    }
}
