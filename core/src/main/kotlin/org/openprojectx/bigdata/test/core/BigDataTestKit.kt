package org.openprojectx.bigdata.test.core

import org.openprojectx.bigdata.test.core.container.BigDataContainerFactory
import org.testcontainers.lifecycle.Startable

class BigDataTestKit private constructor(
    private val options: BigDataTestKitOptions,
) : Startable, AutoCloseable {
    private val factory = BigDataContainerFactory(options)
    private val serviceContainers = factory.create()
    private val endpoints = linkedMapOf<BigDataService, BigDataEndpoint>()
    private var started = false

    override fun start() {
        if (started) return
        serviceContainers.forEach { it.container.start() }
        serviceContainers.forEach { endpoints[it.service] = it.endpoint() }
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
        private var containerLogs = ContainerLogOptions()

        fun withKerberos(options: KerberosOptions = KerberosOptions(enabled = true)): Builder =
            apply { kerberos = options.copy(enabled = true) }

        fun withTls(options: TlsOptions = TlsOptions(enabled = true)): Builder =
            apply { tls = options.copy(enabled = true) }

        fun withHdfs(options: HdfsOptions = HdfsOptions(enabled = true)): Builder =
            apply { hdfs = options.copy(enabled = true) }

        fun withHiveMetastore(options: HiveMetastoreOptions = HiveMetastoreOptions(enabled = true)): Builder =
            apply { hiveMetastore = options.copy(enabled = true) }

        fun withKafka(options: KafkaOptions = KafkaOptions(enabled = true)): Builder =
            apply { kafka = options.copy(enabled = true) }

        fun withLocalStackS3(options: ObjectStoreOptions = ObjectStoreOptions(enabled = true)): Builder =
            apply { localStackS3 = options.copy(enabled = true) }

        fun withFakeGcs(options: ObjectStoreOptions = ObjectStoreOptions(enabled = true, image = "fsouza/fake-gcs-server:1.54")): Builder =
            apply { fakeGcs = options.copy(enabled = true) }

        fun withContainerLogs(options: ContainerLogOptions): Builder =
            apply { containerLogs = options }

        fun withContainerLogsToStdout(): Builder =
            withContainerLogs(ContainerLogOptions(mode = ContainerLogMode.STDOUT))

        fun withContainerLogsToDirectory(directory: String = "build/bigdata-test-container-logs"): Builder =
            withContainerLogs(ContainerLogOptions(mode = ContainerLogMode.FILE, directory = directory))

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
                    containerLogs = containerLogs,
                ),
            )
    }
}
