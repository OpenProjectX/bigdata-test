package org.openprojectx.bigdata.test.gradle

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.net.URLClassLoader
import org.openprojectx.bigdata.test.core.BigDataService
import org.openprojectx.bigdata.test.core.BigDataTestKit
import org.openprojectx.bigdata.test.core.ContainerCustomizationOptions
import org.openprojectx.bigdata.test.core.ContainerFileTransferOptions
import org.openprojectx.bigdata.test.core.ContainerLogMode
import org.openprojectx.bigdata.test.core.ContainerLogOptions
import org.openprojectx.bigdata.test.core.ContainerMountOptions
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
import java.nio.file.Path

abstract class BigDataTestGradleService : BuildService<BigDataTestGradleService.Parameters>, AutoCloseable {
    interface Parameters : BuildServiceParameters {
        val projectDirectory: Property<String>
        val enabled: Property<Boolean>
        val injectRawEndpointProperties: Property<Boolean>
        val injectNamespacedEndpointProperties: Property<Boolean>
        val injectEnvironmentVariables: Property<Boolean>
        val extensionConfig: ListProperty<String>
        val extensionRuntimeClasspath: ConfigurableFileCollection
        val containerLogLevels: MapProperty<String, String>
        val containerCustomizations: ListProperty<String>

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
        val kerberosMaterialDirectory: Property<String>
        val kerberosLocalKrb5ConfPath: Property<String>
        val kerberosLocalClientKeytabPath: Property<String>
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
        val hdfsLocalHdfsSitePath: Property<String>

        val hiveMetastoreImage: Property<String>
        val hiveMetastoreDatabaseImage: Property<String>
        val hiveMetastoreDatabaseName: Property<String>
        val hiveMetastoreDatabaseUser: Property<String>
        val hiveMetastoreDatabasePassword: Property<String>
        val hiveMetastoreWarehouseDir: Property<String>
        val hiveMetastoreLocalHiveSitePath: Property<String>
        val hiveMetastoreLocalMetastoreSitePath: Property<String>
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
        val containerLogAppend: Property<Boolean>
    }

    private var kit: BigDataTestKit? = null
    private var extensionRuntime: ExtensionRuntime? = null
    private var extensionResult: Any? = null
    private var extensionOutputs: Map<String, String> = emptyMap()

