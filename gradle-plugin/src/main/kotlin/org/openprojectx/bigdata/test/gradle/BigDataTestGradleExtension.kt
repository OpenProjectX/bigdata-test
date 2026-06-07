package org.openprojectx.bigdata.test.gradle

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.openprojectx.bigdata.test.core.ContainerLogMode
import javax.inject.Inject

abstract class BigDataTestGradleExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    val autoConfigureJavaExecTasks: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    val autoConfigureTestTasks: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    val injectRawEndpointProperties: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    val injectNamespacedEndpointProperties: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    val injectEnvironmentVariables: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    val extensionConfig: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    val containerLogLevels: MapProperty<String, String> =
        objects.mapProperty(String::class.java, String::class.java).convention(emptyMap())

    val services: BigDataTestGradleServices = objects.newInstance(BigDataTestGradleServices::class.java)
    val ports: BigDataTestGradlePorts = objects.newInstance(BigDataTestGradlePorts::class.java)
    val kerberos: BigDataTestGradleKerberos = objects.newInstance(BigDataTestGradleKerberos::class.java)
    val tls: BigDataTestGradleTls = objects.newInstance(BigDataTestGradleTls::class.java)
    val hdfs: BigDataTestGradleHdfs = objects.newInstance(BigDataTestGradleHdfs::class.java)
    val hiveMetastore: BigDataTestGradleHiveMetastore = objects.newInstance(BigDataTestGradleHiveMetastore::class.java)
    val clouderaHms: BigDataTestGradleClouderaHms = objects.newInstance(BigDataTestGradleClouderaHms::class.java)
    val kafka: BigDataTestGradleKafka = objects.newInstance(BigDataTestGradleKafka::class.java)
    val localStackS3: BigDataTestGradleObjectStore = objects.newInstance(BigDataTestGradleObjectStore::class.java)
    val fakeGcs: BigDataTestGradleObjectStore = objects.newInstance(BigDataTestGradleObjectStore::class.java)
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

    fun localStackS3(action: Action<in BigDataTestGradleObjectStore>) {
        action.execute(localStackS3)
    }

    fun fakeGcs(action: Action<in BigDataTestGradleObjectStore>) {
        action.execute(fakeGcs)
    }

    fun containerLogs(action: Action<in BigDataTestGradleContainerLogs>) {
        action.execute(containerLogs)
    }
}

abstract class BigDataTestGradleServices @Inject constructor(objects: ObjectFactory) {
    val kerberos: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val hdfs: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val hiveMetastore: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val clouderaHms: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val kafka: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val schemaRegistry: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val kafkaUi: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val localStackS3: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val fakeGcs: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
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
    val localStackS3: Property<Int> = objects.property(Int::class.java).convention(0)
    val fakeGcs: Property<Int> = objects.property(Int::class.java).convention(0)
}

abstract class BigDataTestGradleKerberos @Inject constructor(objects: ObjectFactory) {
    val image: Property<String> = objects.property(String::class.java)
        .convention("ghcr.io/openprojectx/directory-kerby/kerby-kdc:latest")
    val realm: Property<String> = objects.property(String::class.java).convention("EXAMPLE.COM")
    val domain: Property<String> = objects.property(String::class.java).convention("example.com")
    val clientPrincipal: Property<String> = objects.property(String::class.java).convention("app_user@EXAMPLE.COM")
    val clientPassword: Property<String> = objects.property(String::class.java).convention("app-user-secret")
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
    val haproxyImage: Property<String> = objects.property(String::class.java).convention("haproxy:3.0-alpine")
}

abstract class BigDataTestGradleHdfs @Inject constructor(objects: ObjectFactory) {
    val image: Property<String> = objects.property(String::class.java).convention("apache/hadoop:3.5.0")
    val kerberosEnabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val dataNodeHostname: Property<String> = objects.property(String::class.java).convention("hdfs")
}

abstract class BigDataTestGradleHiveMetastore @Inject constructor(objects: ObjectFactory) {
    val image: Property<String> = objects.property(String::class.java)
        .convention("ghcr.io/openprojectx/hive:3.1.3-hadoop-3.4.2-gcs-4.0.4-jdk17-0.1.4")
    val databaseImage: Property<String> = objects.property(String::class.java).convention("postgres:16-alpine")
    val databaseName: Property<String> = objects.property(String::class.java).convention("metastore")
    val databaseUser: Property<String> = objects.property(String::class.java).convention("hive")
    val databasePassword: Property<String> = objects.property(String::class.java).convention("hive")
    val warehouseDir: Property<String> = objects.property(String::class.java).convention("/user/hive/warehouse")
    val kerberosEnabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
}

abstract class BigDataTestGradleClouderaHms @Inject constructor(objects: ObjectFactory) {
    val image: Property<String> = objects.property(String::class.java).convention("ghcr.io/openprojectx/cloudera-hms:0.1.16")
    val warehouseDir: Property<String> = objects.property(String::class.java).convention("/user/hive/warehouse")
    val kerberosEnabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
}

abstract class BigDataTestGradleKafka @Inject constructor(objects: ObjectFactory) {
    val image: Property<String> = objects.property(String::class.java).convention("apache/kafka:4.1.2")
    val schemaRegistryImage: Property<String> = objects.property(String::class.java)
        .convention("confluentinc/cp-schema-registry:7.8.0")
    val kafkaUiImage: Property<String> = objects.property(String::class.java).convention("ghcr.io/kafbat/kafka-ui:latest")
    val kerberosEnabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val kafkaUiKerberosEnabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
}

abstract class BigDataTestGradleObjectStore @Inject constructor(objects: ObjectFactory) {
    val image: Property<String> = objects.property(String::class.java)
}

abstract class BigDataTestGradleContainerLogs @Inject constructor(objects: ObjectFactory) {
    val mode: Property<ContainerLogMode> = objects.property(ContainerLogMode::class.java).convention(ContainerLogMode.NONE)
    val directory: Property<String> = objects.property(String::class.java).convention("build/bigdata-test-container-logs")
}
