package org.openprojectx.bigdata.test.junit5

import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.openprojectx.bigdata.test.core.HdfsOptions
import org.openprojectx.bigdata.test.core.HttpTlsOptions
import org.openprojectx.bigdata.test.core.ClouderaHmsDatabaseType
import org.openprojectx.bigdata.test.core.HiveMetastoreDatabaseType
import org.openprojectx.bigdata.test.core.HiveMetastoreOptions
import org.openprojectx.bigdata.test.core.HiveMetastoreDistribution
import org.openprojectx.bigdata.test.core.BigDataTestKit
import org.openprojectx.bigdata.test.core.ContainerLogMode
import org.openprojectx.bigdata.test.core.ContainerLogOptions
import org.openprojectx.bigdata.test.core.DEFAULT_FAKE_GCS_IMAGE
import org.openprojectx.bigdata.test.core.DEFAULT_HAPROXY_IMAGE
import org.openprojectx.bigdata.test.core.DEFAULT_HDFS_IMAGE
import org.openprojectx.bigdata.test.core.DEFAULT_KAFKA_IMAGE
import org.openprojectx.bigdata.test.core.DEFAULT_KAFKA_UI_IMAGE
import org.openprojectx.bigdata.test.core.DEFAULT_KERBEROS_IMAGE
import org.openprojectx.bigdata.test.core.DEFAULT_S3_IMAGE
import org.openprojectx.bigdata.test.core.DEFAULT_SCHEMA_REGISTRY_IMAGE
import org.openprojectx.bigdata.test.core.KafkaOptions
import org.openprojectx.bigdata.test.core.KerberosAuthOptions
import org.openprojectx.bigdata.test.core.KerberosOptions
import org.openprojectx.bigdata.test.core.ObjectStoreOptions
import org.openprojectx.bigdata.test.core.PortBindingOptions
import org.openprojectx.bigdata.test.core.TlsOptions
import org.openprojectx.bigdata.test.core.config.BigDataTestConfigLoader
import org.openprojectx.bigdata.test.core.config.BigDataTestHttpTlsConfig
import org.openprojectx.bigdata.test.core.config.toTestKitOptions

class BigDataTestExtension : BeforeAllCallback, AfterAllCallback, ParameterResolver {
    override fun beforeAll(context: ExtensionContext) {
        val annotation = context.requiredTestClass.getAnnotation(BigDataTest::class.java)
            ?: return
        val kit = kitFrom(annotation, context)
        kit.start()
        installJvmTlsProperties(context, kit)
        BigDataTestKitStore.put(context, kit)
    }

    override fun afterAll(context: ExtensionContext) {
        restoreJvmTlsProperties(context)
        BigDataTestKitStore.remove(context)?.close()
    }

    override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean =
        parameterContext.parameter.type == BigDataTestKit::class.java

    override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any =
        BigDataTestKitStore.get(extensionContext)

