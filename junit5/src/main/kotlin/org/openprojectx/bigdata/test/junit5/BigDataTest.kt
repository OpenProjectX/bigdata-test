package org.openprojectx.bigdata.test.junit5

import org.junit.jupiter.api.extension.ExtendWith
import org.openprojectx.bigdata.test.core.ContainerLogMode

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ExtendWith(BigDataTestExtension::class)
annotation class BigDataTest(
    val kerberos: Boolean = false,
    val kerberosClientPrincipal: String = "app_user@EXAMPLE.COM",
    val kerberosClientPassword: String = "app-user-secret",
    val hdfs: Boolean = false,
    val hdfsKerberos: Boolean = false,
    val hiveMetastore: Boolean = false,
    val hiveMetastoreKerberos: Boolean = false,
    val kafka: Boolean = false,
    val kafkaKerberos: Boolean = false,
    val schemaRegistry: Boolean = false,
    val kafkaUi: Boolean = false,
    val kafkaUiKerberos: Boolean = false,
    val localStackS3: Boolean = false,
    val fakeGcs: Boolean = false,
    val sameHostPorts: Boolean = false,
    val kerberosKdcPort: Int = 0,
    val hdfsNameNodePort: Int = 0,
    val hdfsWebPort: Int = 0,
    val hiveMetastorePort: Int = 0,
    val kafkaPort: Int = 0,
    val schemaRegistryPort: Int = 0,
    val kafkaUiPort: Int = 0,
    val localStackS3Port: Int = 0,
    val fakeGcsPort: Int = 0,
    val containerLogMode: ContainerLogMode = ContainerLogMode.NONE,
    val containerLogDirectory: String = "build/bigdata-test-container-logs",
)
