package org.openprojectx.bigdata.test.extensions.core

import org.openprojectx.bigdata.test.core.BigDataEndpoint
import org.openprojectx.bigdata.test.core.BigDataService
import org.openprojectx.bigdata.test.core.BigDataTestKit

class BigDataExtensionContext(
    val kit: BigDataTestKit,
    val resources: BigDataExtensionResourceLoader = BigDataExtensionResourceLoader(),
    private val mutableOutputs: MutableMap<String, String> = linkedMapOf(),
) {
    val outputs: Map<String, String> get() = mutableOutputs.toMap()

    fun endpoint(service: BigDataService): BigDataEndpoint = kit.endpoint(service)

    fun putOutput(key: String, value: String) {
        mutableOutputs[key] = value
    }
}
