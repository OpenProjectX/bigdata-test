package org.openprojectx.bigdata.test.core

data class BigDataEndpoint(
    val service: BigDataService,
    val host: String,
    val ports: Map<String, Int>,
    val properties: Map<String, String> = emptyMap(),
    val instance: String = DEFAULT_SERVICE_INSTANCE,
) {
    val id: BigDataServiceId get() = BigDataServiceId(service, instance)

    fun port(name: String): Int =
        ports[name] ?: error("Service $id does not expose a port named '$name'")

    fun property(name: String): String =
        properties[name] ?: error("Service $id does not expose a property named '$name'")
}