    private fun kitFrom(annotation: BigDataTest, context: ExtensionContext): BigDataTestKit {
        val configLocations = bigDataTestConfigLocations(annotation, context)
        val config = BigDataTestConfigLoader(context.requiredTestClass.classLoader).load(configLocations)
        val images = config.images
        val services = config.services
        val kerberosConfig = config.kerberos
        val ports = config.ports
        val tls = config.tls
        val containerLogs = config.containerLogs
        val builder = BigDataTestKit.builder()
            .withPortBindings(
                PortBindingOptions(
                    sameHostPorts = annotation.sameHostPorts || (ports.sameHostPorts ?: false),
                    kerberosKdc = annotation.kerberosKdcPort.takeIfPositive() ?: ports.kerberosKdc ?: 0,
                    hdfsNameNode = annotation.hdfsNameNodePort.takeIfPositive() ?: ports.hdfsNameNode ?: 0,
                    hdfsDataNode = annotation.hdfsDataNodePort.takeIfPositive() ?: ports.hdfsDataNode ?: 0,
                    hdfsWeb = annotation.hdfsWebPort.takeIfPositive() ?: ports.hdfsWeb ?: 0,
                    hdfsWebTls = ports.hdfsWebTls ?: 0,
                    hiveMetastore = annotation.hiveMetastorePort.takeIfPositive() ?: ports.hiveMetastore ?: 0,
                    kafka = annotation.kafkaPort.takeIfPositive() ?: ports.kafka ?: 0,
                    schemaRegistry = annotation.schemaRegistryPort.takeIfPositive() ?: ports.schemaRegistry ?: 0,
                    schemaRegistryTls = ports.schemaRegistryTls ?: 0,
                    kafkaUi = annotation.kafkaUiPort.takeIfPositive() ?: ports.kafkaUi ?: 0,
                    kafkaUiTls = ports.kafkaUiTls ?: 0,
                    s3 = annotation.s3Port.takeIfPositive() ?: ports.s3 ?: 0,
                    s3Tls = ports.s3Tls ?: 0,
                    fakeGcs = annotation.fakeGcsPort.takeIfPositive() ?: ports.fakeGcs ?: 0,
                    fakeGcsTls = ports.fakeGcsTls ?: 0,
                ),
            )
        val containerLogMode = if (annotation.containerLogMode != ContainerLogMode.NONE) {
            annotation.containerLogMode
        } else {
            containerLogs.mode ?: ContainerLogMode.NONE
        }
        val containerLogDirectory =
            if (annotation.containerLogDirectory != "build/bigdata-test-container-logs") {
                annotation.containerLogDirectory
            } else {
                containerLogs.directory ?: annotation.containerLogDirectory
            }
        if (containerLogMode != ContainerLogMode.NONE) {
            builder.withContainerLogs(
                ContainerLogOptions(
                    mode = containerLogMode,
                    directory = containerLogDirectory,
                    append = annotation.containerLogAppend && (containerLogs.append ?: true),
                ),
            )
        }
        config.containerLogLevels.forEach { (service, level) ->
            builder.withContainerLogLevel(service, level)
        }
        config.containerCustomizations.forEach { (service, customization) ->
            builder.withContainerCustomization(service, customization)
        }
        config.healthChecks.forEach { (service, healthCheck) ->
            builder.withHealthCheck(service, healthCheck)
        }
        val kerberos = annotation.kerberos || services.kerberos == true
        val hdfs = annotation.hdfs || services.hdfs == true
        val hdfsKerberos = annotation.hdfsKerberos || services.hdfsKerberos == true
        val hiveMetastore = annotation.hiveMetastore || services.hiveMetastore == true
        val clouderaHms = annotation.clouderaHms || services.clouderaHms == true
        val hiveMetastoreKerberos = annotation.hiveMetastoreKerberos || services.hiveMetastoreKerberos == true
        val hiveMetastoreTls = annotation.hiveMetastoreTls || config.hiveMetastoreTls.enabled == true
        val kafkaTls = annotation.kafkaTls || config.kafkaTls.enabled == true
        val kafka = annotation.kafka || services.kafka == true || kafkaTls
        val kafkaKerberos = annotation.kafkaKerberos || services.kafkaKerberos == true
        val schemaRegistry = annotation.schemaRegistry || services.schemaRegistry == true
        val kafkaUi = annotation.kafkaUi || services.kafkaUi == true
        val kafkaUiKerberos = annotation.kafkaUiKerberos || services.kafkaUiKerberos == true
        val s3 = annotation.s3 || services.s3 == true
        val fakeGcs = annotation.fakeGcs || services.fakeGcs == true
        val defaultKerberos = KerberosOptions()
        val kerberosRealm = kerberosConfig.realm ?: defaultKerberos.realm
        val kerberosDomain = kerberosConfig.domain ?: defaultKerberos.domain
        val tlsEnabled = tls.enabled == true ||
            config.hdfsWebTls.enabled == true ||
            hiveMetastoreTls ||
            kafkaTls ||
            config.schemaRegistryTls.enabled == true ||
            config.kafkaUiTls.enabled == true ||
            config.s3Tls.enabled == true ||
            config.fakeGcsTls.enabled == true
        if (tlsEnabled || tls.hasValues()) {
            builder.withTls(
                TlsOptions(
                    enabled = tlsEnabled,
                    caCertPath = tls.caCertPath,
                    caKeyPath = tls.caKeyPath,
                    trustStorePath = tls.trustStorePath,
                    trustStorePassword = tls.trustStorePassword ?: "changeit",
                    haproxyImage = tls.haproxyImage ?: DEFAULT_HAPROXY_IMAGE,
                ),
            )
        }

        if (kerberos || hdfsKerberos || hiveMetastoreKerberos || kafkaKerberos || kafkaUiKerberos) {
            builder.withKerberos(
                KerberosOptions(
                    enabled = true,
                    image = images.kerberos ?: DEFAULT_KERBEROS_IMAGE,
                    realm = kerberosRealm,
                    domain = kerberosDomain,
                    clientPrincipal = annotation.kerberosClientPrincipal
                        .takeIf { it != BigDataTestDefaults.KERBEROS_CLIENT_PRINCIPAL }
                        ?: kerberosConfig.clientPrincipal
                        ?: defaultKerberos.clientPrincipal,
                    clientPassword = annotation.kerberosClientPassword
                        .takeIf { it != BigDataTestDefaults.KERBEROS_CLIENT_PASSWORD }
                        ?: kerberosConfig.clientPassword
                        ?: defaultKerberos.clientPassword,
                    materialDirectory = kerberosConfig.materialDirectory,
                    localKrb5ConfPath = kerberosConfig.localKrb5ConfPath,
                    localClientKeytabPath = kerberosConfig.localClientKeytabPath,
                    startupTimeoutSeconds = kerberosConfig.startupTimeoutSeconds ?: defaultKerberos.startupTimeoutSeconds,
                    materialTimeoutSeconds = kerberosConfig.materialTimeoutSeconds ?: defaultKerberos.materialTimeoutSeconds,
                    adminAttempts = kerberosConfig.adminAttempts ?: defaultKerberos.adminAttempts,
                    adminRetryDelaySeconds = kerberosConfig.adminRetryDelaySeconds ?: defaultKerberos.adminRetryDelaySeconds,
                    debug = kerberosConfig.debug ?: defaultKerberos.debug,
                ),
            )
        }
        if (hdfs || hdfsKerberos) {
            builder.withHdfs(
                HdfsOptions(
                    enabled = true,
                    image = images.hdfs ?: DEFAULT_HDFS_IMAGE,
                    dataNodeHostname = annotation.hdfsDataNodeHostname.takeIf { it.isNotBlank() }
                        ?: config.hdfs.dataNodeHostname
                        ?: HdfsOptions().dataNodeHostname,
                    webTls = config.hdfsWebTls.toHttpTls("localhost"),
                    kerberos = KerberosAuthOptions(
                        enabled = hdfsKerberos,
                        servicePrincipal = "nn/hdfs.$kerberosDomain@$kerberosRealm",
                        keytabPath = "/kerby/keytabs/hdfs-namenode.keytab",
                    ),
                ),
            )
        }
        require(!(hiveMetastore && clouderaHms)) {
            "Use only one HMS implementation: hiveMetastore or clouderaHms"
        }
        if (hiveMetastore || clouderaHms || hiveMetastoreKerberos || hiveMetastoreTls) {
            val distribution = if (clouderaHms) {
                HiveMetastoreDistribution.CLOUDERA
            } else {
                HiveMetastoreDistribution.OPEN_SOURCE
            }
            builder.withHiveMetastore(
                HiveMetastoreOptions(
                    enabled = true,
                    distribution = distribution,
                    image = when (distribution) {
                        HiveMetastoreDistribution.OPEN_SOURCE ->
                            images.hiveMetastore ?: HiveMetastoreOptions.DEFAULT_IMAGE
                        HiveMetastoreDistribution.CLOUDERA ->
                            clouderaHmsImage(
                                config.clouderaHms.databaseType ?: ClouderaHmsDatabaseType.POSTGRESQL,
                                images.clouderaHms,
                                images.clouderaHmsMariadb,
                            )
                    },
                    databaseType = config.hiveMetastore.databaseType ?: HiveMetastoreDatabaseType.POSTGRESQL,
                    clouderaDatabaseType =
                        config.clouderaHms.databaseType ?: ClouderaHmsDatabaseType.POSTGRESQL,
                    databaseImage = hiveMetastoreDatabaseImage(
                        config.hiveMetastore.databaseType ?: HiveMetastoreDatabaseType.POSTGRESQL,
                        images.hiveMetastorePostgres,
                        images.hiveMetastoreMysql,
                    ),
                    databaseHostPort = when (distribution) {
                        HiveMetastoreDistribution.OPEN_SOURCE -> config.hiveMetastore.databaseHostPort ?: 0
                        HiveMetastoreDistribution.CLOUDERA -> config.clouderaHms.databaseHostPort ?: 0
                    },
                    databaseName = when (distribution) {
                        HiveMetastoreDistribution.OPEN_SOURCE -> config.hiveMetastore.databaseName
                        HiveMetastoreDistribution.CLOUDERA -> config.clouderaHms.databaseName
                    } ?: HiveMetastoreOptions().databaseName,
                    databaseUser = when (distribution) {
                        HiveMetastoreDistribution.OPEN_SOURCE -> config.hiveMetastore.databaseUser
                        HiveMetastoreDistribution.CLOUDERA -> config.clouderaHms.databaseUser
                    } ?: HiveMetastoreOptions().databaseUser,
                    databasePassword = when (distribution) {
                        HiveMetastoreDistribution.OPEN_SOURCE -> config.hiveMetastore.databasePassword
                        HiveMetastoreDistribution.CLOUDERA -> config.clouderaHms.databasePassword
                    } ?: HiveMetastoreOptions().databasePassword,
                    warehouseDir = when (distribution) {
                        HiveMetastoreDistribution.OPEN_SOURCE ->
                            config.hiveMetastore.warehouseDir ?: HiveMetastoreOptions().warehouseDir
                        HiveMetastoreDistribution.CLOUDERA ->
                            config.clouderaHms.warehouseDir ?: HiveMetastoreOptions.DEFAULT_CLOUDERA_WAREHOUSE_DIR
                    },
                    tls = config.hiveMetastoreTls.toHttpTls("localhost").copy(enabled = hiveMetastoreTls),
                    kerberos = KerberosAuthOptions(
                        enabled = hiveMetastoreKerberos,
                        servicePrincipal = "hive/hive-metastore.$kerberosDomain@$kerberosRealm",
                        keytabPath = "/kerby/keytabs/hive-metastore.keytab",
                    ),
                ),
            )
        }
        if (kafka || schemaRegistry || kafkaUi) {
            builder.withKafka(
                KafkaOptions(
                    enabled = true,
                    image = images.kafka ?: DEFAULT_KAFKA_IMAGE,
                    startupTimeoutSeconds = config.kafka.startupTimeoutSeconds?.toLong()
                        ?: KafkaOptions().startupTimeoutSeconds,
                    tls = config.kafkaTls.toHttpTls("localhost").copy(enabled = kafkaTls),
                    schemaRegistryEnabled = schemaRegistry,
                    schemaRegistryImage = images.schemaRegistry ?: DEFAULT_SCHEMA_REGISTRY_IMAGE,
                    schemaRegistryTls = config.schemaRegistryTls.toHttpTls("localhost"),
                    kafkaUiEnabled = kafkaUi,
                    kafkaUiImage = images.kafkaUi ?: DEFAULT_KAFKA_UI_IMAGE,
                    kafkaUiTls = config.kafkaUiTls.toHttpTls("localhost"),
                    kerberos = KerberosAuthOptions(
                        enabled = kafkaKerberos,
                        servicePrincipal = "kafka/localhost@$kerberosRealm",
                        keytabPath = "/kerby/keytabs/kafka-broker1.keytab",
                    ),
                    kafkaUiKerberos = KerberosAuthOptions(
                        enabled = kafkaUiKerberos,
                        servicePrincipal = "kafbat-ui/kafbat-ui.$kerberosDomain@$kerberosRealm",
                        keytabPath = "/kerby/keytabs/kafbat-ui.keytab",
                    ),
                ),
            )
        }
        if (s3) {
            builder.withS3(
                ObjectStoreOptions(
                    enabled = true,
                    image = images.s3 ?: DEFAULT_S3_IMAGE,
                    tls = config.s3Tls.toHttpTls("localhost"),
                ),
            )
        }
        if (fakeGcs) {
            builder.withFakeGcs(
                ObjectStoreOptions(
                    enabled = true,
                    image = images.fakeGcs ?: DEFAULT_FAKE_GCS_IMAGE,
                    tls = config.fakeGcsTls.toHttpTls("storage.googleapis.com"),
                ),
            )
        }
        config.instances.forEach { (name, instanceConfig) ->
            builder.withInstance(name, instanceConfig.toTestKitOptions())
        }
        return builder.build()
    }

