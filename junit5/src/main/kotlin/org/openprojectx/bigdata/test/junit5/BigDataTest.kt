package org.openprojectx.bigdata.test.junit5

import org.junit.jupiter.api.extension.ExtendWith

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ExtendWith(BigDataTestExtension::class)
annotation class BigDataTest(
    val kerberos: Boolean = false,
    val hdfs: Boolean = false,
    val hdfsKerberos: Boolean = false,
    val hiveMetastore: Boolean = false,
    val hiveMetastoreKerberos: Boolean = false,
    val kafka: Boolean = false,
    val kafkaKerberos: Boolean = false,
    val schemaRegistry: Boolean = false,
    val schemaRegistryKerberos: Boolean = false,
    val kafkaUi: Boolean = false,
    val kafkaUiKerberos: Boolean = false,
    val localStackS3: Boolean = false,
    val fakeGcs: Boolean = false,
)
