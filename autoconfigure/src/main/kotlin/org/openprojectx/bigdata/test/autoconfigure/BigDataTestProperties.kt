package org.openprojectx.bigdata.test.autoconfigure

import org.openprojectx.bigdata.test.core.HiveMetastoreDatabaseType
import org.openprojectx.bigdata.test.core.HiveMetastoreOptions
import org.openprojectx.bigdata.test.core.ClouderaHmsDatabaseType

import org.openprojectx.bigdata.test.core.ContainerLogMode
import org.openprojectx.bigdata.test.core.DEFAULT_FAKE_GCS_IMAGE
import org.openprojectx.bigdata.test.core.DEFAULT_HAPROXY_IMAGE
import org.openprojectx.bigdata.test.core.DEFAULT_HDFS_IMAGE
import org.openprojectx.bigdata.test.core.DEFAULT_ICEBERG_REST_CATALOG_IMAGE
import org.openprojectx.bigdata.test.core.DEFAULT_KAFKA_IMAGE
import org.openprojectx.bigdata.test.core.DEFAULT_KAFKA_UI_IMAGE
import org.openprojectx.bigdata.test.core.DEFAULT_KERBEROS_IMAGE
import org.openprojectx.bigdata.test.core.DEFAULT_S3_IMAGE
import org.openprojectx.bigdata.test.core.DEFAULT_SCHEMA_REGISTRY_IMAGE
import org.openprojectx.bigdata.test.core.DEFAULT_TRINO_IMAGE
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("bigdata.test")
data class BigDataTestProperties(
    var enabled: Boolean = false,
    var kerberos: Kerberos = Kerberos(),
    var tls: Tls = Tls(),
    var ports: Ports = Ports(),
    var hdfs: Hdfs = Hdfs(),
    var hiveMetastore: HiveMetastore = HiveMetastore(),
    var clouderaHms: ClouderaHms = ClouderaHms(),
    var kafka: Kafka = Kafka(),
    var s3: ObjectStore = ObjectStore(image = DEFAULT_S3_IMAGE),
    var fakeGcs: ObjectStore = ObjectStore(image = DEFAULT_FAKE_GCS_IMAGE),
    var icebergRestCatalog: IcebergRestCatalog = IcebergRestCatalog(),
    var trino: Trino = Trino(),
    var containerLogs: ContainerLogs = ContainerLogs(),
    var containerLogLevels: Map<String, String> = emptyMap(),
    var instances: Map<String, BigDataTestProperties> = emptyMap(),
) {
    data class Kerberos(
        var enabled: Boolean = false,
        var image: String = DEFAULT_KERBEROS_IMAGE,
        var realm: String = "EXAMPLE.COM",
        var domain: String = "example.com",
        var clientPrincipal: String = "app_user@EXAMPLE.COM",
        var clientPassword: String = "app-user-secret",
    )

    data class Tls(
        var enabled: Boolean = false,
        var caCertPath: String? = null,
        var caKeyPath: String? = null,
        var trustStorePath: String? = null,
        var trustStorePassword: String = "changeit",
        var haproxyImage: String = DEFAULT_HAPROXY_IMAGE,
        @Deprecated("Use caCertPath")
        var certPath: String? = null,
        @Deprecated("Use caKeyPath")
        var keyPath: String? = null,
    )

    data class HttpTls(
        var enabled: Boolean = false,
        var domain: String = "localhost",
    )

    data class Ports(
        var sameHostPorts: Boolean = false,
        var kerberosKdc: Int = 0,
        var hdfsNameNode: Int = 0,
        var hdfsDataNode: Int = 0,
        var hdfsWeb: Int = 0,
        var s3: Int = 0,
        var icebergRestCatalog: Int = 0,
        var trino: Int = 0,
    )

    data class Hdfs(
        var enabled: Boolean = false,
        var image: String = DEFAULT_HDFS_IMAGE,
        var kerberosEnabled: Boolean = false,
        var dataNodeHostname: String = "hdfs",
        var webTls: HttpTls = HttpTls(),
    )

    data class HiveMetastore(
        var enabled: Boolean = false,
        var image: String = HiveMetastoreOptions.DEFAULT_IMAGE,
        var databaseType: HiveMetastoreDatabaseType = HiveMetastoreDatabaseType.POSTGRESQL,
        var databaseImage: String = HiveMetastoreOptions.DEFAULT_POSTGRES_IMAGE,
        var databaseName: String = "metastore",
        var databaseUser: String = "hive",
        var databasePassword: String = "hive",
        var databaseHostPort: Int = 0,
        var warehouseDir: String = HiveMetastoreOptions.DEFAULT_CLOUDERA_WAREHOUSE_DIR,
        var extraConfiguration: Map<String, String> = emptyMap(),
        var kerberosEnabled: Boolean = false,
    )

    data class ClouderaHms(
        var enabled: Boolean = false,
        var image: String = HiveMetastoreOptions.DEFAULT_CLOUDERA_IMAGE,
        var databaseType: ClouderaHmsDatabaseType = ClouderaHmsDatabaseType.POSTGRESQL,
        var databaseName: String = "metastore",
        var databaseUser: String = "hive",
        var databasePassword: String = "hive",
        var databaseHostPort: Int = 0,
        var warehouseDir: String = "/user/hive/warehouse",
        var extraConfiguration: Map<String, String> = emptyMap(),
        var kerberosEnabled: Boolean = false,
    )

    data class Kafka(
        var enabled: Boolean = false,
        var image: String = DEFAULT_KAFKA_IMAGE,
        var startupTimeoutSeconds: Long = 180,
        var schemaRegistryEnabled: Boolean = false,
        var schemaRegistryImage: String = DEFAULT_SCHEMA_REGISTRY_IMAGE,
        var schemaRegistryTls: HttpTls = HttpTls(),
        var kafkaUiEnabled: Boolean = false,
        var kafkaUiImage: String = DEFAULT_KAFKA_UI_IMAGE,
        var kafkaUiTls: HttpTls = HttpTls(),
        var kerberosEnabled: Boolean = false,
        var kafkaUiKerberosEnabled: Boolean = false,
    )

    data class ObjectStore(
        var enabled: Boolean = false,
        var image: String,
        var tls: HttpTls = HttpTls(),
    )

    data class IcebergRestCatalog(
        var enabled: Boolean = false,
        var image: String = DEFAULT_ICEBERG_REST_CATALOG_IMAGE,
        var catalogName: String = "bigdata_test",
        var warehouse: String = "file:///tmp/iceberg/warehouse",
        var realm: String = "POLARIS",
        var clientId: String = "root",
        var clientSecret: String = "s3cr3t",
        var scope: String = "PRINCIPAL_ROLE:ALL",
        var s3RoleArn: String? = null,
        var s3ExternalId: String? = null,
        var startupTimeoutSeconds: Long = 180,
        var tls: HttpTls = HttpTls(),
    )

    data class Trino(
        var enabled: Boolean = false,
        var image: String = DEFAULT_TRINO_IMAGE,
        var catalogName: String = "hive",
        var startupTimeoutSeconds: Long = 180,
        var catalogProperties: Map<String, String> = emptyMap(),
    )

    data class ContainerLogs(
        var mode: ContainerLogMode = ContainerLogMode.NONE,
        var directory: String = "build/bigdata-test-container-logs",
    )
}