    private fun bigDataExtensionsLocations(context: ExtensionContext): List<String> =
        context.requiredTestClass.annotations
            .firstOrNull { it.annotationClass.qualifiedName == BIG_DATA_EXTENSIONS_ANNOTATION }
            ?.let { annotation ->
                val method = annotation.javaClass.getMethod("value")
                @Suppress("UNCHECKED_CAST")
                (method.invoke(annotation) as Array<String>).toList()
            }
            .orEmpty()

    private fun bigDataTestConfigLocations(annotation: BigDataTest, context: ExtensionContext): List<String> {
        val taskConfig = systemPropertyLocations(TEST_CONFIG_PROPERTY)
        return if (System.getProperty(TEST_CONFIG_REPLACE_PROPERTY).toBoolean()) {
            taskConfig
        } else {
            bigDataExtensionsLocations(context) + annotation.config.asIterable() + taskConfig
        }
    }

    private fun systemPropertyLocations(name: String): List<String> =
        System.getProperty(name)
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            .orEmpty()

    private fun installJvmTlsProperties(context: ExtensionContext, kit: BigDataTestKit) {
        val properties = kit.springProperties()
            .filterKeys { it in JVM_TLS_PROPERTIES }
        if (properties.isEmpty()) return
        BigDataTestKitStore.putSystemProperties(
            context,
            properties.keys.associateWith { System.getProperty(it) },
        )
        properties.forEach { (key, value) -> System.setProperty(key, value) }
        BigDataTestKitStore.putSslContext(context, SSLContext.getDefault())
        SSLContext.setDefault(sslContext(properties))
    }

