package org.openprojectx.bigdata.test.junit5

import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.openprojectx.bigdata.test.core.HdfsOptions
import org.openprojectx.bigdata.test.core.HiveMetastoreOptions
import org.openprojectx.bigdata.test.core.HiveMetastoreDistribution
import org.openprojectx.bigdata.test.core.BigDataTestKit
import org.openprojectx.bigdata.test.core.ContainerLogMode
import org.openprojectx.bigdata.test.core.ContainerLogOptions
import org.openprojectx.bigdata.test.core.KafkaOptions
import org.openprojectx.bigdata.test.core.KerberosAuthOptions
import org.openprojectx.bigdata.test.core.KerberosOptions
import org.openprojectx.bigdata.test.core.ObjectStoreOptions
import org.openprojectx.bigdata.test.core.PortBindingOptions

class BigDataTestExtension : BeforeAllCallback, AfterAllCallback, ParameterResolver {
    override fun beforeAll(context: ExtensionContext) {
        val annotation = context.requiredTestClass.getAnnotation(BigDataTest::class.java)
            ?: return
        val kit = kitFrom(annotation, context)
        kit.start()
        BigDataTestKitStore.put(context, kit)
    }

    override fun afterAll(context: ExtensionContext) {
        BigDataTestKitStore.remove(context)?.close()
    }

    override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean =
        parameterContext.parameter.type == BigDataTestKit::class.java

    override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any =
        BigDataTestKitStore.get(extensionContext)

    private fun kitFrom(annotation: BigDataTest, context: ExtensionContext): BigDataTestKit {
        val configLocations = bigDataExtensionsLocations(context) + annotation.config.asIterable()
        val config = BigDataTestConfigLoader(context.requiredTestClass.classLoader).load(configLocations)
        val images = config.images
        val services = config.services
        val ports = config.ports
        val containerLogs = config.containerLogs
        val builder = BigDataTestKit.builder()
            .withPortBindings(
                PortBindingOptions(
                    sameHostPorts = annotation.sameHostPorts || (ports.sameHostPorts ?: false),
                    kerberosKdc = annotation.kerberosKdcPort.takeIfPositive() ?: ports.kerberosKdc ?: 0,
                    hdfsNameNode = annotation.hdfsNameNodePort.takeIfPositive() ?: ports.hdfsNameNode ?: 0,
                    hdfsWeb = annotation.hdfsWebPort.takeIfPositive() ?: ports.hdfsWeb ?: 0,
                    hiveMetastore = annotation.hiveMetastorePort.takeIfPositive() ?: ports.hiveMetastore ?: 0,
                    kafka = annotation.kafkaPort.takeIfPositive() ?: ports.kafka ?: 0,
                    schemaRegistry = annotation.schemaRegistryPort.takeIfPositive() ?: ports.schemaRegistry ?: 0,
                    kafkaUi = annotation.kafkaUiPort.takeIfPositive() ?: ports.kafkaUi ?: 0,
                    localStackS3 = annotation.localStackS3Port.takeIfPositive() ?: ports.localStackS3 ?: 0,
                    fakeGcs = annotation.fakeGcsPort.takeIfPositive() ?: ports.fakeGcs ?: 0,
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
                ),
            )
        }
        val kerberos = annotation.kerberos || services.kerberos == true
        val hdfs = annotation.hdfs || services.hdfs == true
        val hdfsKerberos = annotation.hdfsKerberos || services.hdfsKerberos == true
        val hiveMetastore = annotation.hiveMetastore || services.hiveMetastore == true
        val clouderaHms = annotation.clouderaHms || services.clouderaHms == true
        val hiveMetastoreKerberos = annotation.hiveMetastoreKerberos || services.hiveMetastoreKerberos == true
        val kafka = annotation.kafka || services.kafka == true
        val kafkaKerberos = annotation.kafkaKerberos || services.kafkaKerberos == true
        val schemaRegistry = annotation.schemaRegistry || services.schemaRegistry == true
        val kafkaUi = annotation.kafkaUi || services.kafkaUi == true
        val kafkaUiKerberos = annotation.kafkaUiKerberos || services.kafkaUiKerberos == true
        val localStackS3 = annotation.localStackS3 || services.localStackS3 == true
        val fakeGcs = annotation.fakeGcs || services.fakeGcs == true

