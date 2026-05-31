package org.openprojectx.bigdata.test.extensions.core

data class BigDataExtensionResult(
    val outputs: Map<String, String>,
) {
    fun required(key: String): String = outputs[key] ?: error("Missing bigdata-test extension output '$key'")

    fun optional(key: String): String? = outputs[key]
}