    private fun restoreJvmTlsProperties(context: ExtensionContext) {
        BigDataTestKitStore.removeSslContext(context)?.let { SSLContext.setDefault(it) }
        BigDataTestKitStore.removeSystemProperties(context).forEach { (key, value) ->
            if (value == null) {
                System.clearProperty(key)
            } else {
                System.setProperty(key, value)
            }
        }
    }

    private fun sslContext(properties: Map<String, String>): SSLContext {
        val trustStorePath = properties.getValue("javax.net.ssl.trustStore")
        val trustStorePassword = properties.getValue("javax.net.ssl.trustStorePassword").toCharArray()
        val trustStoreType = properties["javax.net.ssl.trustStoreType"] ?: KeyStore.getDefaultType()
        val keyStore = KeyStore.getInstance(trustStoreType)
        Files.newInputStream(Path.of(trustStorePath)).use { keyStore.load(it, trustStorePassword) }
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(keyStore)
        return SSLContext.getInstance("TLS").apply {
            init(null, trustManagerFactory.trustManagers, null)
        }
    }

    private companion object {
        const val BIG_DATA_EXTENSIONS_ANNOTATION = "org.openprojectx.bigdata.test.extensions.junit5.BigDataExtensions"
        const val TEST_CONFIG_PROPERTY = "bigdata.test.config"
        const val TEST_CONFIG_REPLACE_PROPERTY = "bigdata.test.config.replace"
        val JVM_TLS_PROPERTIES = setOf(
            "javax.net.ssl.trustStore",
            "javax.net.ssl.trustStorePassword",
            "javax.net.ssl.trustStoreType",
        )
    }
}