    @Synchronized
    fun startIfNeeded(): Map<String, String> {
        if (!parameters.enabled.get()) return emptyMap()
        if (kit == null) {
            val created = buildKit()
            created.start()
            kit = created
            extensionRuntime = ExtensionRuntime(parameters.extensionRuntimeClasspath.files)
            extensionRuntime?.let { runtime ->
                extensionResult = runtime.fireAfterKitStart(parameters.extensionConfig.get(), created)
                extensionOutputs = runtime.outputs(extensionResult)
            }
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
            extensionRuntime?.let { runtime ->
                extensionResult = runtime.fireAfterAll(current, extensionResult)
                extensionOutputs = runtime.outputs(extensionResult)
            }
        } finally {
            current.close()
            extensionRuntime?.close()
            kit = null
            extensionRuntime = null
            extensionResult = null
            extensionOutputs = emptyMap()
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
                    append = parameters.containerLogAppend.get(),
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
                    localHdfsSitePath = projectPath(parameters.hdfsLocalHdfsSitePath.get()),
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
                    localHiveSitePath = projectPath(parameters.hiveMetastoreLocalHiveSitePath.get()),
                    localMetastoreSitePath = projectPath(parameters.hiveMetastoreLocalMetastoreSitePath.get()),
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
                    localHiveSitePath = projectPath(parameters.hiveMetastoreLocalHiveSitePath.get()),
                    localMetastoreSitePath = projectPath(parameters.hiveMetastoreLocalMetastoreSitePath.get()),
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
        decodeContainerCustomizations(parameters.containerCustomizations.get()).forEach { (service, customization) ->
            builder.withContainerCustomization(service, customization)
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
            materialDirectory = projectPath(parameters.kerberosMaterialDirectory.get()),
            localKrb5ConfPath = projectPath(parameters.kerberosLocalKrb5ConfPath.get()),
            localClientKeytabPath = projectPath(parameters.kerberosLocalClientKeytabPath.get()),
            startupTimeoutSeconds = parameters.kerberosStartupTimeoutSeconds.get(),
            materialTimeoutSeconds = parameters.kerberosMaterialTimeoutSeconds.get(),
            adminAttempts = parameters.kerberosAdminAttempts.get(),
            adminRetryDelaySeconds = parameters.kerberosAdminRetryDelaySeconds.get(),
            debug = parameters.kerberosDebug.get(),
        )

    private fun projectPath(value: String): String? {
        if (value.isBlank()) return null
        val path = Path.of(value)
        return if (path.isAbsolute) {
            path.toString()
        } else {
            Path.of(parameters.projectDirectory.get()).resolve(path).normalize().toString()
        }
    }

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
            extensionOutputs.forEach { (key, value) ->
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

    private fun decodeContainerCustomizations(encoded: List<String>): Map<BigDataService, ContainerCustomizationOptions> {
        val customizations = linkedMapOf<BigDataService, ContainerCustomizationOptions>()
        encoded.forEach { item ->
            val parts = decode(item)
            require(parts.size >= 3) { "Invalid encoded container customization" }
            val kind = parts[0]
            val service = parts[1].toBigDataService()
            val options = when (kind) {
                "network" -> {
                    require(parts.size == 3) { "Invalid encoded network container customization" }
                    ContainerCustomizationOptions(networkMode = parts[2])
                }
                "env" -> {
                    require(parts.size == 4) { "Invalid encoded env container customization" }
                    ContainerCustomizationOptions(environment = mapOf(parts[2] to parts[3]))
                }
                "file" -> {
                    require(parts.size == 6) { "Invalid encoded file container customization" }
                    val fileMode = parts[5].takeIf { it.isNotBlank() }?.toInt()
                    val file = if (parts[3].isNotBlank()) {
                        ContainerFileTransferOptions.hostPath(parts[3], parts[2], fileMode)
                    } else {
                        ContainerFileTransferOptions.content(
                            java.util.Base64.getDecoder().decode(parts[4]),
                            parts[2],
                            fileMode,
                        )
                    }
                    ContainerCustomizationOptions(files = listOf(file))
                }
                "mount" -> {
                    require(parts.size == 5) { "Invalid encoded mount container customization" }
                    ContainerCustomizationOptions(
                        mounts = listOf(
                            ContainerMountOptions(
                                hostPath = parts[2],
                                containerPath = parts[3],
                                readOnly = parts[4].toBooleanStrict(),
                            ),
                        ),
                    )
                }
                "port" -> {
                    require(parts.size == 4) { "Invalid encoded port container customization" }
                    ContainerCustomizationOptions(
                        ports = listOf(ContainerPortOptions(containerPort = parts[2].toInt(), hostPort = parts[3].toInt())),
                    )
                }
                else -> error("Unknown encoded container customization kind '$kind'")
            }
            customizations[service] = customizations[service]?.merge(options) ?: options
        }
        return customizations
    }

    private fun BigDataService.propertyName(): String =
        name.lowercase().replace('_', '-')

    private fun String.toEnvironmentVariableName(): String =
        uppercase()
            .map { char -> if (char.isLetterOrDigit()) char else '_' }
            .joinToString("")
            .replace(Regex("_+"), "_")
            .trim('_')

    private class ExtensionRuntime(files: Set<java.io.File>) : AutoCloseable {
        private val classLoader = URLClassLoader(
            files.map { it.toURI().toURL() }.toTypedArray(),
            BigDataTestGradleService::class.java.classLoader,
        )
        private var runner: RunnerHandle? = null

        fun fireAfterKitStart(config: List<String>, kit: BigDataTestKit): Any? =
            if (config.isEmpty()) null else withRuntimeClassLoader {
                val resources = newResources()
                val extensions = newConfigLoader(resources).load(config)
                runner = newRunner(extensions, resources)
                runner?.fire(event("AFTER_KIT_START"), kit, null)
            }

        fun fireAfterAll(kit: BigDataTestKit, previous: Any?): Any? =
            previous?.let { result ->
                withRuntimeClassLoader {
                    runner?.fire(event("AFTER_ALL"), kit, result)
                }
            }

        fun outputs(result: Any?): Map<String, String> {
            if (result == null) return emptyMap()
            val value = result.javaClass.getMethod("getOutputs").invoke(result)
            @Suppress("UNCHECKED_CAST")
            return value as Map<String, String>
        }

        override fun close() {
            classLoader.close()
        }

        private fun newResources(): Any =
            classLoader
                .loadClass("org.openprojectx.bigdata.test.extensions.core.BigDataExtensionResourceLoader")
                .getConstructor(ClassLoader::class.java)
                .newInstance(classLoader)

        private fun newConfigLoader(resources: Any): ConfigLoaderHandle {
            val providerClass = classLoader
                .loadClass("org.openprojectx.bigdata.test.extensions.core.BigDataExtensionProvider")
            val loader = classLoader
                .loadClass("org.openprojectx.bigdata.test.extensions.config.BigDataExtensionsConfigLoader")
                .constructors
                .first { it.parameterCount == 2 }
                .newInstance(resources, java.util.ServiceLoader.load(providerClass, classLoader))
            return ConfigLoaderHandle(loader)
        }

        private fun newRunner(extensions: List<Any>, resources: Any): RunnerHandle {
            val runner = classLoader
                .loadClass("org.openprojectx.bigdata.test.extensions.core.BigDataExtensionRunner")
                .constructors
                .first { it.parameterCount == 2 }
                .newInstance(extensions, resources)
            return RunnerHandle(runner)
        }

        private fun event(name: String): Any =
            classLoader
                .loadClass("org.openprojectx.bigdata.test.extensions.core.BigDataExtensionEvent")
                .enumConstants
                .first { (it as Enum<*>).name == name }

        private fun <T> withRuntimeClassLoader(action: () -> T): T {
            val thread = Thread.currentThread()
            val previous = thread.contextClassLoader
            thread.contextClassLoader = classLoader
            return try {
                action()
            } finally {
                thread.contextClassLoader = previous
            }
        }

        private class ConfigLoaderHandle(private val delegate: Any) {
            fun load(locations: List<String>): List<Any> {
                val value = delegate.javaClass.getMethod("load", Iterable::class.java).invoke(delegate, locations)
                @Suppress("UNCHECKED_CAST")
                return value as List<Any>
            }
        }

        private class RunnerHandle(private val delegate: Any) {
            fun fire(event: Any, kit: BigDataTestKit, previous: Any?): Any =
                delegate.javaClass
                    .methods
                    .first { it.name == "fire" && it.parameterCount == 3 }
                    .invoke(delegate, event, kit, previous)
        }
    }
}
