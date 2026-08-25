package org.openprojectx.bigdata.test.core

import java.util.function.Consumer
import org.openprojectx.bigdata.test.core.container.BigDataContainerFactory
import org.openprojectx.bigdata.test.core.container.BigDataServiceContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.lifecycle.Startable

class BigDataTestKit private constructor(
    private val options: BigDataTestKitOptions,
) : Startable, AutoCloseable {
    private data class ServiceStack(
        val name: String,
        val factory: BigDataContainerFactory,
        val containers: List<BigDataServiceContainer>,
    )

    private val stacks = buildList {
        add(serviceStack(DEFAULT_SERVICE_INSTANCE, options.copy(instances = emptyMap())))
        options.instances.forEach { (name, instanceOptions) ->
            requireValidServiceInstanceName(name)
            require(name != DEFAULT_SERVICE_INSTANCE) {
                "Named service instances cannot use the reserved name '$DEFAULT_SERVICE_INSTANCE'"
            }
            require(instanceOptions.instances.isEmpty()) {
                "Nested service instances are not supported (found instances inside '$name')"
            }
            add(serviceStack(name, instanceOptions))
        }
    }
    private val endpoints = linkedMapOf<BigDataServiceId, BigDataEndpoint>()
    private var started = false

    override fun start() {
        if (started) return
        stacks.forEach { stack ->
            stack.containers.forEach { serviceContainer ->
                serviceContainer.container.start()
                serviceContainer.afterStart()
                val endpoint = serviceContainer.endpoint().copy(instance = stack.name)
                endpoints[endpoint.id] = endpoint
                stack.factory.healthCheck(serviceContainer.service, serviceContainer.container, endpoint)
            }
        }
        started = true
    }

    override fun stop() {
        close()
    }

    override fun close() {
        stacks.asReversed().forEach { stack ->
            stack.containers.asReversed().forEach { it.container.stop() }
            stack.factory.close()
        }
        endpoints.clear()
        started = false
    }

    fun endpoint(service: BigDataService): BigDataEndpoint = endpoint(service, DEFAULT_SERVICE_INSTANCE)

    fun endpoint(service: BigDataService, instance: String): BigDataEndpoint =
        endpoint(BigDataServiceId(service, instance))

    fun endpoint(id: BigDataServiceId): BigDataEndpoint =
        endpoints[id] ?: error("Service $id has not been started")

    /** Returns endpoints in the default instance for source compatibility. */
    fun endpoints(): Map<BigDataService, BigDataEndpoint> =
        endpoints.values
            .filter { it.instance == DEFAULT_SERVICE_INSTANCE }
            .associateByTo(linkedMapOf()) { it.service }

    fun endpoints(service: BigDataService): Map<String, BigDataEndpoint> =
        endpoints.values
            .filter { it.service == service }
            .associateByTo(linkedMapOf()) { it.instance }

    fun allEndpoints(): Map<BigDataServiceId, BigDataEndpoint> = endpoints.toMap()

    /**
     * Keeps legacy client properties for the default instance and adds collision-free
     * `bigdata.test.instances.<instance>...` properties for every named instance.
     */
    fun springProperties(): Map<String, String> = buildMap {
        endpoints.values
            .filter { it.instance == DEFAULT_SERVICE_INSTANCE }
            .forEach { putAll(it.properties) }
        endpoints.values
            .filter { it.instance != DEFAULT_SERVICE_INSTANCE }
            .forEach { endpoint -> putAll(endpoint.namespacedProperties()) }
    }

    private fun serviceStack(name: String, stackOptions: BigDataTestKitOptions): ServiceStack {
        val factory = BigDataContainerFactory(stackOptions, name)
        return ServiceStack(name, factory, factory.create())
    }

    private fun BigDataEndpoint.namespacedProperties(): Map<String, String> = buildMap {
        val prefix = "bigdata.test.instances.$instance.${service.propertyName()}"
        put("$prefix.host", host)
        ports.forEach { (name, port) -> put("$prefix.ports.$name", port.toString()) }
        properties.forEach { (name, value) -> put("$prefix.properties.$name", value) }
    }

    private fun BigDataService.propertyName(): String = name.lowercase().replace('_', '-')

    companion object {
        @JvmStatic
        fun builder(): Builder = Builder()
    }

    class Builder {
        private var kerberos = KerberosOptions()
        private var tls = TlsOptions()
        private var hdfs = HdfsOptions()
        private var hiveMetastore = HiveMetastoreOptions()
        private var kafka = KafkaOptions()
        private var s3 = ObjectStoreOptions()
        private var fakeGcs = ObjectStoreOptions(image = DEFAULT_FAKE_GCS_IMAGE)
        private var icebergRestCatalog = IcebergRestCatalogOptions()
        private var portBindings = PortBindingOptions()
        private var containerLogs = ContainerLogOptions()
        private var containerCustomizations = emptyMap<BigDataService, ContainerCustomizationOptions>()
        private var healthChecks = emptyMap<BigDataService, BigDataHealthCheckOptions>()
        private val instances = linkedMapOf<String, BigDataTestKitOptions>()

        fun withKerberos(options: KerberosOptions = KerberosOptions(enabled = true)): Builder =
            apply { kerberos = options.copy(enabled = true) }

        fun withTls(options: TlsOptions = TlsOptions(enabled = true)): Builder =
            apply { tls = options.copy(enabled = true) }

        fun withHdfs(options: HdfsOptions = HdfsOptions(enabled = true)): Builder =
            apply { hdfs = options.copy(enabled = true) }

        fun withHiveMetastore(options: HiveMetastoreOptions = HiveMetastoreOptions(enabled = true)): Builder =
            apply { hiveMetastore = options.copy(enabled = true) }

        fun withClouderaHms(
            options: HiveMetastoreOptions = HiveMetastoreOptions(
                enabled = true,
                distribution = HiveMetastoreDistribution.CLOUDERA,
                image = HiveMetastoreOptions.DEFAULT_CLOUDERA_IMAGE,
                warehouseDir = HiveMetastoreOptions.DEFAULT_CLOUDERA_WAREHOUSE_DIR,
            ),
        ): Builder =
            apply { hiveMetastore = options.copy(enabled = true, distribution = HiveMetastoreDistribution.CLOUDERA) }

        fun withKafka(options: KafkaOptions = KafkaOptions(enabled = true)): Builder =
            apply { kafka = options.copy(enabled = true) }

        fun withS3(options: ObjectStoreOptions = ObjectStoreOptions(enabled = true)): Builder =
            apply { s3 = options.copy(enabled = true) }

        fun withFakeGcs(options: ObjectStoreOptions = ObjectStoreOptions(enabled = true, image = DEFAULT_FAKE_GCS_IMAGE)): Builder =
            apply { fakeGcs = options.copy(enabled = true) }

        fun withIcebergRestCatalog(
            options: IcebergRestCatalogOptions = IcebergRestCatalogOptions(enabled = true),
        ): Builder = apply { icebergRestCatalog = options.copy(enabled = true) }

        fun withPortBindings(options: PortBindingOptions): Builder = apply { portBindings = options }

        fun withSameHostPorts(): Builder = apply { portBindings = portBindings.copy(sameHostPorts = true) }

        fun withContainerLogs(options: ContainerLogOptions): Builder = apply { containerLogs = options }

        fun withContainerLogsToStdout(): Builder =
            withContainerLogs(ContainerLogOptions(mode = ContainerLogMode.STDOUT))

        fun withContainerLogsToDirectory(directory: String = "build/bigdata-test-container-logs"): Builder =
            withContainerLogs(ContainerLogOptions(mode = ContainerLogMode.FILE, directory = directory))

        fun withContainerCustomization(service: BigDataService, options: ContainerCustomizationOptions): Builder =
            apply { containerCustomizations = containerCustomizations.merge(service, options) }

        fun withContainerNetworkMode(service: BigDataService, networkMode: String): Builder =
            withContainerCustomization(service, ContainerCustomizationOptions(networkMode = networkMode))

        fun withContainerEnv(service: BigDataService, name: String, value: String): Builder =
            withContainerCustomization(service, ContainerCustomizationOptions(environment = mapOf(name to value)))

        fun withContainerLogLevel(service: BigDataService, level: String): Builder =
            withContainerCustomization(
                service,
                ContainerCustomizationOptions(environment = BigDataContainerLogLevels.environment(service, level)),
            )

        fun withContainerFile(service: BigDataService, file: ContainerFileTransferOptions): Builder =
            withContainerCustomization(service, ContainerCustomizationOptions(files = listOf(file)))

        fun withContainerMount(service: BigDataService, mount: ContainerMountOptions): Builder =
            withContainerCustomization(service, ContainerCustomizationOptions(mounts = listOf(mount)))

        fun withContainerPort(service: BigDataService, port: ContainerPortOptions): Builder =
            withContainerCustomization(service, ContainerCustomizationOptions(ports = listOf(port)))

        fun customizeContainer(service: BigDataService, customizer: BigDataContainerCustomizer): Builder =
            withContainerCustomization(service, ContainerCustomizationOptions(customizers = listOf(customizer)))

        fun customizeContainer(service: BigDataService, customizer: Consumer<GenericContainer<*>>): Builder =
            customizeContainer(service, BigDataContainerCustomizer { customizer.accept(it) })

        fun withHealthCheck(service: BigDataService, options: BigDataHealthCheckOptions): Builder =
            apply { healthChecks = healthChecks + (service to options) }

        fun withCliHealthCheck(service: BigDataService): Builder =
            withHealthCheck(service, BigDataHealthCheckOptions(mode = BigDataHealthCheckMode.CLI))

        fun withCliHealthChecks(): Builder = apply {
            BigDataService.entries.forEach { withCliHealthCheck(it) }
        }

        fun withInstance(name: String, options: BigDataTestKitOptions): Builder = apply {
            validateInstance(name, options)
            instances[name] = options
        }

        @JvmSynthetic
        fun withInstance(name: String, configure: Builder.() -> Unit): Builder = apply {
            val child = Builder().apply(configure)
            withInstance(name, child.buildOptions())
        }

        fun withInstance(name: String, configure: Consumer<Builder>): Builder = apply {
            val child = Builder().also { configure.accept(it) }
            withInstance(name, child.buildOptions())
        }

        fun build(): BigDataTestKit = BigDataTestKit(buildOptions())

        fun options(): BigDataTestKitOptions = buildOptions()

        internal fun buildOptions(): BigDataTestKitOptions = BigDataTestKitOptions(
            kerberos = kerberos,
            tls = tls,
            hdfs = hdfs,
            hiveMetastore = hiveMetastore,
            kafka = kafka,
            s3 = s3,
            fakeGcs = fakeGcs,
            icebergRestCatalog = icebergRestCatalog,
            portBindings = portBindings,
            containerLogs = containerLogs,
            containerCustomizations = containerCustomizations,
            healthChecks = healthChecks,
            instances = instances.toMap(),
        )

        private fun validateInstance(name: String, options: BigDataTestKitOptions) {
            requireValidServiceInstanceName(name)
            require(name != DEFAULT_SERVICE_INSTANCE) {
                "Named service instances cannot use the reserved name '$DEFAULT_SERVICE_INSTANCE'"
            }
            require(options.instances.isEmpty()) { "Nested service instances are not supported" }
        }

        private fun Map<BigDataService, ContainerCustomizationOptions>.merge(
            service: BigDataService,
            options: ContainerCustomizationOptions,
        ): Map<BigDataService, ContainerCustomizationOptions> =
            this + (service to (this[service]?.merge(options) ?: options))
    }
}
