package org.openprojectx.bigdata.test.extensions.core

import org.openprojectx.bigdata.test.core.BigDataEndpoint
import org.openprojectx.bigdata.test.core.BigDataService
import org.openprojectx.bigdata.test.core.BigDataTestKit
import org.openprojectx.bigdata.test.core.DEFAULT_SERVICE_INSTANCE

class BigDataExtensionContext(
    val kit: BigDataTestKit,
    val resources: BigDataExtensionResourceLoader = BigDataExtensionResourceLoader(),
    val instance: String = DEFAULT_SERVICE_INSTANCE,
    private val mutableOutputs: MutableMap<String, String> = linkedMapOf(),
) {
    val outputs: Map<String, String> get() = mutableOutputs.toMap()

    fun endpoint(service: BigDataService): BigDataEndpoint =
        if (instance == DEFAULT_SERVICE_INSTANCE) kit.endpoint(service) else kit.endpoint(service, instance)

    fun endpointOrNull(service: BigDataService): BigDataEndpoint? =
        if (instance == DEFAULT_SERVICE_INSTANCE) kit.endpoints()[service] else kit.endpoints(service)[instance]

    fun putOutput(key: String, value: String) {
        mutableOutputs[key] = value
    }
}
