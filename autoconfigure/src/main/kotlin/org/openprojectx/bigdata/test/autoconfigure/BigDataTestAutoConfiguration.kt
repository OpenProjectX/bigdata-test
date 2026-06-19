package org.openprojectx.bigdata.test.autoconfigure

import org.openprojectx.bigdata.test.core.BigDataTestKit
import org.openprojectx.bigdata.test.core.BigDataService
import org.openprojectx.bigdata.test.core.ContainerLogOptions
import org.openprojectx.bigdata.test.core.HdfsOptions
import org.openprojectx.bigdata.test.core.HttpTlsOptions
import org.openprojectx.bigdata.test.core.HiveMetastoreDatabaseType
import org.openprojectx.bigdata.test.core.HiveMetastoreDistribution
import org.openprojectx.bigdata.test.core.HiveMetastoreOptions
import org.openprojectx.bigdata.test.core.KafkaOptions
import org.openprojectx.bigdata.test.core.KerberosAuthOptions
import org.openprojectx.bigdata.test.core.KerberosOptions
import org.openprojectx.bigdata.test.core.ObjectStoreOptions
import org.openprojectx.bigdata.test.core.PortBindingOptions
import org.openprojectx.bigdata.test.core.TlsOptions
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

@AutoConfiguration
@EnableConfigurationProperties(BigDataTestProperties::class)
@ConditionalOnProperty(prefix = "bigdata.test", name = ["enabled"], havingValue = "true")
class BigDataTestAutoConfiguration {
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    fun bigDataTestKit(properties: BigDataTestProperties): BigDataTestKit {
        val builder = BigDataTestKit.builder()
            .withPortBindings(
                PortBindingOptions(
                    sameHostPorts = properties.ports.sameHostPorts,
                    kerberosKdc = properties.ports.kerberosKdc,
                    hdfsNameNode = properties.ports.hdfsNameNode,
                    hdfsDataNode = properties.ports.hdfsDataNode,
                    hdfsWeb = properties.ports.hdfsWeb,
                    localStackS3 = properties.ports.localstackS3,
                ),
            )
            .withContainerLogs(
                ContainerLogOptions(
                    mode = properties.containerLogs.mode,
                    directory = properties.containerLogs.directory,
                ),
            )
        properties.containerLogLevels.forEach { (serviceName, level) ->
            builder.withContainerLogLevel(serviceName.toBigDataService(), level)
        }

        if (properties.kerberos.enabled) {
            builder.withKerberos(
                KerberosOptions(
                    enabled = true,
                    image = properties.kerberos.image,
                    realm = properties.kerberos.realm,
                    domain = properties.kerberos.domain,
                    clientPrincipal = properties.kerberos.clientPrincipal,
                    clientPassword = properties.kerberos.clientPassword,
                ),
            )
        }

        if (properties.tls.enabled || properties.anyHttpTlsEnabled()) {
            builder.withTls(
                TlsOptions(
                    enabled = properties.tls.enabled || properties.anyHttpTlsEnabled(),
                    caCertPath = properties.tls.caCertPath,
                    caKeyPath = properties.tls.caKeyPath,
                    trustStorePath = properties.tls.trustStorePath,
                    trustStorePassword = properties.tls.trustStorePassword,
                    haproxyImage = properties.tls.haproxyImage,
                    certPath = properties.tls.certPath,
                    keyPath = properties.tls.keyPath,
                ),
            )
        }

        if (properties.hdfs.enabled) {
            builder.withHdfs(
                HdfsOptions(
                    enabled = true,
                    image = properties.hdfs.image,
                    dataNodeHostname = properties.hdfs.dataNodeHostname,
                    webTls = properties.hdfs.webTls.toCore(),
                    kerberos = KerberosAuthOptions(
                        enabled = properties.hdfs.kerberosEnabled,
                        servicePrincipal = "nn/hdfs.example.com@${properties.kerberos.realm}",
                        keytabPath = "/kerby/keytabs/hdfs-namenode.keytab",
                    ),
                ),
            )
        }

        require(!(properties.hiveMetastore.enabled && properties.clouderaHms.enabled)) {
            "Use only one HMS implementation: bigdata.test.hive-metastore or bigdata.test.cloudera-hms"
        }
        if (properties.hiveMetastore.enabled) {
            builder.withHiveMetastore(
                HiveMetastoreOptions(
                    enabled = true,
                    distribution = HiveMetastoreDistribution.OPEN_SOURCE,
                    image = properties.hiveMetastore.image,
                    databaseType = properties.hiveMetastore.databaseType,
                    databaseImage = properties.hiveMetastore.databaseImageForType(),
                    databaseName = properties.hiveMetastore.databaseName,
                    databaseUser = properties.hiveMetastore.databaseUser,
                    databasePassword = properties.hiveMetastore.databasePassword,
                    databaseHostPort = properties.hiveMetastore.databaseHostPort,
                    warehouseDir = properties.hiveMetastore.warehouseDir,
                    extraConfiguration = properties.hiveMetastore.extraConfiguration,
                    kerberos = KerberosAuthOptions(
                        enabled = properties.hiveMetastore.kerberosEnabled,
                        servicePrincipal = "hive/hive-metastore.example.com@${properties.kerberos.realm}",
                        keytabPath = "/kerby/keytabs/hive-metastore.keytab",
                    ),
                ),
            )
        }

        if (properties.clouderaHms.enabled) {
            builder.withClouderaHms(
                HiveMetastoreOptions(
                    enabled = true,
                    distribution = HiveMetastoreDistribution.CLOUDERA,
                    image = properties.clouderaHms.image,
                    warehouseDir = properties.clouderaHms.warehouseDir,
                    extraConfiguration = properties.clouderaHms.extraConfiguration,
                    kerberos = KerberosAuthOptions(
                        enabled = properties.clouderaHms.kerberosEnabled,
                        servicePrincipal = "hive/hive-metastore.example.com@${properties.kerberos.realm}",
                        keytabPath = "/kerby/keytabs/hive-metastore.keytab",
                    ),
                ),
            )
        }

        if (properties.kafka.enabled) {
            builder.withKafka(
                KafkaOptions(
                    enabled = true,
                    image = properties.kafka.image,
                    schemaRegistryEnabled = properties.kafka.schemaRegistryEnabled,
                    schemaRegistryImage = properties.kafka.schemaRegistryImage,
                    schemaRegistryTls = properties.kafka.schemaRegistryTls.toCore(),
                    kafkaUiEnabled = properties.kafka.kafkaUiEnabled,
                    kafkaUiImage = properties.kafka.kafkaUiImage,
                    kafkaUiTls = properties.kafka.kafkaUiTls.toCore(),
                    kerberos = KerberosAuthOptions(
                        enabled = properties.kafka.kerberosEnabled,
                        servicePrincipal = "kafka/broker1.example.com@${properties.kerberos.realm}",
                        keytabPath = "/kerby/keytabs/kafka-broker1.keytab",
                    ),
                    kafkaUiKerberos = KerberosAuthOptions(
                        enabled = properties.kafka.kafkaUiKerberosEnabled,
                        servicePrincipal = "kafbat-ui/kafbat-ui.example.com@${properties.kerberos.realm}",
                        keytabPath = "/kerby/keytabs/kafbat-ui.keytab",
                    ),
                ),
            )
        }

        if (properties.localstackS3.enabled) {
            builder.withLocalStackS3(
                ObjectStoreOptions(
                    enabled = true,
                    image = properties.localstackS3.image,
                    tls = properties.localstackS3.tls.toCore(),
                ),
            )
        }

        if (properties.fakeGcs.enabled) {
            builder.withFakeGcs(
                ObjectStoreOptions(
                    enabled = true,
                    image = properties.fakeGcs.image,
                    tls = properties.fakeGcs.tls.toCore(defaultDomain = "storage.googleapis.com"),
                ),
            )
        }

        return builder.build().also { it.start() }
    }

