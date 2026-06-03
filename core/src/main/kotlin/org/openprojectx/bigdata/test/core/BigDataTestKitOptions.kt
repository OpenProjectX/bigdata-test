package org.openprojectx.bigdata.test.core

data class BigDataTestKitOptions(
    val kerberos: KerberosOptions = KerberosOptions(),
    val tls: TlsOptions = TlsOptions(),
    val hdfs: HdfsOptions = HdfsOptions(),
    val hiveMetastore: HiveMetastoreOptions = HiveMetastoreOptions(),
    val kafka: KafkaOptions = KafkaOptions(),
    val localStackS3: ObjectStoreOptions = ObjectStoreOptions(),
    val fakeGcs: ObjectStoreOptions = ObjectStoreOptions(image = "fsouza/fake-gcs-server:1.54"),
    val portBindings: PortBindingOptions = PortBindingOptions(),
    val containerLogs: ContainerLogOptions = ContainerLogOptions(),
)

data class KerberosOptions(
    val enabled: Boolean = false,
    val image: String = "ghcr.io/openprojectx/directory-kerby/kerby-kdc:latest",
    val realm: String = "EXAMPLE.COM",
    val domain: String = "example.com",
    val clientPrincipal: String = "app_user@EXAMPLE.COM",
    val clientPassword: String = "app-user-secret",
    val users: List<KerberosUserOptions> = emptyList(),
    val startupTimeoutSeconds: Int = 120,
    val materialTimeoutSeconds: Int = 30,
    val adminAttempts: Int = 30,
    val adminRetryDelaySeconds: Int = 1,
)

data class KerberosUserOptions(
    val principal: String,
    val password: String,
    val keytabPath: String = "/kerby/keytabs/${principal.substringBefore("@").replace(Regex("[^A-Za-z0-9._-]"), "_")}.keytab",
)

data class KerberosAuthOptions(
    val enabled: Boolean = false,
    val servicePrincipal: String,
    val keytabPath: String,
)

data class TlsOptions(
    val enabled: Boolean = false,
    val caCertPath: String? = null,
    val caKeyPath: String? = null,
    val trustStorePath: String? = null,
    val trustStorePassword: String = "changeit",
    val haproxyImage: String = "haproxy:3.0-alpine",
    @Deprecated("Use caCertPath for the root CA certificate")
    val certPath: String? = null,
    @Deprecated("Use caKeyPath for the root CA private key")
    val keyPath: String? = null,
)

data class HttpTlsOptions(
    val enabled: Boolean = false,
    val domain: String = "localhost",
)

data class HdfsOptions(
    val enabled: Boolean = false,
    val image: String = "apache/hadoop:3.5.0",
    val nameNodePort: Int = 8020,
    val webPort: Int = 9870,
    val webTls: HttpTlsOptions = HttpTlsOptions(),
    val kerberos: KerberosAuthOptions = KerberosAuthOptions(
        servicePrincipal = "nn/hdfs.example.com@EXAMPLE.COM",
        keytabPath = "/kerby/keytabs/hdfs-namenode.keytab",
    ),
)

data class HiveMetastoreOptions(
    val enabled: Boolean = false,
    val distribution: HiveMetastoreDistribution = HiveMetastoreDistribution.OPEN_SOURCE,
    val image: String = "ghcr.io/openprojectx/hive:3.1.3-hadoop-3.4.2-gcs-4.0.4-jdk17-0.1.4",
    val databaseImage: String = "postgres:16-alpine",
    val databaseName: String = "metastore",
    val databaseUser: String = "hive",
    val databasePassword: String = "hive",
    val warehouseDir: String = "/user/hive/warehouse",
    val extraConfiguration: Map<String, String> = emptyMap(),
    val tls: HttpTlsOptions = HttpTlsOptions(),
    val kerberos: KerberosAuthOptions = KerberosAuthOptions(
        servicePrincipal = "hive/hive-metastore.example.com@EXAMPLE.COM",
        keytabPath = "/kerby/keytabs/hive-metastore.keytab",
    ),
)

enum class HiveMetastoreDistribution {
    OPEN_SOURCE,
    CLOUDERA,
}

data class KafkaOptions(
    val enabled: Boolean = false,
    val image: String = "apache/kafka:4.1.2",
    val tls: HttpTlsOptions = HttpTlsOptions(),
    val schemaRegistryEnabled: Boolean = false,
    val schemaRegistryImage: String = "confluentinc/cp-schema-registry:7.8.0",
    val schemaRegistryTls: HttpTlsOptions = HttpTlsOptions(),
    val kafkaUiEnabled: Boolean = false,
    val kafkaUiImage: String = "ghcr.io/kafbat/kafka-ui:latest",
    val kafkaUiTls: HttpTlsOptions = HttpTlsOptions(),
    val clusterId: String = "MkU3OEVBNTcwNTJENDM2Qk",
    val kerberos: KerberosAuthOptions = KerberosAuthOptions(
        servicePrincipal = "kafka/localhost@EXAMPLE.COM",
        keytabPath = "/kerby/keytabs/kafka-broker1.keytab",
    ),
    val kafkaUiKerberos: KerberosAuthOptions = KerberosAuthOptions(
        servicePrincipal = "kafbat-ui/kafbat-ui.example.com@EXAMPLE.COM",
        keytabPath = "/kerby/keytabs/kafbat-ui.keytab",
    ),
)

data class ObjectStoreOptions(
    val enabled: Boolean = false,
    val image: String = "localstack/localstack:4.14.0",
    val tls: HttpTlsOptions = HttpTlsOptions(),
)



data class PortBindingOptions(
    val sameHostPorts: Boolean = false,
    val kerberosKdc: Int = 0,
    val hdfsNameNode: Int = 0,
    val hdfsWeb: Int = 0,
    val hdfsWebTls: Int = 0,
    val hiveMetastore: Int = 0,
    val kafka: Int = 0,
    val schemaRegistry: Int = 0,
    val schemaRegistryTls: Int = 0,
    val kafkaUi: Int = 0,
    val kafkaUiTls: Int = 0,
    val localStackS3: Int = 0,
    val localStackS3Tls: Int = 0,
    val fakeGcs: Int = 0,
    val fakeGcsTls: Int = 0,
) {
    fun hostPort(containerPort: Int, configuredHostPort: Int): Int {
        require(configuredHostPort >= 0) { "Host port must be 0 for random binding or a positive fixed port" }
        return when {
            configuredHostPort > 0 -> configuredHostPort
            sameHostPorts -> containerPort
            else -> 0
        }
    }
}

enum class ContainerLogMode {
    NONE,
    STDOUT,
    FILE,
}

data class ContainerLogOptions(
    val mode: ContainerLogMode = ContainerLogMode.NONE,
    val directory: String = "build/bigdata-test-container-logs",
)
