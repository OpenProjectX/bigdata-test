package org.openprojectx.bigdata.test.extensions.core

import org.openprojectx.bigdata.test.core.BigDataTestKit

class BigDataExtensionRunner(
    private val extensions: List<BigDataExtension>,
    private val resources: BigDataExtensionResourceLoader = BigDataExtensionResourceLoader(),
) {
    fun fire(event: BigDataExtensionEvent, kit: BigDataTestKit, previous: BigDataExtensionResult? = null): BigDataExtensionResult {
        val outputs = previous?.outputs?.toMutableMap() ?: linkedMapOf()
        val context = BigDataExtensionContext(kit = kit, resources = resources, mutableOutputs = outputs)
        extensions.filter { event in it.events }.forEach { extension ->
            validateServices(extension, kit)
            extension.onEvent(event, context)
        }
        return BigDataExtensionResult(context.outputs)
    }

    private fun validateServices(extension: BigDataExtension, kit: BigDataTestKit) {
        val available = kit.endpoints().keys
        val missing = extension.requiredServices - available
        check(missing.isEmpty()) {
            "BigData extension '${extension.id}' requires services $missing, but available services are $available"
        }
    }
}
