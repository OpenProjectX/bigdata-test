package org.openprojectx.bigdata.test.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("bigdata.test")
data class BigDataTestProperties(
    var enabled: Boolean = false,
    var kerberos: Kerberos = Kerberos(),
    var tls: Tls = Tls(),
    var hdfs: Hdfs = Hdfs(),
    var hiveMetastore: HiveMetastore = HiveMetastore(),
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
        var certPath: String? = null,
        var keyPath: String? = null,
        var caCertPath: String? = null,
    )

    data class Hdfs(
        var enabled: Boolean = false,
        var image: String = "apache/hadoop:3.5.0",
        var kerberosEnabled: Boolean = false,
    )

    data class HiveMetastore(
        var enabled: Boolean = false,
        var image: String = "ghcr.io/openprojectx/cloudera-hms:0.1.16",
        var databaseName: String = "metastore_db",
        var databaseUser: String = "hive",
        var databasePassword: String = "hive-password",
        var warehouseDir: String = "/user/hive/warehouse",
        var extraConfiguration: Map<String, String> = emptyMap(),
        var kerberosEnabled: Boolean = false,
    )

    data class Kafka(
        var enabled: Boolean = false,
        var image: String = "apache/kafka:4.1.2",
        var schemaRegistryEnabled: Boolean = false,
        var schemaRegistryImage: String = "confluentinc/cp-schema-registry:7.8.0",
        var kafkaUiEnabled: Boolean = false,
        var kafkaUiImage: String = "ghcr.io/kafbat/kafka-ui:latest",
        var kerberosEnabled: Boolean = false,
        var schemaRegistryKerberosEnabled: Boolean = false,
        var kafkaUiKerberosEnabled: Boolean = false,
    )

    data class ObjectStore(
        var enabled: Boolean = false,
        var image: String,
    )
}
