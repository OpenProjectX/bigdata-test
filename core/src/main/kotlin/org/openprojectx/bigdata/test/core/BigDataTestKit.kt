package org.openprojectx.bigdata.test.core

import org.openprojectx.bigdata.test.core.container.BigDataContainerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.lifecycle.Startable
import java.util.function.Consumer

class BigDataTestKit private constructor(
    private val options: BigDataTestKitOptions,
) : Startable, AutoCloseable {
    private val factory = BigDataContainerFactory(options)
    private val serviceContainers = factory.create()
    private val endpoints = linkedMapOf<BigDataService, BigDataEndpoint>()
    private var started = false

    override fun start() {
        if (started) return
        serviceContainers.forEach {
            it.container.start()
            it.afterStart()
            val endpoint = it.endpoint()
            endpoints[it.service] = endpoint
            factory.healthCheck(it.service, it.container, endpoint)
        }
        started = true
    }

    override fun stop() {
        close()
    }

    override fun close() {
        serviceContainers.asReversed().forEach { it.container.stop() }
        factory.close()
        endpoints.clear()
        started = false
    }

    fun endpoint(service: BigDataService): BigDataEndpoint =
        endpoints[service] ?: error("Service $service has not been started")

    fun endpoints(): Map<BigDataService, BigDataEndpoint> = endpoints.toMap()

    fun springProperties(): Map<String, String> =
        endpoints.values.flatMap { endpoint ->
            endpoint.properties.map { (key, value) -> key to value }
        }.toMap()

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
        private var localStackS3 = ObjectStoreOptions()
        private var fakeGcs = ObjectStoreOptions(image = "fsouza/fake-gcs-server:1.54")
        private var portBindings = PortBindingOptions()
        private var containerLogs = ContainerLogOptions()
        private var containerCustomizations = emptyMap<BigDataService, ContainerCustomizationOptions>()
        private var healthChecks = emptyMap<BigDataService, BigDataHealthCheckOptions>()

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

        fun withLocalStackS3(options: ObjectStoreOptions = ObjectStoreOptions(enabled = true)): Builder =
            apply { localStackS3 = options.copy(enabled = true) }

        fun withFakeGcs(options: ObjectStoreOptions = ObjectStoreOptions(enabled = true, image = "fsouza/fake-gcs-server:1.54")): Builder =
            apply { fakeGcs = options.copy(enabled = true) }

        fun withPortBindings(options: PortBindingOptions): Builder =
            apply { portBindings = options }

        fun withSameHostPorts(): Builder =
            apply { portBindings = portBindings.copy(sameHostPorts = true) }

        fun withContainerLogs(options: ContainerLogOptions): Builder =
            apply { containerLogs = options }

        fun withContainerLogsToStdout(): Builder =
            withContainerLogs(ContainerLogOptions(mode = ContainerLogMode.STDOUT))

        fun withContainerLogsToDirectory(directory: String = "build/bigdata-test-container-logs"): Builder =
            withContainerLogs(ContainerLogOptions(mode = ContainerLogMode.FILE, directory = directory))

        fun withContainerCustomization(service: BigDataService, options: ContainerCustomizationOptions): Builder =
            apply { containerCustomizations = containerCustomizations.merge(service, options) }

        fun withContainerNetworkMode(service: BigDataService, networkMode: String): Builder =
            withContainerCustomization(service, ContainerCustomizationOptions(networkMode = networkMode))

        fun withContainerEnv(service: BigDataService, name: String, value: String): Builder =
            withContainerCustomization(
                service,
                ContainerCustomizationOptions(environment = mapOf(name to value)),
            )

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

        fun withCliHealthChecks(): Builder =
            apply {
                BigDataService.entries.forEach {
                    withCliHealthCheck(it)
                }
            }

        fun build(): BigDataTestKit =
            BigDataTestKit(
                BigDataTestKitOptions(
                    kerberos = kerberos,
                    tls = tls,
                    hdfs = hdfs,
                    hiveMetastore = hiveMetastore,
                    kafka = kafka,
                    localStackS3 = localStackS3,
                    fakeGcs = fakeGcs,
                    portBindings = portBindings,
                    containerLogs = containerLogs,
                    containerCustomizations = containerCustomizations,
                    healthChecks = healthChecks,
                ),
            )

        private fun Map<BigDataService, ContainerCustomizationOptions>.merge(
            service: BigDataService,
            options: ContainerCustomizationOptions,
        ): Map<BigDataService, ContainerCustomizationOptions> =
            this + (service to (this[service]?.merge(options) ?: options))
    }
}