private fun Int.takeIfPositive(): Int? = takeIf { it > 0 }

private fun BigDataTestHttpTlsConfig.toHttpTls(defaultDomain: String): HttpTlsOptions =
    HttpTlsOptions(
        enabled = enabled == true,
        domain = domain ?: defaultDomain,
    )

private fun hiveMetastoreDatabaseImage(
    databaseType: HiveMetastoreDatabaseType,
    postgresImage: String?,
    mysqlImage: String?,
): String =
    when (databaseType) {
        HiveMetastoreDatabaseType.POSTGRESQL -> postgresImage ?: HiveMetastoreOptions.DEFAULT_POSTGRES_IMAGE
        HiveMetastoreDatabaseType.MYSQL -> mysqlImage ?: HiveMetastoreOptions.DEFAULT_MYSQL_IMAGE
    }

private fun clouderaHmsImage(
    databaseType: ClouderaHmsDatabaseType,
    postgresImage: String?,
    mariadbImage: String?,
): String =
    when (databaseType) {
        ClouderaHmsDatabaseType.POSTGRESQL -> postgresImage ?: HiveMetastoreOptions.DEFAULT_CLOUDERA_IMAGE
        ClouderaHmsDatabaseType.MARIADB -> mariadbImage ?: HiveMetastoreOptions.DEFAULT_CLOUDERA_MARIADB_IMAGE
    }
