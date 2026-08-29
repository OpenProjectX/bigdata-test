package org.openprojectx.bigdata.test.gradle

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.openprojectx.bigdata.test.core.ClouderaHmsDatabaseType
import org.openprojectx.bigdata.test.core.ContainerLogMode
import org.openprojectx.bigdata.test.core.DEFAULT_HAPROXY_IMAGE
import org.openprojectx.bigdata.test.core.DEFAULT_HDFS_IMAGE
import org.openprojectx.bigdata.test.core.DEFAULT_ICEBERG_REST_CATALOG_IMAGE
import org.openprojectx.bigdata.test.core.DEFAULT_TRINO_IMAGE
import org.openprojectx.bigdata.test.core.DEFAULT_KAFKA_IMAGE
import org.openprojectx.bigdata.test.core.DEFAULT_KAFKA_UI_IMAGE
import org.openprojectx.bigdata.test.core.DEFAULT_KERBEROS_IMAGE
import org.openprojectx.bigdata.test.core.DEFAULT_SCHEMA_REGISTRY_IMAGE
import org.openprojectx.bigdata.test.core.HiveMetastoreDatabaseType
import org.openprojectx.bigdata.test.core.HiveMetastoreOptions
import javax.inject.Inject

abstract class BigDataTestGradleExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    val autoConfigureJavaExecTasks: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val autoConfigureTestTasks: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val injectRawEndpointProperties: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    val injectNamespacedEndpointProperties: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    val injectEnvironmentVariables: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    val config: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    /** Additional named stacks, where each value is a standalone TOML config location. */
    val instances: MapProperty<String, String> =
        objects.mapProperty(String::class.java, String::class.java).convention(emptyMap())
    val extensionConfig: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    val containerLogLevels: MapProperty<String, String> =
        objects.mapProperty(String::class.java, String::class.java).convention(emptyMap())
    internal val containerCustomizations: ListProperty<String> =
        objects.listProperty(String::class.java).convention(emptyList())
    val extensionRuntime: BigDataTestGradleExtensionRuntime =
        objects.newInstance(BigDataTestGradleExtensionRuntime::class.java)

    val services: BigDataTestGradleServices = objects.newInstance(BigDataTestGradleServices::class.java)
    val ports: BigDataTestGradlePorts = objects.newInstance(BigDataTestGradlePorts::class.java)
    val kerberos: BigDataTestGradleKerberos = objects.newInstance(BigDataTestGradleKerberos::class.java)
    val tls: BigDataTestGradleTls = objects.newInstance(BigDataTestGradleTls::class.java)
    val hdfs: BigDataTestGradleHdfs = objects.newInstance(BigDataTestGradleHdfs::class.java)
    val hiveMetastore: BigDataTestGradleHiveMetastore = objects.newInstance(BigDataTestGradleHiveMetastore::class.java)
    val clouderaHms: BigDataTestGradleClouderaHms = objects.newInstance(BigDataTestGradleClouderaHms::class.java)
    val kafka: BigDataTestGradleKafka = objects.newInstance(BigDataTestGradleKafka::class.java)
    val s3: BigDataTestGradleObjectStore = objects.newInstance(BigDataTestGradleObjectStore::class.java)
    val fakeGcs: BigDataTestGradleObjectStore = objects.newInstance(BigDataTestGradleObjectStore::class.java)
    val icebergRestCatalog: BigDataTestGradleIcebergRestCatalog =
        objects.newInstance(BigDataTestGradleIcebergRestCatalog::class.java)
    val trino: BigDataTestGradleTrino = objects.newInstance(BigDataTestGradleTrino::class.java)
    val containerLogs: BigDataTestGradleContainerLogs = objects.newInstance(BigDataTestGradleContainerLogs::class.java)

    fun services(action: Action<in BigDataTestGradleServices>) {
        action.execute(services)
    }

    fun ports(action: Action<in BigDataTestGradlePorts>) {
        action.execute(ports)
    }

    fun kerberos(action: Action<in BigDataTestGradleKerberos>) {
        action.execute(kerberos)
    }

    fun tls(action: Action<in BigDataTestGradleTls>) {
        action.execute(tls)
    }

    fun hdfs(action: Action<in BigDataTestGradleHdfs>) {
        action.execute(hdfs)
    }

    fun hiveMetastore(action: Action<in BigDataTestGradleHiveMetastore>) {
        action.execute(hiveMetastore)
    }

    fun clouderaHms(action: Action<in BigDataTestGradleClouderaHms>) {
        action.execute(clouderaHms)
    }

    fun kafka(action: Action<in BigDataTestGradleKafka>) {
        action.execute(kafka)
    }

    fun s3(action: Action<in BigDataTestGradleObjectStore>) {
        action.execute(s3)
    }

    fun fakeGcs(action: Action<in BigDataTestGradleObjectStore>) {
        action.execute(fakeGcs)
    }

    fun icebergRestCatalog(action: Action<in BigDataTestGradleIcebergRestCatalog>) {
        action.execute(icebergRestCatalog)
    }

    fun trino(action: Action<in BigDataTestGradleTrino>) {
        action.execute(trino)
    }

    fun containerLogs(action: Action<in BigDataTestGradleContainerLogs>) {
        action.execute(containerLogs)
    }

    fun extensionRuntime(action: Action<in BigDataTestGradleExtensionRuntime>) {
        action.execute(extensionRuntime)
    }
}

