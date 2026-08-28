package org.openprojectx.bigdata.test.core

/** Address of a big-data service as seen by containers attached to the same test-kit network. */
data class BigDataContainerEndpoint(
    val service: BigDataService,
    val host: String,
    val ports: Map<String, Int>,
    val instance: String = DEFAULT_SERVICE_INSTANCE,
) {
    val id: BigDataServiceId get() = BigDataServiceId(service, instance)

    fun port(name: String): Int =
        ports[name] ?: error("Service $id does not expose a container port named '$name'")

    @JvmOverloads
    fun uri(scheme: String, portName: String, path: String = ""): String {
        require(scheme.isNotBlank()) { "URI scheme must not be blank" }
        require(path.isEmpty() || path.startsWith('/')) { "URI path must be empty or start with '/'" }
        return "$scheme://$host:${port(portName)}$path"
    }
}
