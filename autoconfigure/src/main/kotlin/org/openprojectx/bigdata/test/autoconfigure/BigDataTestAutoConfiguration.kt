package org.openprojectx.bigdata.test.autoconfigure

import org.openprojectx.bigdata.test.core.BigDataTestKit
import org.openprojectx.bigdata.test.core.HdfsOptions
import org.openprojectx.bigdata.test.core.HiveMetastoreDistribution
import org.openprojectx.bigdata.test.core.HiveMetastoreOptions
import org.openprojectx.bigdata.test.core.KafkaOptions
import org.openprojectx.bigdata.test.core.KerberosAuthOptions
import org.openprojectx.bigdata.test.core.KerberosOptions
import org.openprojectx.bigdata.test.core.ObjectStoreOptions
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

        if (properties.tls.enabled) {
            builder.withTls(
                TlsOptions(
                    enabled = true,
                    certPath = properties.tls.certPath,
                    keyPath = properties.tls.keyPath,
                    caCertPath = properties.tls.caCertPath,
                ),
            )
        }

        if (properties.hdfs.enabled) {
            builder.withHdfs(
                HdfsOptions(
                    enabled = true,
                    image = properties.hdfs.image,
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
                    databaseImage = properties.hiveMetastore.databaseImage,
                    databaseName = properties.hiveMetastore.databaseName,
                    databaseUser = properties.hiveMetastore.databaseUser,
                    databasePassword = properties.hiveMetastore.databasePassword,
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
                    kafkaUiEnabled = properties.kafka.kafkaUiEnabled,
                    kafkaUiImage = properties.kafka.kafkaUiImage,
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
            builder.withLocalStackS3(ObjectStoreOptions(enabled = true, image = properties.localstackS3.image))
        }

        if (properties.fakeGcs.enabled) {
            builder.withFakeGcs(ObjectStoreOptions(enabled = true, image = properties.fakeGcs.image))
        }

        return builder.build().also { it.start() }
    }
}