abstract class BigDataTestGradleExtensionRuntime @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    val autoDetect: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    val useShadedArtifact: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    val extensionsVersion: Property<String> = objects.property(String::class.java)
    val sparkVersion: Property<String> = objects.property(String::class.java).convention("3.5.7")
    val icebergVersion: Property<String> = objects.property(String::class.java).convention("1.11.0")
    val hadoopVersion: Property<String> = objects.property(String::class.java).convention("3.4.2")
    val confluentVersion: Property<String> = objects.property(String::class.java).convention("8.2.1")
    val avroVersion: Property<String> = objects.property(String::class.java).convention("1.12.1")
    val lz4Version: Property<String> = objects.property(String::class.java).convention("1.10.1")
    val awsSdkVersion: Property<String> = objects.property(String::class.java).convention("2.41.5")
    val trinoVersion: Property<String> = objects.property(String::class.java).convention("483")
    val includeHadoop: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val includeKafkaAvro: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val includeSpark: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val includeTrinoJdbc: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
}

abstract class BigDataTestGradleServices @Inject constructor(objects: ObjectFactory) {
    val kerberos: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val hdfs: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val hiveMetastore: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val clouderaHms: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val kafka: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val schemaRegistry: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val kafkaUi: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val s3: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val fakeGcs: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val icebergRestCatalog: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val trino: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
}

abstract class BigDataTestGradlePorts @Inject constructor(objects: ObjectFactory) {
    val sameHostPorts: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val kerberosKdc: Property<Int> = objects.property(Int::class.java).convention(0)
    val hdfsNameNode: Property<Int> = objects.property(Int::class.java).convention(0)
    val hdfsDataNode: Property<Int> = objects.property(Int::class.java).convention(0)
    val hdfsWeb: Property<Int> = objects.property(Int::class.java).convention(0)
    val hiveMetastore: Property<Int> = objects.property(Int::class.java).convention(0)
    val kafka: Property<Int> = objects.property(Int::class.java).convention(0)
    val schemaRegistry: Property<Int> = objects.property(Int::class.java).convention(0)
    val kafkaUi: Property<Int> = objects.property(Int::class.java).convention(0)
    val s3: Property<Int> = objects.property(Int::class.java).convention(0)
    val fakeGcs: Property<Int> = objects.property(Int::class.java).convention(0)
    val icebergRestCatalog: Property<Int> = objects.property(Int::class.java).convention(0)
    val icebergRestCatalogTls: Property<Int> = objects.property(Int::class.java).convention(0)
    val trino: Property<Int> = objects.property(Int::class.java).convention(0)
}

