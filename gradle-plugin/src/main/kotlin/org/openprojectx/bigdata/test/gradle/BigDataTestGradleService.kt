package org.openprojectx.bigdata.test.gradle

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.openprojectx.bigdata.test.core.BigDataService
import org.openprojectx.bigdata.test.core.BigDataTestKit
import org.openprojectx.bigdata.test.core.ContainerLogMode
import org.openprojectx.bigdata.test.core.ContainerLogOptions
import org.openprojectx.bigdata.test.core.ContainerPortOptions
import org.openprojectx.bigdata.test.core.HdfsOptions
import org.openprojectx.bigdata.test.core.HiveMetastoreDistribution
import org.openprojectx.bigdata.test.core.HiveMetastoreOptions
import org.openprojectx.bigdata.test.core.HttpTlsOptions
import org.openprojectx.bigdata.test.core.KafkaOptions
import org.openprojectx.bigdata.test.core.KerberosAuthOptions
import org.openprojectx.bigdata.test.core.KerberosOptions
import org.openprojectx.bigdata.test.core.ObjectStoreOptions
import org.openprojectx.bigdata.test.core.PortBindingOptions
import org.openprojectx.bigdata.test.core.TlsOptions
import org.openprojectx.bigdata.test.extensions.config.BigDataExtensionsConfigLoader
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionEvent
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionResourceLoader
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionResult
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionRunner

abstract class BigDataTestGradleService : BuildService<BigDataTestGradleService.Parameters>, AutoCloseable {
    interface Parameters : BuildServiceParameters {
        val enabled: Property<Boolean>
        val injectRawEndpointProperties: Property<Boolean>
        val injectNamespacedEndpointProperties: Property<Boolean>
        val injectEnvironmentVariables: Property<Boolean>
        val extensionConfig: ListProperty<String>
        val containerLogLevels: MapProperty<String, String>

        val kerberos: Property<Boolean>
        val hdfs: Property<Boolean>
        val hiveMetastore: Property<Boolean>
        val clouderaHms: Property<Boolean>
        val kafka: Property<Boolean>
        val schemaRegistry: Property<Boolean>
        val kafkaUi: Property<Boolean>
        val localStackS3: Property<Boolean>
        val fakeGcs: Property<Boolean>

        val sameHostPorts: Property<Boolean>
        val kerberosKdcPort: Property<Int>
        val hdfsNameNodePort: Property<Int>
        val hdfsDataNodePort: Property<Int>
        val hdfsWebPort: Property<Int>
        val hiveMetastorePort: Property<Int>
        val kafkaPort: Property<Int>
        val schemaRegistryPort: Property<Int>
        val kafkaUiPort: Property<Int>
        val localStackS3Port: Property<Int>
        val fakeGcsPort: Property<Int>

        val kerberosImage: Property<String>
        val kerberosRealm: Property<String>
        val kerberosDomain: Property<String>
        val kerberosClientPrincipal: Property<String>
        val kerberosClientPassword: Property<String>
        val kerberosStartupTimeoutSeconds: Property<Int>
        val kerberosMaterialTimeoutSeconds: Property<Int>
        val kerberosAdminAttempts: Property<Int>
        val kerberosAdminRetryDelaySeconds: Property<Int>
        val kerberosDebug: Property<Boolean>

        val tlsEnabled: Property<Boolean>
        val tlsCaCertPath: Property<String>
        val tlsCaKeyPath: Property<String>
        val tlsTrustStorePath: Property<String>
        val tlsTrustStorePassword: Property<String>
        val tlsHaproxyImage: Property<String>

        val hdfsImage: Property<String>
        val hdfsKerberosEnabled: Property<Boolean>
        val hdfsDataNodeHostname: Property<String>

        val hiveMetastoreImage: Property<String>
        val hiveMetastoreDatabaseImage: Property<String>
        val hiveMetastoreDatabaseName: Property<String>
        val hiveMetastoreDatabaseUser: Property<String>
        val hiveMetastoreDatabasePassword: Property<String>
        val hiveMetastoreWarehouseDir: Property<String>
        val hiveMetastoreKerberosEnabled: Property<Boolean>

        val clouderaHmsImage: Property<String>
        val clouderaHmsWarehouseDir: Property<String>
        val clouderaHmsKerberosEnabled: Property<Boolean>

        val kafkaImage: Property<String>
        val schemaRegistryImage: Property<String>
        val kafkaUiImage: Property<String>
        val kafkaKerberosEnabled: Property<Boolean>
        val kafkaUiKerberosEnabled: Property<Boolean>

        val localStackS3Image: Property<String>
        val fakeGcsImage: Property<String>

        val containerLogMode: Property<ContainerLogMode>
        val containerLogDirectory: Property<String>
    }

    private var kit: BigDataTestKit? = null
    private var runner: BigDataExtensionRunner? = null
    private var result: BigDataExtensionResult = BigDataExtensionResult(emptyMap())