        if (kerberos || hdfsKerberos || hiveMetastoreKerberos || kafkaKerberos || kafkaUiKerberos) {
            builder.withKerberos(
                KerberosOptions(
                    enabled = true,
                    image = images.kerberos ?: "openprojectx/kerby-kdc:latest",
                    clientPrincipal = annotation.kerberosClientPrincipal,
                    clientPassword = annotation.kerberosClientPassword,
                ),
            )
        }
        if (hdfs || hdfsKerberos) {
            builder.withHdfs(
                HdfsOptions(
                    enabled = true,
                    image = images.hdfs ?: "apache/hadoop:3.5.0",
                    kerberos = KerberosAuthOptions(
                        enabled = hdfsKerberos,
                        servicePrincipal = "nn/hdfs.example.com@EXAMPLE.COM",
                        keytabPath = "/kerby/keytabs/hdfs-namenode.keytab",
                    ),
                ),
            )
        }
        require(!(hiveMetastore && clouderaHms)) {
            "Use only one HMS implementation: hiveMetastore or clouderaHms"
        }
        if (hiveMetastore || clouderaHms || hiveMetastoreKerberos) {
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
                            images.hiveMetastore ?: "ghcr.io/openprojectx/hive:3.1.3-hadoop-3.4.2-gcs-4.0.4-jdk17-0.1.4"
                        HiveMetastoreDistribution.CLOUDERA ->
                            images.clouderaHms ?: "ghcr.io/openprojectx/cloudera-hms:0.1.16"
                    },
                    databaseImage = images.hiveMetastorePostgres ?: "postgres:16-alpine",
                    kerberos = KerberosAuthOptions(
                        enabled = hiveMetastoreKerberos,
                        servicePrincipal = "hive/hive-metastore.example.com@EXAMPLE.COM",
                        keytabPath = "/kerby/keytabs/hive-metastore.keytab",
                    ),
                ),
            )
        }
        if (kafka || schemaRegistry || kafkaUi) {
            builder.withKafka(
                KafkaOptions(
                    enabled = true,
                    image = images.kafka ?: "apache/kafka:4.1.2",
                    schemaRegistryEnabled = schemaRegistry,
                    schemaRegistryImage = images.schemaRegistry ?: "confluentinc/cp-schema-registry:7.8.0",
                    kafkaUiEnabled = kafkaUi,
                    kafkaUiImage = images.kafkaUi ?: "ghcr.io/kafbat/kafka-ui:latest",
                    kerberos = KerberosAuthOptions(
                        enabled = kafkaKerberos,
                        servicePrincipal = "kafka/localhost@EXAMPLE.COM",
                        keytabPath = "/kerby/keytabs/kafka-broker1.keytab",
                    ),
                    kafkaUiKerberos = KerberosAuthOptions(
                        enabled = kafkaUiKerberos,
                        servicePrincipal = "kafbat-ui/kafbat-ui.example.com@EXAMPLE.COM",
                        keytabPath = "/kerby/keytabs/kafbat-ui.keytab",
                    ),
                ),
            )
        }
        if (localStackS3) {
            builder.withLocalStackS3(
                ObjectStoreOptions(
                    enabled = true,
                    image = images.localStackS3 ?: "localstack/localstack:4.14.0",
                ),
            )
        }
        if (fakeGcs) {
            builder.withFakeGcs(
                ObjectStoreOptions(
                    enabled = true,
                    image = images.fakeGcs ?: "fsouza/fake-gcs-server:1.54",
                ),
            )
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

    private companion object {
        const val BIG_DATA_EXTENSIONS_ANNOTATION = "org.openprojectx.bigdata.test.extensions.junit5.BigDataExtensions"
    }
}

private fun Int.takeIfPositive(): Int? = takeIf { it > 0 }
