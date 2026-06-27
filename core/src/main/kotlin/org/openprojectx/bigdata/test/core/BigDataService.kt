package org.openprojectx.bigdata.test.core

enum class BigDataService(
    val defaultPorts: Map<String, Int>,
    val endpointProperties: Set<String>,
) {
    KERBEROS(
        defaultPorts = mapOf("kdc" to 88),
        endpointProperties = setOf(
            "bigdata.test.kerberos.realm",
            "bigdata.test.kerberos.kdc",
            "bigdata.test.kerberos.krb5-conf",
            "bigdata.test.kerberos.client-principal",
            "bigdata.test.kerberos.client-password",
            "bigdata.test.kerberos.client-keytab",
            "bigdata.test.kerberos.client-keytab.container",
        ),
    ),
    HDFS(
        defaultPorts = mapOf("namenode" to 8020, "datanode" to 9866, "web" to 9870, "web-tls" to 443),
        endpointProperties = setOf(
            "fs.defaultFS",
            "spring.hadoop.fs-uri",
            "dfs.client.use.datanode.hostname",
            "dfs.datanode.hostname",
            "dfs.namenode.https-address",
        ),
    ),
    HIVE_METASTORE(
        defaultPorts = mapOf("thrift" to 9083),
        endpointProperties = setOf(
            "hive.metastore.uris",
            "spring.bigdata.test.hive-metastore.thrift-uri",
            "hive.metastore.use.SSL",
            "hive.metastore.truststore.path",
            "hive.metastore.truststore.password",
        ),
    ),
    KAFKA(
        defaultPorts = mapOf("bootstrap" to 9092),
        endpointProperties = setOf("bootstrap.servers", "spring.kafka.bootstrap-servers", "bootstrap.servers.internal"),
    ),
    SCHEMA_REGISTRY(
        defaultPorts = mapOf("http" to 8085, "https" to 443),
        endpointProperties = setOf("schema.registry.url"),
    ),
    KAFKA_UI(
        defaultPorts = mapOf("http" to 8080, "https" to 443),
        endpointProperties = setOf("bigdata.test.kafka-ui.url"),
    ),
    LOCALSTACK_S3(
        defaultPorts = mapOf("edge" to 4566, "https" to 443),
        endpointProperties = setOf(
            "spring.cloud.aws.s3.endpoint",
            "aws.endpoint-url.s3",
            "aws.accessKeyId",
            "aws.secretAccessKey",
            "aws.region",
        ),
    ),
    FAKE_GCS(
        defaultPorts = mapOf("http" to 4588, "https" to 443),
        endpointProperties = setOf("bigdata.test.gcs.endpoint", "google.cloud.storage.host"),
    ),
}