    private fun BigDataTestProperties.HttpTls.toCore(defaultDomain: String = "localhost"): HttpTlsOptions =
        HttpTlsOptions(
            enabled = enabled,
            domain = domain.ifBlank { defaultDomain },
        )

    private fun BigDataTestProperties.anyHttpTlsEnabled(): Boolean =
        hdfs.webTls.enabled ||
            kafka.schemaRegistryTls.enabled ||
            kafka.kafkaUiTls.enabled ||
            localstackS3.tls.enabled ||
            fakeGcs.tls.enabled

    private fun String.toBigDataService(): BigDataService =
        BigDataService.entries.firstOrNull { service ->
            service.name.equals(this, ignoreCase = true) ||
                service.name.replace("_", "-").equals(this, ignoreCase = true) ||
                service.name.replace("_", "").equals(this, ignoreCase = true)
        } ?: error("Unknown bigdata.test.container-log-levels service '$this'")

    private fun BigDataTestProperties.HiveMetastore.databaseImageForType(): String =
        when (databaseType) {
            HiveMetastoreDatabaseType.POSTGRESQL -> databaseImage
            HiveMetastoreDatabaseType.MYSQL ->
                if (databaseImage == HiveMetastoreOptions.DEFAULT_POSTGRES_IMAGE) {
                    HiveMetastoreOptions.DEFAULT_MYSQL_IMAGE
                } else {
                    databaseImage
                }
        }
}
