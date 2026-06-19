package org.openprojectx.bigdata.test.core

import org.testcontainers.containers.GenericContainer

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
    val containerCustomizations: Map<BigDataService, ContainerCustomizationOptions> = emptyMap(),
    val healthChecks: Map<BigDataService, BigDataHealthCheckOptions> = emptyMap(),
)

data class KerberosOptions(
    val enabled: Boolean = false,
    val image: String = "ghcr.io/openprojectx/directory-kerby/kerby-kdc:latest",
    val realm: String = "EXAMPLE.COM",
    val domain: String = "example.com",
    val clientPrincipal: String = "app_user@EXAMPLE.COM",
    val clientPassword: String = "app-user-secret",
    val materialDirectory: String? = null,
    val localKrb5ConfPath: String? = null,
    val localClientKeytabPath: String? = null,
    val users: List<KerberosUserOptions> = emptyList(),
    val startupTimeoutSeconds: Int = 120,
    val materialTimeoutSeconds: Int = 30,
    val adminAttempts: Int = 30,
    val adminRetryDelaySeconds: Int = 1,
    val debug: Boolean = false,
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
    val dataNodePort: Int = 9866,
    val dataNodeHostname: String = "hdfs",
    val localHdfsSitePath: String? = null,
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
    val image: String = DEFAULT_IMAGE,
    val databaseType: HiveMetastoreDatabaseType = HiveMetastoreDatabaseType.POSTGRESQL,
    val clouderaDatabaseType: ClouderaHmsDatabaseType = ClouderaHmsDatabaseType.POSTGRESQL,
    val databaseImage: String = DEFAULT_POSTGRES_IMAGE,
    val databaseHostPort: Int = 0,
    val databaseName: String = "metastore",
    val databaseUser: String = "hive",
    val databasePassword: String = "hive",
    val warehouseDir: String = "/user/hive/warehouse",
    val localHiveSitePath: String? = null,
    val localMetastoreSitePath: String? = null,
    val extraConfiguration: Map<String, String> = emptyMap(),
    val tls: HttpTlsOptions = HttpTlsOptions(),
    val kerberos: KerberosAuthOptions = KerberosAuthOptions(
        servicePrincipal = "hive/hive-metastore.example.com@EXAMPLE.COM",
        keytabPath = "/kerby/keytabs/hive-metastore.keytab",
    ),
) {
    companion object {
        const val DEFAULT_IMAGE = "ghcr.io/openprojectx/hive:3.1.3-hadoop-3.4.2-gcs-4.0.4-jdk17-0.1.5"
        const val DEFAULT_CLOUDERA_IMAGE = "ghcr.io/openprojectx/cloudera-hms:0.1.74"
        const val DEFAULT_CLOUDERA_MARIADB_IMAGE = "ghcr.io/openprojectx/cloudera-hms:0.1.74-mariadb"
        const val DEFAULT_CLOUDERA_WAREHOUSE_DIR = "/tmp/cloudera-hms/warehouse"
        const val DEFAULT_POSTGRES_IMAGE = "postgres:16-alpine"
        const val DEFAULT_MYSQL_IMAGE = "mysql:8.0.44-bookworm"
    }
}

enum class HiveMetastoreDistribution {
    OPEN_SOURCE,
    CLOUDERA,
}

enum class HiveMetastoreDatabaseType {
    POSTGRESQL,
    MYSQL,
}

enum class ClouderaHmsDatabaseType {
    POSTGRESQL,
    MARIADB,
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
    val hdfsDataNode: Int = 0,
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
    val append: Boolean = true,
)

enum class BigDataHealthCheckMode {
    NONE,
    BASIC,
    CLI,
}

data class BigDataHealthCheckOptions(
    val mode: BigDataHealthCheckMode = BigDataHealthCheckMode.BASIC,
    val timeoutSeconds: Long = 60,
)

data class ContainerCustomizationOptions(
    val networkMode: String? = null,
    val environment: Map<String, String> = emptyMap(),
    val files: List<ContainerFileTransferOptions> = emptyList(),
    val mounts: List<ContainerMountOptions> = emptyList(),
    val ports: List<ContainerPortOptions> = emptyList(),
    val customizers: List<BigDataContainerCustomizer> = emptyList(),
) {
    fun merge(override: ContainerCustomizationOptions): ContainerCustomizationOptions =
        ContainerCustomizationOptions(
            networkMode = override.networkMode ?: networkMode,
            environment = environment + override.environment,
            files = files + override.files,
            mounts = mounts + override.mounts,
            ports = ports + override.ports,
            customizers = customizers + override.customizers,
        )
}

data class ContainerFileTransferOptions(
    val containerPath: String,
    val hostPath: String? = null,
    val content: ByteArray? = null,
    val fileMode: Int? = null,
) {
    init {
        require(containerPath.isNotBlank()) { "Container file path must not be blank" }
        require((hostPath == null) != (content == null)) {
            "Exactly one of hostPath or content must be set for container file transfer"
        }
    }

    companion object {
        @JvmStatic
        fun hostPath(hostPath: String, containerPath: String, fileMode: Int? = null): ContainerFileTransferOptions =
            ContainerFileTransferOptions(containerPath = containerPath, hostPath = hostPath, fileMode = fileMode)

        @JvmStatic
        fun content(content: String, containerPath: String, fileMode: Int? = null): ContainerFileTransferOptions =
            ContainerFileTransferOptions(
                containerPath = containerPath,
                content = content.toByteArray(Charsets.UTF_8),
                fileMode = fileMode,
            )

        @JvmStatic
        fun content(content: ByteArray, containerPath: String, fileMode: Int? = null): ContainerFileTransferOptions =
            ContainerFileTransferOptions(containerPath = containerPath, content = content, fileMode = fileMode)
    }
}

data class ContainerMountOptions(
    val hostPath: String,
    val containerPath: String,
    val readOnly: Boolean = true,
) {
    init {
        require(hostPath.isNotBlank()) { "Host mount path must not be blank" }
        require(containerPath.isNotBlank()) { "Container mount path must not be blank" }
    }
}

data class ContainerPortOptions(
    val containerPort: Int,
    val hostPort: Int = 0,
) {
    init {
        require(containerPort > 0) { "Container port must be positive" }
        require(hostPort >= 0) { "Host port must be 0 for random binding or a positive fixed port" }
    }
}

fun interface BigDataContainerCustomizer {
    fun customize(container: GenericContainer<*>)
}