    @Synchronized
    fun startIfNeeded(): Map<String, String> {
        if (!parameters.enabled.get()) return emptyMap()
        if (kit == null) {
            val created = buildKit()
            created.start()
            kit = created
            val resources = BigDataExtensionResourceLoader(Thread.currentThread().contextClassLoader)
            val extensions = BigDataExtensionsConfigLoader(resources).load(parameters.extensionConfig.get())
            runner = BigDataExtensionRunner(extensions, resources)
            result = runner?.fire(BigDataExtensionEvent.AFTER_KIT_START, created) ?: BigDataExtensionResult(emptyMap())
        }
        return injectedProperties()
    }

    fun injectedEnvironmentVariables(properties: Map<String, String>): Map<String, String> {
        if (!parameters.injectEnvironmentVariables.get()) return emptyMap()
        return properties.mapKeys { (key, _) -> key.toEnvironmentVariableName() }
    }

    @Synchronized
    override fun close() {
        val current = kit ?: return
        try {
            runner?.let { result = it.fire(BigDataExtensionEvent.AFTER_ALL, current, result) }
        } finally {
            current.close()
            kit = null
            runner = null
            result = BigDataExtensionResult(emptyMap())
        }
    }

    private fun buildKit(): BigDataTestKit {
        require(!(parameters.hiveMetastore.get() && parameters.clouderaHms.get())) {
            "Use only one HMS implementation: hiveMetastore or clouderaHms"
        }
        val builder = BigDataTestKit.builder()
            .withPortBindings(
                PortBindingOptions(
                    sameHostPorts = parameters.sameHostPorts.get(),
                    kerberosKdc = parameters.kerberosKdcPort.get(),
                    hdfsNameNode = parameters.hdfsNameNodePort.get(),
                    hdfsDataNode = parameters.hdfsDataNodePort.get(),
                    hdfsWeb = parameters.hdfsWebPort.get(),
                    hiveMetastore = parameters.hiveMetastorePort.get(),
                    kafka = parameters.kafkaPort.get(),
                    schemaRegistry = parameters.schemaRegistryPort.get(),
                    kafkaUi = parameters.kafkaUiPort.get(),
                    localStackS3 = parameters.localStackS3Port.get(),
                    fakeGcs = parameters.fakeGcsPort.get(),
                ),
            )
            .withContainerLogs(
                ContainerLogOptions(
                    mode = parameters.containerLogMode.get(),
                    directory = parameters.containerLogDirectory.get(),
                ),
            )

        if (parameters.tlsEnabled.get()) {
            builder.withTls(
                TlsOptions(
                    enabled = true,
                    caCertPath = parameters.tlsCaCertPath.get().ifBlank { null },
                    caKeyPath = parameters.tlsCaKeyPath.get().ifBlank { null },
                    trustStorePath = parameters.tlsTrustStorePath.get().ifBlank { null },
                    trustStorePassword = parameters.tlsTrustStorePassword.get(),
                    haproxyImage = parameters.tlsHaproxyImage.get(),
                ),
            )
        }
        if (parameters.kerberos.get()) {
            builder.withKerberos(kerberosOptions())
        }
        if (parameters.hdfs.get()) {
            builder.withHdfs(
                HdfsOptions(
                    enabled = true,
                    image = parameters.hdfsImage.get(),
                    dataNodeHostname = parameters.hdfsDataNodeHostname.get(),
                    kerberos = KerberosAuthOptions(
                        enabled = parameters.hdfsKerberosEnabled.get(),
                        servicePrincipal = "nn/hdfs.${parameters.kerberosDomain.get()}@${parameters.kerberosRealm.get()}",
                        keytabPath = "/kerby/keytabs/hdfs-namenode.keytab",
                    ),
                ),
            )
        }
        if (parameters.hiveMetastore.get()) {
            builder.withHiveMetastore(
                HiveMetastoreOptions(
                    enabled = true,
                    distribution = HiveMetastoreDistribution.OPEN_SOURCE,
                    image = parameters.hiveMetastoreImage.get(),
                    databaseImage = parameters.hiveMetastoreDatabaseImage.get(),
                    databaseName = parameters.hiveMetastoreDatabaseName.get(),
                    databaseUser = parameters.hiveMetastoreDatabaseUser.get(),
                    databasePassword = parameters.hiveMetastoreDatabasePassword.get(),
                    warehouseDir = parameters.hiveMetastoreWarehouseDir.get(),
                    kerberos = KerberosAuthOptions(
                        enabled = parameters.hiveMetastoreKerberosEnabled.get(),
                        servicePrincipal = "hive/hive-metastore.${parameters.kerberosDomain.get()}@${parameters.kerberosRealm.get()}",
                        keytabPath = "/kerby/keytabs/hive-metastore.keytab",
                    ),
                ),
            )
        }
        if (parameters.clouderaHms.get()) {
            builder.withClouderaHms(
                HiveMetastoreOptions(
                    enabled = true,
                    distribution = HiveMetastoreDistribution.CLOUDERA,
                    image = parameters.clouderaHmsImage.get(),
                    warehouseDir = parameters.clouderaHmsWarehouseDir.get(),
                    kerberos = KerberosAuthOptions(
                        enabled = parameters.clouderaHmsKerberosEnabled.get(),
                        servicePrincipal = "hive/hive-metastore.${parameters.kerberosDomain.get()}@${parameters.kerberosRealm.get()}",
                        keytabPath = "/kerby/keytabs/hive-metastore.keytab",
                    ),
                ),
            )
        }
        if (parameters.kafka.get()) {
            builder.withKafka(
                KafkaOptions(
                    enabled = true,
                    image = parameters.kafkaImage.get(),
                    schemaRegistryEnabled = parameters.schemaRegistry.get(),
                    schemaRegistryImage = parameters.schemaRegistryImage.get(),
                    kafkaUiEnabled = parameters.kafkaUi.get(),
                    kafkaUiImage = parameters.kafkaUiImage.get(),
                    kerberos = KerberosAuthOptions(
                        enabled = parameters.kafkaKerberosEnabled.get(),
                        servicePrincipal = "kafka/localhost@${parameters.kerberosRealm.get()}",
                        keytabPath = "/kerby/keytabs/kafka-broker1.keytab",
                    ),
                    kafkaUiKerberos = KerberosAuthOptions(
                        enabled = parameters.kafkaUiKerberosEnabled.get(),
                        servicePrincipal = "kafbat-ui/kafbat-ui.${parameters.kerberosDomain.get()}@${parameters.kerberosRealm.get()}",
                        keytabPath = "/kerby/keytabs/kafbat-ui.keytab",
                    ),
                ),
            )
        }
        if (parameters.localStackS3.get()) {
            builder.withLocalStackS3(ObjectStoreOptions(enabled = true, image = parameters.localStackS3Image.get()))
        }
        if (parameters.fakeGcs.get()) {
            builder.withFakeGcs(ObjectStoreOptions(enabled = true, image = parameters.fakeGcsImage.get()))
        }
        parameters.containerLogLevels.get().forEach { (service, level) ->
            builder.withContainerLogLevel(service.toBigDataService(), level)
        }
        return builder.build()
    }

