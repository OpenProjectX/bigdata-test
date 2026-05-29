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
import org.openprojectx.bigdata.test.core.PortBindingOptions

class BigDataTestExtension : BeforeAllCallback, AfterAllCallback, ParameterResolver {
    override fun beforeAll(context: ExtensionContext) {
        val annotation = context.requiredTestClass.getAnnotation(BigDataTest::class.java)
            ?: return
        val kit = kitFrom(annotation)
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

    private fun kitFrom(annotation: BigDataTest): BigDataTestKit {
        val builder = BigDataTestKit.builder()
            .withPortBindings(
                PortBindingOptions(
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
        if (annotation.kerberos) builder.withKerberos()
        if (annotation.hdfs || annotation.hdfsKerberos) {
            builder.withHdfs(
                HdfsOptions(
                    enabled = true,
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
                    schemaRegistryEnabled = annotation.schemaRegistry,
                    kafkaUiEnabled = annotation.kafkaUi,
                    kerberos = KerberosAuthOptions(
                        enabled = annotation.kafkaKerberos,
                        servicePrincipal = "kafka/broker1.example.com@EXAMPLE.COM",
                        keytabPath = "/kerby/keytabs/kafka-broker1.keytab",
                    ),
                    schemaRegistryKerberos = KerberosAuthOptions(
                        enabled = annotation.schemaRegistryKerberos,
                        servicePrincipal = "schema-registry/schema-registry.example.com@EXAMPLE.COM",
                        keytabPath = "/kerby/keytabs/schema-registry.keytab",
                    ),
                    kafkaUiKerberos = KerberosAuthOptions(
                        enabled = annotation.kafkaUiKerberos,
                        servicePrincipal = "kafbat-ui/kafbat-ui.example.com@EXAMPLE.COM",
                        keytabPath = "/kerby/keytabs/kafbat-ui.keytab",
                    ),
                ),
            )
        }
        if (annotation.localStackS3) builder.withLocalStackS3()
        if (annotation.fakeGcs) builder.withFakeGcs()
        return builder.build()
    }
}
