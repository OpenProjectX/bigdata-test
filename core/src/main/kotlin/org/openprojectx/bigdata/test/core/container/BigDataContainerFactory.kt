package org.openprojectx.bigdata.test.core.container

import org.openprojectx.bigdata.test.core.BigDataEndpoint
import org.openprojectx.bigdata.test.core.BigDataService
import org.openprojectx.bigdata.test.core.BigDataTestKitOptions
import org.openprojectx.bigdata.test.core.KerberosAuthOptions
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.Network
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.Transferable
import org.testcontainers.lifecycle.Startable
import org.testcontainers.utility.DockerImageName
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Locale

internal class BigDataContainerFactory(
    private val options: BigDataTestKitOptions,
) : AutoCloseable {
    private val network = Network.newNetwork()
    private val supportContainers = mutableListOf<Startable>()
    private val kerberosDir: Path? = if (kerberosRequired()) Files.createTempDirectory("bigdata-test-kerberos-") else null

    fun create(): List<BigDataServiceContainer> {
        val containers = mutableListOf<BigDataServiceContainer>()
        if (kerberosRequired()) containers += kerberos()
        if (options.hdfs.enabled) containers += hdfs()
        if (options.hiveMetastore.enabled) containers += hiveMetastore()
        if (options.kafka.enabled) {
            containers += kafka()
            if (options.kafka.schemaRegistryEnabled) containers += schemaRegistry()
            if (options.kafka.kafkaUiEnabled) containers += kafkaUi()
        }
        if (options.localStackS3.enabled) containers += localStackS3()
        if (options.fakeGcs.enabled) containers += fakeGcs()
        return containers
    }

    override fun close() {
        supportContainers.asReversed().forEach { it.stop() }
        network.close()
    }

    private fun kerberosRequired(): Boolean =
        options.kerberos.enabled ||
            options.hdfs.kerberos.enabled ||
            options.hiveMetastore.kerberos.enabled ||
            options.kafka.kerberos.enabled ||
            options.kafka.schemaRegistryKerberos.enabled ||
            options.kafka.kafkaUiKerberos.enabled

    private fun kerberos(): BigDataServiceContainer {
        val kerberos = options.kerberos
        val servicePrincipals = buildList {
            addIfEnabled(options.hdfs.kerberos)
            addIfEnabled(options.hiveMetastore.kerberos)
            addIfEnabled(options.kafka.kerberos)
            addIfEnabled(options.kafka.schemaRegistryKerberos)
            addIfEnabled(options.kafka.kafkaUiKerberos)
            if (options.kafka.kerberos.enabled && options.kafka.schemaRegistryEnabled) addPrincipal(options.kafka.schemaRegistryKerberos)
            if (options.kafka.kerberos.enabled && options.kafka.kafkaUiEnabled) addPrincipal(options.kafka.kafkaUiKerberos)
        }

        val container = GenericBigDataContainer(kerberos.image)
            .withNetwork(network)
            .withNetworkAliases("kerby-kdc")
            .withExposedPorts(88)
            .withFileSystemBind(kerberosDirectory(), "/var/lib/kerby")
            .withEnv("KERBY_REALM", kerberos.realm)
            .withEnv("KERBY_KDC_HOST", "kerby-kdc")
            .withEnv("KERBY_KDC_BIND_HOST", "0.0.0.0")
            .withEnv("KERBY_CLIENT_KDC_HOST", "kerby-kdc")
            .withEnv("KERBY_CLIENT_DOMAIN", kerberos.domain)
            .withEnv("KERBY_PREAUTH_REQUIRED", "false")
            .withEnv("KERBY_PA_ENC_TIMESTAMP_REQUIRED", "false")
            .withEnv("KERBY_CLIENT_PRINCIPAL", kerberos.clientPrincipal)
            .withEnv("KERBY_CLIENT_PASSWORD", kerberos.clientPassword)
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2)))
        if (servicePrincipals.isNotEmpty()) {
            container.withEnv("KERBY_EXTRA_SERVICE_PRINCIPALS", servicePrincipals.joinToString(","))
        }

        return BigDataServiceContainer(BigDataService.KERBEROS, container) {
            BigDataEndpoint(
                service = BigDataService.KERBEROS,
                host = container.host,
                ports = mapOf("kdc" to container.getMappedPort(88)),
                properties = mapOf(
                    "bigdata.test.kerberos.realm" to kerberos.realm,
                    "bigdata.test.kerberos.kdc" to "${container.host}:${container.getMappedPort(88)}",
                    "bigdata.test.kerberos.client-principal" to kerberos.clientPrincipal,
                    "bigdata.test.kerberos.client-password" to kerberos.clientPassword,
                ),
            )
        }
    }

    private fun hdfs(): BigDataServiceContainer {
        val hdfs = options.hdfs
        val container = GenericBigDataContainer(hdfs.image)
            .withNetwork(network)
            .withNetworkAliases("hdfs", "hdfs.example.com")
            .withExposedPorts(hdfs.nameNodePort, hdfs.webPort)
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(3)))
        if (hdfs.kerberos.enabled) {
            mountKerberos(container)
            container
                .withEnv("KRB5_CONFIG", "/kerby/client/krb5.conf")
                .withEnv("HADOOP_OPTS", "-Djava.security.krb5.conf=/kerby/client/krb5.conf")
                .withEnv("HADOOP_SECURITY_AUTHENTICATION", "kerberos")
                .withEnv("HDFS_NAMENODE_KERBEROS_PRINCIPAL", hdfs.kerberos.servicePrincipal)
                .withEnv("HDFS_NAMENODE_KEYTAB_FILE", hdfs.kerberos.keytabPath)
        }

        return BigDataServiceContainer(BigDataService.HDFS, container) {
            val nameNode = "${container.host}:${container.getMappedPort(hdfs.nameNodePort)}"
            BigDataEndpoint(
                service = BigDataService.HDFS,
                host = container.host,
                ports = mapOf(
                    "namenode" to container.getMappedPort(hdfs.nameNodePort),
                    "web" to container.getMappedPort(hdfs.webPort),
                ),
                properties = mapOf(
                    "fs.defaultFS" to "hdfs://$nameNode",
                    "spring.hadoop.fs-uri" to "hdfs://$nameNode",
                ) + kerberosProperties("hadoop", hdfs.kerberos),
            )
        }
    }

    private fun hiveMetastore(): BigDataServiceContainer {
        val hive = options.hiveMetastore
        val image = DockerImageName.parse("postgres:14")
        val postgres = PostgreSQLContainer(image)
            .withDatabaseName(hive.databaseName)
            .withUsername(hive.databaseUser)
            .withPassword(hive.databasePassword)
            .withNetwork(network)
            .withNetworkAliases("hms-postgres")
        supportContainers += postgres

        val container = GenericBigDataContainer(hive.image)
            .withNetwork(network)
            .withNetworkAliases("hive-metastore", "hive-metastore.example.com")
            .withExposedPorts(9083)
            .withEnv("POSTGRES_DB", hive.databaseName)
            .withEnv("POSTGRES_USER", hive.databaseUser)
            .withEnv("POSTGRES_PASSWORD", hive.databasePassword)
            .withEnv("HMS_JDBC_URL", "jdbc:postgresql://hms-postgres:5432/${hive.databaseName}")
            .withEnv("HMS_JDBC_USER", hive.databaseUser)
            .withEnv("HMS_JDBC_PASSWORD", hive.databasePassword)
            .withEnv("HMS_WAREHOUSE_DIR", hive.warehouseDir)
            .dependsOn(postgres)
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(3)))
        if (hive.kerberos.enabled) {
            mountKerberos(container)
            container
                .withEnv("KRB5_CONFIG", "/kerby/client/krb5.conf")
                .withEnv("HMS_CONF_HIVE_METASTORE_SASL_ENABLED", "true")
                .withEnv("HMS_CONF_HIVE_METASTORE_KERBEROS_PRINCIPAL", hive.kerberos.servicePrincipal)
                .withEnv("HMS_CONF_HIVE_METASTORE_KERBEROS_KEYTAB_FILE", hive.kerberos.keytabPath)
                .withEnv("HMS_CONF_HADOOP_SECURITY_AUTHENTICATION", "kerberos")
        }

        hive.extraConfiguration.forEach { (key, value) ->
            container.withEnv("HMS_CONF_${encodeConfigKey(key)}", value)
        }

        return BigDataServiceContainer(BigDataService.HIVE_METASTORE, container) {
            val thriftUri = "thrift://${container.host}:${container.getMappedPort(9083)}"
            BigDataEndpoint(
                service = BigDataService.HIVE_METASTORE,
                host = container.host,
                ports = mapOf("thrift" to container.getMappedPort(9083)),
                properties = mapOf(
                    "hive.metastore.uris" to thriftUri,
                    "spring.bigdata.test.hive-metastore.thrift-uri" to thriftUri,
                ) + kerberosProperties("hive.metastore", hive.kerberos),
            )
        }
    }

    private fun kafka(): BigDataServiceContainer {
        val kafka = options.kafka
        val container = GenericBigDataContainer(kafka.image)
            .withNetwork(network)
            .withNetworkAliases("kafka", "broker1.example.com")
            .withExposedPorts(9092)
            .withEnv("KAFKA_NODE_ID", "1")
            .withEnv("KAFKA_PROCESS_ROLES", "broker,controller")
            .withEnv("KAFKA_CONTROLLER_QUORUM_VOTERS", "1@kafka:29093")
            .withEnv("KAFKA_LISTENERS", "PLAINTEXT://0.0.0.0:9092,CONTROLLER://kafka:29093")
            .withEnv("KAFKA_ADVERTISED_LISTENERS", "PLAINTEXT://kafka:9092")
            .withEnv("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", "PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT")
            .withEnv("KAFKA_CONTROLLER_LISTENER_NAMES", "CONTROLLER")
            .withEnv("KAFKA_INTER_BROKER_LISTENER_NAME", "PLAINTEXT")
            .withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1")
            .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
            .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
            .withEnv("CLUSTER_ID", kafka.clusterId)
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(3)))
        if (kafka.kerberos.enabled) {
            mountKerberos(container)
            container
                .withEnv("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", "CONTROLLER:PLAINTEXT,SASL_PLAINTEXT:SASL_PLAINTEXT")
                .withEnv("KAFKA_ADVERTISED_LISTENERS", "SASL_PLAINTEXT://broker1.example.com:9092")
                .withEnv("KAFKA_LISTENERS", "SASL_PLAINTEXT://broker1.example.com:9092,CONTROLLER://broker1.example.com:29093")
                .withEnv("KAFKA_CONTROLLER_QUORUM_VOTERS", "1@broker1.example.com:29093")
                .withEnv("KAFKA_INTER_BROKER_LISTENER_NAME", "SASL_PLAINTEXT")
                .withEnv("KAFKA_SASL_ENABLED_MECHANISMS", "GSSAPI")
                .withEnv("KAFKA_SASL_MECHANISM_INTER_BROKER_PROTOCOL", "GSSAPI")
                .withEnv("KAFKA_SASL_KERBEROS_SERVICE_NAME", kafka.kerberos.servicePrincipal.substringBefore("/"))
                .withEnv("KAFKA_OPTS", "-Djava.security.krb5.conf=/kerby/client/krb5.conf -Djava.security.auth.login.config=/etc/kafka/kerberos/kafka_server_jaas.conf")
                .withEnv("KRB5_CONFIG", "/kerby/client/krb5.conf")
                .withCopyToContainer(
                    Transferable.of(kafkaJaas(kafka.kerberos)),
                    "/etc/kafka/kerberos/kafka_server_jaas.conf",
                )
        }

        return BigDataServiceContainer(BigDataService.KAFKA, container) {
            val bootstrapServers = "${container.host}:${container.getMappedPort(9092)}"
            BigDataEndpoint(
                service = BigDataService.KAFKA,
                host = container.host,
                ports = mapOf("bootstrap" to container.getMappedPort(9092)),
                properties = mapOf(
                    "bootstrap.servers" to bootstrapServers,
                    "spring.kafka.bootstrap-servers" to bootstrapServers,
                ) + kafkaClientKerberosProperties(kafka.kerberos),
            )
        }
    }

    private fun schemaRegistry(): BigDataServiceContainer {
        val kafka = options.kafka
        val container = GenericBigDataContainer(kafka.schemaRegistryImage)
            .withNetwork(network)
            .withNetworkAliases("schema-registry")
            .withExposedPorts(8085)
            .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
            .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8085")
            .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://kafka:9092")
            .waitingFor(Wait.forHttp("/subjects").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(3)))
        if (kafka.schemaRegistryKerberos.enabled || kafka.kerberos.enabled) {
            mountKerberos(container)
            val registryPrincipal = kafka.schemaRegistryKerberos
            container
                .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "SASL_PLAINTEXT://broker1.example.com:9092")
                .withEnv("SCHEMA_REGISTRY_KAFKASTORE_SECURITY_PROTOCOL", "SASL_PLAINTEXT")
                .withEnv("SCHEMA_REGISTRY_KAFKASTORE_SASL_MECHANISM", "GSSAPI")
                .withEnv("SCHEMA_REGISTRY_KAFKASTORE_SASL_KERBEROS_SERVICE_NAME", "kafka")
                .withEnv("SCHEMA_REGISTRY_KAFKASTORE_SASL_JAAS_CONFIG", inlineJaas(registryPrincipal))
                .withEnv("KAFKA_OPTS", "-Djava.security.krb5.conf=/kerby/client/krb5.conf")
                .withEnv("KRB5_CONFIG", "/kerby/client/krb5.conf")
                .withEnv("SCHEMA_REGISTRY_OPTS", "-Djava.security.krb5.conf=/kerby/client/krb5.conf")
        }

        return BigDataServiceContainer(BigDataService.SCHEMA_REGISTRY, container) {
            val url = "http://${container.host}:${container.getMappedPort(8085)}"
            BigDataEndpoint(
                service = BigDataService.SCHEMA_REGISTRY,
                host = container.host,
                ports = mapOf("http" to container.getMappedPort(8085)),
                properties = mapOf("schema.registry.url" to url),
            )
        }
    }

    private fun kafkaUi(): BigDataServiceContainer {
        val kafka = options.kafka
        val container = GenericBigDataContainer(kafka.kafkaUiImage)
            .withNetwork(network)
            .withNetworkAliases("kafka-ui")
            .withExposedPorts(8080)
            .withEnv("KAFKA_CLUSTERS_0_NAME", "local")
            .withEnv("KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS", "kafka:9092")
            .withEnv("DYNAMIC_CONFIG_ENABLED", "false")
            .waitingFor(Wait.forHttp("/").withStartupTimeout(Duration.ofMinutes(3)))
        if (kafka.kafkaUiKerberos.enabled || kafka.kerberos.enabled) {
            mountKerberos(container)
            container
                .withEnv("JAVA_OPTS", "-Djava.security.krb5.conf=/kerby/client/krb5.conf")
                .withEnv("KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS", "broker1.example.com:9092")
                .withEnv("KAFKA_CLUSTERS_0_PROPERTIES_SECURITY_PROTOCOL", "SASL_PLAINTEXT")
                .withEnv("KAFKA_CLUSTERS_0_PROPERTIES_SASL_MECHANISM", "GSSAPI")
                .withEnv("KAFKA_CLUSTERS_0_PROPERTIES_SASL_KERBEROS_SERVICE_NAME", "kafka")
                .withEnv("KAFKA_CLUSTERS_0_PROPERTIES_SASL_JAAS_CONFIG", inlineJaas(kafka.kafkaUiKerberos))
        }

        return BigDataServiceContainer(BigDataService.KAFKA_UI, container) {
            val url = "http://${container.host}:${container.getMappedPort(8080)}"
            BigDataEndpoint(
                service = BigDataService.KAFKA_UI,
                host = container.host,
                ports = mapOf("http" to container.getMappedPort(8080)),
                properties = mapOf("bigdata.test.kafka-ui.url" to url),
            )
        }
    }

    private fun localStackS3(): BigDataServiceContainer {
        val objectStore = options.localStackS3
        val container = GenericBigDataContainer(objectStore.image)
            .withNetwork(network)
            .withNetworkAliases("localstack")
            .withExposedPorts(4566)
            .withEnv("SERVICES", "s3")
            .waitingFor(Wait.forHttp("/_localstack/health").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(3)))

        return BigDataServiceContainer(BigDataService.LOCALSTACK_S3, container) {
            val endpoint = "http://${container.host}:${container.getMappedPort(4566)}"
            BigDataEndpoint(
                service = BigDataService.LOCALSTACK_S3,
                host = container.host,
                ports = mapOf("edge" to container.getMappedPort(4566)),
                properties = mapOf(
                    "spring.cloud.aws.s3.endpoint" to endpoint,
                    "aws.endpoint-url.s3" to endpoint,
                    "aws.accessKeyId" to "test",
                    "aws.secretAccessKey" to "test",
                    "aws.region" to "us-east-1",
                ),
            )
        }
    }

    private fun fakeGcs(): BigDataServiceContainer {
        val objectStore = options.fakeGcs
        val container = GenericBigDataContainer(objectStore.image)
            .withNetwork(network)
            .withNetworkAliases("fake-gcs")
            .withExposedPorts(4443)
            .withCommand("-scheme", "http", "-port", "4443")
            .waitingFor(Wait.forHttp("/storage/v1/b").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(2)))

        return BigDataServiceContainer(BigDataService.FAKE_GCS, container) {
            val endpoint = "http://${container.host}:${container.getMappedPort(4443)}"
            BigDataEndpoint(
                service = BigDataService.FAKE_GCS,
                host = container.host,
                ports = mapOf("http" to container.getMappedPort(4443)),
                properties = mapOf(
                    "bigdata.test.gcs.endpoint" to endpoint,
                    "google.cloud.storage.host" to endpoint,
                ),
            )
        }
    }

    private fun encodeConfigKey(key: String): String =
        key.lowercase(Locale.ROOT)
            .replace("-", "__")
            .replace(".", "_")
            .uppercase(Locale.ROOT)

    private fun MutableList<String>.addIfEnabled(options: KerberosAuthOptions) {
        if (options.enabled) addPrincipal(options)
    }

    private fun MutableList<String>.addPrincipal(options: KerberosAuthOptions) {
        val principal = "${options.servicePrincipal}:${options.keytabPath.replace("/kerby/", "/var/lib/kerby/")}"
        if (!contains(principal)) add(principal)
    }

    private fun mountKerberos(container: GenericBigDataContainer) {
        container.withFileSystemBind(kerberosDirectory(), "/kerby", BindMode.READ_ONLY)
    }

    private fun kerberosDirectory(): String =
        kerberosDir?.toString() ?: error("Kerberos directory was not initialized")

    private fun kerberosProperties(prefix: String, options: KerberosAuthOptions): Map<String, String> =
        if (options.enabled) {
            mapOf(
                "$prefix.security.authentication" to "kerberos",
                "$prefix.kerberos.principal" to options.servicePrincipal,
                "$prefix.kerberos.keytab" to options.keytabPath,
                "java.security.krb5.conf" to "/kerby/client/krb5.conf",
            )
        } else {
            emptyMap()
        }

    private fun kafkaClientKerberosProperties(options: KerberosAuthOptions): Map<String, String> =
        if (options.enabled) {
            mapOf(
                "security.protocol" to "SASL_PLAINTEXT",
                "sasl.mechanism" to "GSSAPI",
                "sasl.kerberos.service.name" to options.servicePrincipal.substringBefore("/"),
                "sasl.jaas.config" to inlineJaas(options),
            )
        } else {
            emptyMap()
        }

    private fun kafkaJaas(options: KerberosAuthOptions): String =
        """
        KafkaServer {
          com.sun.security.auth.module.Krb5LoginModule required
          useKeyTab=true
          storeKey=true
          keyTab="${options.keytabPath}"
          principal="${options.servicePrincipal}";
        };
        """.trimIndent()

    private fun inlineJaas(options: KerberosAuthOptions): String =
        """com.sun.security.auth.module.Krb5LoginModule required useKeyTab=true storeKey=true keyTab="${options.keytabPath}" principal="${options.servicePrincipal}";"""
}