abstract class BigDataTestGradleTrino @Inject constructor(objects: ObjectFactory) {
    val image: Property<String> = objects.property(String::class.java).convention(DEFAULT_TRINO_IMAGE)
    val catalogName: Property<String> = objects.property(String::class.java).convention("hive")
    val startupTimeoutSeconds: Property<Int> = objects.property(Int::class.java).convention(180)
    val catalogProperties: MapProperty<String, String> =
        objects.mapProperty(String::class.java, String::class.java).convention(emptyMap())
}

abstract class BigDataTestGradleIcebergRestCatalog @Inject constructor(objects: ObjectFactory) {
    val image: Property<String> = objects.property(String::class.java).convention(DEFAULT_ICEBERG_REST_CATALOG_IMAGE)
    val catalogName: Property<String> = objects.property(String::class.java).convention("bigdata_test")
    val warehouse: Property<String> = objects.property(String::class.java).convention("file:///tmp/iceberg/warehouse")
    val realm: Property<String> = objects.property(String::class.java).convention("POLARIS")
    val clientId: Property<String> = objects.property(String::class.java).convention("root")
    val clientSecret: Property<String> = objects.property(String::class.java).convention("s3cr3t")
    val scope: Property<String> = objects.property(String::class.java).convention("PRINCIPAL_ROLE:ALL")
    val s3RoleArn: Property<String> = objects.property(String::class.java).convention("")
    val s3ExternalId: Property<String> = objects.property(String::class.java).convention("")
    val startupTimeoutSeconds: Property<Int> = objects.property(Int::class.java).convention(180)
    val tlsEnabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val tlsDomain: Property<String> = objects.property(String::class.java).convention("localhost")
}

abstract class BigDataTestGradleKerberos @Inject constructor(objects: ObjectFactory) {
    val image: Property<String> = objects.property(String::class.java)
        .convention(DEFAULT_KERBEROS_IMAGE)
    val realm: Property<String> = objects.property(String::class.java).convention("EXAMPLE.COM")
    val domain: Property<String> = objects.property(String::class.java).convention("example.com")
    val clientPrincipal: Property<String> = objects.property(String::class.java).convention("app_user@EXAMPLE.COM")
    val clientPassword: Property<String> = objects.property(String::class.java).convention("app-user-secret")
    val materialDirectory: Property<String> = objects.property(String::class.java).convention("")
    val localKrb5ConfPath: Property<String> = objects.property(String::class.java).convention("")
    val localClientKeytabPath: Property<String> = objects.property(String::class.java).convention("")
    val startupTimeoutSeconds: Property<Int> = objects.property(Int::class.java).convention(120)
    val materialTimeoutSeconds: Property<Int> = objects.property(Int::class.java).convention(30)
    val adminAttempts: Property<Int> = objects.property(Int::class.java).convention(30)
    val adminRetryDelaySeconds: Property<Int> = objects.property(Int::class.java).convention(1)
    val debug: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
}

abstract class BigDataTestGradleTls @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val caCertPath: Property<String> = objects.property(String::class.java).convention("")
    val caKeyPath: Property<String> = objects.property(String::class.java).convention("")
    val trustStorePath: Property<String> = objects.property(String::class.java).convention("")
    val trustStorePassword: Property<String> = objects.property(String::class.java).convention("changeit")
    val haproxyImage: Property<String> = objects.property(String::class.java).convention(DEFAULT_HAPROXY_IMAGE)
}

abstract class BigDataTestGradleHdfs @Inject constructor(objects: ObjectFactory) {
    val image: Property<String> = objects.property(String::class.java).convention(DEFAULT_HDFS_IMAGE)
    val kerberosEnabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val dataNodeHostname: Property<String> = objects.property(String::class.java).convention("hdfs")
    val localHdfsSitePath: Property<String> = objects.property(String::class.java).convention("")
}