    private fun kerberosOptions(): KerberosOptions =
        KerberosOptions(
            enabled = true,
            image = parameters.kerberosImage.get(),
            realm = parameters.kerberosRealm.get(),
            domain = parameters.kerberosDomain.get(),
            clientPrincipal = parameters.kerberosClientPrincipal.get(),
            clientPassword = parameters.kerberosClientPassword.get(),
            startupTimeoutSeconds = parameters.kerberosStartupTimeoutSeconds.get(),
            materialTimeoutSeconds = parameters.kerberosMaterialTimeoutSeconds.get(),
            adminAttempts = parameters.kerberosAdminAttempts.get(),
            adminRetryDelaySeconds = parameters.kerberosAdminRetryDelaySeconds.get(),
            debug = parameters.kerberosDebug.get(),
        )

    private fun injectedProperties(): Map<String, String> {
        val current = kit ?: return emptyMap()
        val properties = linkedMapOf<String, String>()
        if (parameters.injectRawEndpointProperties.get()) {
            properties += current.springProperties()
        }
        if (parameters.injectNamespacedEndpointProperties.get()) {
            current.endpoints().forEach { (service, endpoint) ->
                val serviceName = service.propertyName()
                properties["bigdata.test.endpoint.$serviceName.host"] = endpoint.host
                endpoint.ports.forEach { (name, port) ->
                    properties["bigdata.test.endpoint.$serviceName.ports.$name"] = port.toString()
                }
                endpoint.properties.forEach { (key, value) ->
                    properties["bigdata.test.endpoint.$serviceName.properties.$key"] = value
                }
            }
            result.outputs.forEach { (key, value) ->
                properties["bigdata.test.extensions.$key"] = value
            }
        }
        return properties
    }

    private fun String.toBigDataService(): BigDataService =
        BigDataService.entries.firstOrNull { service ->
            service.name.equals(this, ignoreCase = true) ||
                service.name.replace("_", "-").equals(this, ignoreCase = true) ||
                service.name.replace("_", "").equals(this, ignoreCase = true)
        } ?: error("Unknown bigdata-test service '$this'")

    private fun BigDataService.propertyName(): String =
        name.lowercase().replace('_', '-')

    private fun String.toEnvironmentVariableName(): String =
        uppercase()
            .map { char -> if (char.isLetterOrDigit()) char else '_' }
            .joinToString("")
            .replace(Regex("_+"), "_")
            .trim('_')
}
