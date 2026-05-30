package org.openprojectx.bigdata.test.junit5

import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.openprojectx.bigdata.test.core.HdfsOptions
import org.openprojectx.bigdata.test.core.HiveMetastoreOptions
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
        val images = BigDataTestConfigLoader(context.requiredTestClass.classLoader).load(configLocations)
        val builder = BigDataTestKit.builder()
            .withPortBindings(
                PortBindingOptions(
                    sameHostPorts = annotation.sameHostPorts,
                    kerberosKdc = annotation.kerberosKdcPort,
                    hdfsNameNode = annotation.hdfsNameNodePort,
                    hdfsWeb = annotation.hdfsWebPort,
                    hiveMetastore = annotation.hiveMetastorePort,
                    kafka = annotation.kafkaPort,
                    schemaRegistry = annotation.schemaRegistryPort,
                    kafkaUi = annotation.kafkaUiPort,
                    localStackS3 = annotation.localStackS3Port,
                    fakeGcs = annotation.fakeGcsPort,
                ),
            )
        if (annotation.containerLogMode != ContainerLogMode.NONE) {
            builder.withContainerLogs(
                ContainerLogOptions(
                    mode = annotation.containerLogMode,
                    directory = annotation.containerLogDirectory,
                ),
            )
        }
        if (annotation.kerberos || annotation.hasKerberosService()) {
            builder.withKerberos(
                KerberosOptions(
                    enabled = true,
                    image = images.kerberos ?: "openprojectx/kerby-kdc:latest",
                    clientPrincipal = annotation.kerberosClientPrincipal,
                    clientPassword = annotation.kerberosClientPassword,
                ),
            )
        }
        if (annotation.hdfs || annotation.hdfsKerberos) {
            builder.withHdfs(
                HdfsOptions(
                    enabled = true,
                    image = images.hdfs ?: "apache/hadoop:3.5.0",
                    kerberos = KerberosAuthOptions(
                        enabled = annotation.hdfsKerberos,
                        servicePrincipal = "nn/hdfs.example.com@EXAMPLE.COM",
                        keytabPath = "/kerby/keytabs/hdfs-namenode.keytab",
                    ),
                ),
            )
        }
        if (annotation.hiveMetastore || annotation.hiveMetastoreKerberos) {
            builder.withHiveMetastore(
                HiveMetastoreOptions(
                    enabled = true,
                    image = images.hiveMetastore ?: "ghcr.io/openprojectx/cloudera-hms:0.1.16",
                    apacheHiveImage = images.hiveMetastoreApache ?: "apache/hive:3.1.3",
                    kerberos = KerberosAuthOptions(
                        enabled = annotation.hiveMetastoreKerberos,
                        servicePrincipal = "hive/hive-metastore.example.com@EXAMPLE.COM",
                        keytabPath = "/kerby/keytabs/hive-metastore.keytab",
                    ),
                ),
            )
        }
        if (annotation.kafka || annotation.schemaRegistry || annotation.kafkaUi) {
            builder.withKafka(
                KafkaOptions(
                    enabled = true,
                    image = images.kafka ?: "apache/kafka:4.1.2",
                    schemaRegistryEnabled = annotation.schemaRegistry,
                    schemaRegistryImage = images.schemaRegistry ?: "confluentinc/cp-schema-registry:7.8.0",
                    kafkaUiEnabled = annotation.kafkaUi,
                    kafkaUiImage = images.kafkaUi ?: "ghcr.io/kafbat/kafka-ui:latest",
                    kerberos = KerberosAuthOptions(
                        enabled = annotation.kafkaKerberos,
                        servicePrincipal = "kafka/localhost@EXAMPLE.COM",
                        keytabPath = "/kerby/keytabs/kafka-broker1.keytab",
                    ),
                    kafkaUiKerberos = KerberosAuthOptions(
                        enabled = annotation.kafkaUiKerberos,
                        servicePrincipal = "kafbat-ui/kafbat-ui.example.com@EXAMPLE.COM",
                        keytabPath = "/kerby/keytabs/kafbat-ui.keytab",
                    ),
                ),
            )
        }
        if (annotation.localStackS3) {
            builder.withLocalStackS3(
                ObjectStoreOptions(
                    enabled = true,
                    image = images.localStackS3 ?: "localstack/localstack:4.14.0",
                ),
            )
        }
        if (annotation.fakeGcs) {
            builder.withFakeGcs(
                ObjectStoreOptions(
                    enabled = true,
                    image = images.fakeGcs ?: "fsouza/fake-gcs-server:1.54",
                ),
            )
        }
        return builder.build()
    }

    private fun BigDataTest.hasKerberosService(): Boolean =
        hdfsKerberos ||
            hiveMetastoreKerberos ||
            kafkaKerberos ||
            kafkaUiKerberos

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
