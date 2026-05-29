package org.openprojectx.bigdata.test.extensions.core

import kotlinx.serialization.json.JsonObject

interface BigDataExtensionProvider {
    val type: String

    fun create(config: JsonObject, resources: BigDataExtensionResourceLoader): BigDataExtension
}
