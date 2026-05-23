package org.openprojectx.bigdata.test.core

data class BigDataTestKitOptions(
    val kerberos: KerberosOptions = KerberosOptions(),
    val tls: TlsOptions = TlsOptions(),
    val hdfs: HdfsOptions = HdfsOptions(),
    val hiveMetastore: HiveMetastoreOptions = HiveMetastoreOptions(),
    val kafka: KafkaOptions = KafkaOptions(),
    val localStackS3: ObjectStoreOptions = ObjectStoreOptions(),
    val fakeGcs: ObjectStoreOptions = ObjectStoreOptions(image = "fsouza/fake-gcs-server:1.54"),
    val containerLogs: ContainerLogOptions = ContainerLogOptions(),
)

data class KerberosOptions(
    val enabled: Boolean = false,
    val image: String = "openprojectx/kerby-kdc:latest",
    val realm: String = "EXAMPLE.COM",
    val domain: String = "example.com",
    val clientPrincipal: String = "app_user@EXAMPLE.COM",
    val clientPassword: String = "app-user-secret",
)

data class KerberosAuthOptions(
    val enabled: Boolean = false,
    val servicePrincipal: String,
    val keytabPath: String,
)

data class TlsOptions(
    val enabled: Boolean = false,
    val certPath: String? = null,
    val keyPath: String? = null,
    val caCertPath: String? = null,
)

data class HdfsOptions(
    val enabled: Boolean = false,
    val image: String = "apache/hadoop:3.5.0",
    val nameNodePort: Int = 8020,
    val webPort: Int = 9870,
    val kerberos: KerberosAuthOptions = KerberosAuthOptions(
        servicePrincipal = "nn/hdfs.example.com@EXAMPLE.COM",
        keytabPath = "/kerby/keytabs/hdfs-namenode.keytab",
    ),
)

data class HiveMetastoreOptions(
    val enabled: Boolean = false,
    val image: String = "ghcr.io/openprojectx/cloudera-hms:0.1.15",
    val apacheHiveImage: String = "apache/hive:3.1.3",
    val databaseName: String = "metastore_db",
    val databaseUser: String = "hive",
    val databasePassword: String = "hive-password",
    val warehouseDir: String = "/user/hive/warehouse",
    val extraConfiguration: Map<String, String> = emptyMap(),
    val kerberos: KerberosAuthOptions = KerberosAuthOptions(
        servicePrincipal = "hive/hive-metastore.example.com@EXAMPLE.COM",
        keytabPath = "/kerby/keytabs/hive-metastore.keytab",
    ),
)

data class KafkaOptions(
    val enabled: Boolean = false,
    val image: String = "apache/kafka:4.1.2",
    val schemaRegistryEnabled: Boolean = false,
    val schemaRegistryImage: String = "confluentinc/cp-schema-registry:7.8.0",
    val kafkaUiEnabled: Boolean = false,
    val kafkaUiImage: String = "ghcr.io/kafbat/kafka-ui:latest",
    val clusterId: String = "MkU3OEVBNTcwNTJENDM2Qk",
    val kerberos: KerberosAuthOptions = KerberosAuthOptions(
        servicePrincipal = "kafka/broker1.example.com@EXAMPLE.COM",
        keytabPath = "/kerby/keytabs/kafka-broker1.keytab",
    ),
    val schemaRegistryKerberos: KerberosAuthOptions = KerberosAuthOptions(
        servicePrincipal = "schema-registry/schema-registry.example.com@EXAMPLE.COM",
        keytabPath = "/kerby/keytabs/schema-registry.keytab",
    ),
    val kafkaUiKerberos: KerberosAuthOptions = KerberosAuthOptions(
        servicePrincipal = "kafbat-ui/kafbat-ui.example.com@EXAMPLE.COM",
        keytabPath = "/kerby/keytabs/kafbat-ui.keytab",
    ),
)

data class ObjectStoreOptions(
    val enabled: Boolean = false,
    val image: String = "localstack/localstack:4.14.0",
)


enum class ContainerLogMode {
    NONE,
    STDOUT,
    FILE,
}

data class ContainerLogOptions(
    val mode: ContainerLogMode = ContainerLogMode.NONE,
    val directory: String = "build/bigdata-test-container-logs",
)
