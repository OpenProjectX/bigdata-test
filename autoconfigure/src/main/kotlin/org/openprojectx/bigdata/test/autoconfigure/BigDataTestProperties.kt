package org.openprojectx.bigdata.test.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("bigdata.test")
data class BigDataTestProperties(
    var enabled: Boolean = false,
    var kerberos: Kerberos = Kerberos(),
    var tls: Tls = Tls(),
    var hdfs: Hdfs = Hdfs(),
    var hiveMetastore: HiveMetastore = HiveMetastore(),
    var clouderaHms: ClouderaHms = ClouderaHms(),
    var kafka: Kafka = Kafka(),
    var localstackS3: ObjectStore = ObjectStore(image = "localstack/localstack:4.14.0"),
    var fakeGcs: ObjectStore = ObjectStore(image = "fsouza/fake-gcs-server:1.54"),
) {
    data class Kerberos(
        var enabled: Boolean = false,
        var image: String = "openprojectx/kerby-kdc:latest",
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
        var haproxyImage: String = "haproxy:3.0-alpine",
        @Deprecated("Use caCertPath")
        var certPath: String? = null,
        @Deprecated("Use caKeyPath")
        var keyPath: String? = null,
    )

    data class HttpTls(
        var enabled: Boolean = false,
        var domain: String = "localhost",
    )

    data class Hdfs(
        var enabled: Boolean = false,
        var image: String = "apache/hadoop:3.5.0",
        var kerberosEnabled: Boolean = false,
        var webTls: HttpTls = HttpTls(),
    )

    data class HiveMetastore(
        var enabled: Boolean = false,
        var image: String = "ghcr.io/openprojectx/hive:3.1.3-hadoop-3.4.2-gcs-4.0.4-jdk17-0.1.4",
        var databaseImage: String = "postgres:16-alpine",
        var databaseName: String = "metastore",
        var databaseUser: String = "hive",
        var databasePassword: String = "hive",
        var warehouseDir: String = "/user/hive/warehouse",
        var extraConfiguration: Map<String, String> = emptyMap(),
        var kerberosEnabled: Boolean = false,
    )

    data class ClouderaHms(
        var enabled: Boolean = false,
        var image: String = "ghcr.io/openprojectx/cloudera-hms:0.1.16",
        var warehouseDir: String = "/user/hive/warehouse",
        var extraConfiguration: Map<String, String> = emptyMap(),
        var kerberosEnabled: Boolean = false,
    )

    data class Kafka(
        var enabled: Boolean = false,
        var image: String = "apache/kafka:4.1.2",
        var schemaRegistryEnabled: Boolean = false,
        var schemaRegistryImage: String = "confluentinc/cp-schema-registry:7.8.0",
        var schemaRegistryTls: HttpTls = HttpTls(),
        var kafkaUiEnabled: Boolean = false,
        var kafkaUiImage: String = "ghcr.io/kafbat/kafka-ui:latest",
        var kafkaUiTls: HttpTls = HttpTls(),
        var kerberosEnabled: Boolean = false,
        var kafkaUiKerberosEnabled: Boolean = false,
    )

    data class ObjectStore(
        var enabled: Boolean = false,
        var image: String,
        var tls: HttpTls = HttpTls(),
    )
}
