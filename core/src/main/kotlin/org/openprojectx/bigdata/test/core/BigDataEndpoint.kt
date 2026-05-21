package org.openprojectx.bigdata.test.core

data class BigDataEndpoint(
    val service: BigDataService,
    val host: String,
    val ports: Map<String, Int>,
    val properties: Map<String, String> = emptyMap(),
) {
    fun port(name: String): Int =
        ports[name] ?: error("Service $service does not expose a port named '$name'")

    fun property(name: String): String =
        properties[name] ?: error("Service $service does not expose a property named '$name'")
}