abstract class BigDataTestGradleHiveMetastore @Inject constructor(objects: ObjectFactory) {
    val image: Property<String> = objects.property(String::class.java)
        .convention(HiveMetastoreOptions.DEFAULT_IMAGE)
    val databaseType: Property<HiveMetastoreDatabaseType> =
        objects.property(HiveMetastoreDatabaseType::class.java).convention(HiveMetastoreDatabaseType.POSTGRESQL)
    val databaseImage: Property<String> = objects.property(String::class.java).convention(
        databaseType.map {
            when (it) {
                HiveMetastoreDatabaseType.POSTGRESQL -> HiveMetastoreOptions.DEFAULT_POSTGRES_IMAGE
                HiveMetastoreDatabaseType.MYSQL -> HiveMetastoreOptions.DEFAULT_MYSQL_IMAGE
            }
        },
    )
    val databaseName: Property<String> = objects.property(String::class.java).convention("metastore")
    val databaseUser: Property<String> = objects.property(String::class.java).convention("hive")
    val databasePassword: Property<String> = objects.property(String::class.java).convention("hive")
    val databaseHostPort: Property<Int> = objects.property(Int::class.java).convention(0)
    val warehouseDir: Property<String> = objects.property(String::class.java).convention("/user/hive/warehouse")
    val localHiveSitePath: Property<String> = objects.property(String::class.java).convention("")
    val localMetastoreSitePath: Property<String> = objects.property(String::class.java).convention("")
    val kerberosEnabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
}

abstract class BigDataTestGradleClouderaHms @Inject constructor(objects: ObjectFactory) {
    val databaseType: Property<ClouderaHmsDatabaseType> =
        objects.property(ClouderaHmsDatabaseType::class.java).convention(ClouderaHmsDatabaseType.POSTGRESQL)
    val image: Property<String> = objects.property(String::class.java).convention(
        databaseType.map {
            when (it) {
                ClouderaHmsDatabaseType.POSTGRESQL -> HiveMetastoreOptions.DEFAULT_CLOUDERA_IMAGE
                ClouderaHmsDatabaseType.MARIADB -> HiveMetastoreOptions.DEFAULT_CLOUDERA_MARIADB_IMAGE
            }
        },
    )
    val databaseName: Property<String> = objects.property(String::class.java).convention("metastore")
    val databaseUser: Property<String> = objects.property(String::class.java).convention("hive")
    val databasePassword: Property<String> = objects.property(String::class.java).convention("hive")
    val databaseHostPort: Property<Int> = objects.property(Int::class.java).convention(0)
    val warehouseDir: Property<String> =
        objects.property(String::class.java).convention(HiveMetastoreOptions.DEFAULT_CLOUDERA_WAREHOUSE_DIR)
    val kerberosEnabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
}

abstract class BigDataTestGradleKafka @Inject constructor(objects: ObjectFactory) {
    val image: Property<String> = objects.property(String::class.java).convention(DEFAULT_KAFKA_IMAGE)
    val startupTimeoutSeconds: Property<Int> = objects.property(Int::class.java).convention(180)
    val schemaRegistryImage: Property<String> = objects.property(String::class.java)
        .convention(DEFAULT_SCHEMA_REGISTRY_IMAGE)
    val kafkaUiImage: Property<String> = objects.property(String::class.java).convention(DEFAULT_KAFKA_UI_IMAGE)
    val kerberosEnabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val kafkaUiKerberosEnabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
}

abstract class BigDataTestGradleObjectStore @Inject constructor(objects: ObjectFactory) {
    val image: Property<String> = objects.property(String::class.java)
}

abstract class BigDataTestGradleContainerLogs @Inject constructor(objects: ObjectFactory) {
    val mode: Property<ContainerLogMode> = objects.property(ContainerLogMode::class.java).convention(ContainerLogMode.NONE)
    val directory: Property<String> = objects.property(String::class.java).convention("build/bigdata-test-container-logs")
    val append: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
}
