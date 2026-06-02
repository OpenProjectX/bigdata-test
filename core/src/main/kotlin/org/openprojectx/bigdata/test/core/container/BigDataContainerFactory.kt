package org.openprojectx.bigdata.test.core.container

import org.openprojectx.bigdata.test.core.BigDataEndpoint
import org.openprojectx.bigdata.test.core.BigDataService
import org.openprojectx.bigdata.test.core.BigDataTestKitOptions
import org.openprojectx.bigdata.test.core.ContainerLogMode
import org.openprojectx.bigdata.test.core.HttpTlsOptions
import org.openprojectx.bigdata.test.core.HiveMetastoreDistribution
import org.openprojectx.bigdata.test.core.KerberosAuthOptions
import org.openprojectx.bigdata.test.core.KafkaOptions
import org.openprojectx.bigdata.test.core.KerberosOptions
import org.openprojectx.hive.docker.testcontainers.HiveMetastoreContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.containers.output.OutputFrame
import org.testcontainers.images.builder.Transferable
import org.testcontainers.lifecycle.Startable
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile
import java.io.OutputStreamWriter
import java.io.Closeable
import java.nio.file.Files
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.util.Locale

internal class BigDataContainerFactory(
    private val options: BigDataTestKitOptions,
) : AutoCloseable {
    private val network = Network.newNetwork()
    private val supportContainers = mutableListOf<Startable>()
    private val logConsumers = mutableListOf<Closeable>()
    private val kerberosDir: Path? = if (kerberosRequired()) Files.createTempDirectory("bigdata-test-kerberos-") else null
    private val tlsMaterial: TlsMaterial by lazy { TlsMaterial(options.tls.copy(enabled = true)) }

    fun create(): List<BigDataServiceContainer> {
        val containers = mutableListOf<BigDataServiceContainer>()
        if (kerberosRequired()) containers += kerberos()
        if (options.hdfs.enabled) containers += hdfs()
        if (options.localStackS3.enabled) containers += localStackS3()
        if (options.fakeGcs.enabled) containers += fakeGcs()
        if (options.hiveMetastore.enabled) containers += hiveMetastore()
        if (options.kafka.enabled) {
            containers += kafka()
            if (options.kafka.schemaRegistryEnabled) containers += schemaRegistry()
            if (options.kafka.kafkaUiEnabled) containers += kafkaUi()
        }
        return containers
    }

    override fun close() {
        supportContainers.asReversed().forEach { it.stop() }
        logConsumers.asReversed().forEach { it.close() }
        network.close()
    }

    private fun kerberosRequired(): Boolean =
        options.kerberos.enabled ||
        options.hdfs.kerberos.enabled ||
            options.hiveMetastore.kerberos.enabled ||
            options.kafka.kerberos.enabled ||
            options.kafka.kafkaUiKerberos.enabled

    private fun kerberos(): BigDataServiceContainer {
        val kerberos = options.kerberos
        val servicePrincipals = buildList {
            addUserKeytab(kerberos.clientPrincipal, "/kerby/keytabs/client.keytab")
            kerberos.users.forEach { addUserKeytab(it.principal, it.keytabPath) }
            addIfEnabled(options.hdfs.kerberos)
            addIfEnabled(options.hiveMetastore.kerberos)
            addIfEnabled(options.kafka.kerberos)
            addIfEnabled(options.kafka.kafkaUiKerberos)
            if (options.kafka.kerberos.enabled && options.kafka.kafkaUiEnabled) addPrincipal(options.kafka.kafkaUiKerberos)
        }
        val container = GenericBigDataContainer(kerberos.image)
            .withNetwork(network)
            .withNetworkAliases("kerby-kdc")
            .withServicePort(88, options.portBindings.hostPort(88, options.portBindings.kerberosKdc))
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
        val users = kerberos.users.joinToString(",") { "${it.principal}:${it.password}" }
        if (users.isNotEmpty()) {
            container.withEnv("KERBY_EXTRA_PRINCIPALS", users)
        }

        return BigDataServiceContainer(
            service = BigDataService.KERBEROS,
            container = attachLogs("kerberos", container),
            afterStart = { copyKerberosMaterialFromContainer(container) },
        ) {
            val localKrb5Conf = writeLocalKerberosConf(kerberos, container.host, container.getMappedPort(88))
            BigDataEndpoint(
                service = BigDataService.KERBEROS,
                host = container.host,
                ports = mapOf("kdc" to container.getMappedPort(88)),
                properties = mapOf(
                    "bigdata.test.kerberos.realm" to kerberos.realm,
                    "bigdata.test.kerberos.kdc" to "${container.host}:${container.getMappedPort(88)}",
                    "bigdata.test.kerberos.krb5-conf" to localKrb5Conf,
                    "bigdata.test.kerberos.krb5-conf.container" to "/kerby/client/krb5.conf",
                    "bigdata.test.kerberos.client-principal" to kerberos.clientPrincipal,
                    "bigdata.test.kerberos.client-password" to kerberos.clientPassword,
                    "bigdata.test.kerberos.client-keytab" to localKerberosPath("/kerby/keytabs/client.keytab"),
                    "bigdata.test.kerberos.client-keytab.container" to "/kerby/keytabs/client.keytab",
                ),
            )
        }
    }

    private fun hdfs(): BigDataServiceContainer {
        val hdfs = options.hdfs
        val container = GenericBigDataContainer(hdfs.image)
            .withNetwork(network)
            .withNetworkAliases("hdfs", "hdfs.example.com")
            .withServicePort(
                hdfs.nameNodePort,
                options.portBindings.hostPort(hdfs.nameNodePort, options.portBindings.hdfsNameNode),
            )
            .withServicePort(hdfs.webPort, options.portBindings.hostPort(hdfs.webPort, options.portBindings.hdfsWeb))
            .withCommand("sh", "-lc", hdfsStartupCommand(hdfs.nameNodePort, hdfs.webPort))
            .waitingFor(Wait.forHttp("/").forPort(hdfs.webPort).withStartupTimeout(Duration.ofMinutes(3)))
        if (hdfs.kerberos.enabled) {
            mountKerberos(container)
            container
                .withEnv("KRB5_CONFIG", "/kerby/client/krb5.conf")
                .withEnv("HADOOP_OPTS", "-Djava.security.krb5.conf=/kerby/client/krb5.conf")
                .withEnv("HADOOP_SECURITY_AUTHENTICATION", "kerberos")
                .withEnv("HDFS_NAMENODE_KERBEROS_PRINCIPAL", hdfs.kerberos.servicePrincipal)
                .withEnv("HDFS_NAMENODE_KEYTAB_FILE", hdfs.kerberos.keytabPath)
        }

        return BigDataServiceContainer(BigDataService.HDFS, attachLogs("hdfs", container)) {
            val nameNode = "${container.host}:${container.getMappedPort(hdfs.nameNodePort)}"
            val webTls = httpTlsEndpoint(
                name = "hdfs-web",
                tls = hdfs.webTls,
                backendHost = "hdfs",
                backendPort = hdfs.webPort,
                hostPort = tlsHostPort(options.portBindings.hdfsWebTls),
            )
            BigDataEndpoint(
                service = BigDataService.HDFS,
                host = container.host,
                ports = mapOf(
                    "namenode" to container.getMappedPort(hdfs.nameNodePort),
                    "web" to container.getMappedPort(hdfs.webPort),
                ) + webTls.port("web-tls"),
                properties = mapOf(
                    "fs.defaultFS" to "hdfs://$nameNode",
                    "spring.hadoop.fs-uri" to "hdfs://$nameNode",
                ) + webTls.property("dfs.namenode.https-address") + webTls.jvmProperties() + kerberosProperties("hadoop", hdfs.kerberos),
            )
        }
    }

    private fun hiveMetastore(): BigDataServiceContainer {
        val hive = options.hiveMetastore
        if (hive.distribution == HiveMetastoreDistribution.CLOUDERA) {
            return clouderaHms()
        }
        val postgres = PostgreSQLContainer(DockerImageName.parse(hive.databaseImage))
            .withNetwork(network)
            .withNetworkAliases("hive-metastore-postgres")
            .withDatabaseName(hive.databaseName)
            .withUsername(hive.databaseUser)
            .withPassword(hive.databasePassword)
        supportContainers += attachLogs("hive-metastore-postgres", postgres)
        postgres.start()

        val container = FixedPortHiveMetastoreContainer(hive.image)
        container
            .withNetwork(network)
            .withNetworkAliases("hive-metastore", "hive-metastore.example.com")
            .withEnv("SERVICE_NAME", "metastore")
            .withPostgres("hive-metastore-postgres", 5432, hive.databaseName, hive.databaseUser, hive.databasePassword)
            .withWarehousePath(hive.warehouseDir)
        container.withServicePort(9083, options.portBindings.hostPort(9083, options.portBindings.hiveMetastore))
        container.waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(5)))
        if (hive.kerberos.enabled) {
            mountKerberos(container)
            container
                .withEnv("KRB5_CONFIG", "/kerby/client/krb5.conf")
                .withEnv("SERVICE_OPTS", "-Djava.security.krb5.conf=/kerby/client/krb5.conf")
        }
        val tlsProperties = if (hive.tls.enabled) configureHiveMetastoreTls(container, hive.tls) else emptyMap()
        configureHiveDockerObjectStores(container)
        if (hive.extraConfiguration.isNotEmpty() || hive.kerberos.enabled || hive.tls.enabled) {
            container.withEnv("HIVE_CUSTOM_CONF_DIR", "/bigdata-test/hive-conf")
            openSourceHiveConfigurationFiles().forEach { (fileName, content) ->
                container.withCopyToContainer(Transferable.of(content), "/bigdata-test/hive-conf/$fileName")
            }
        }

        return BigDataServiceContainer(BigDataService.HIVE_METASTORE, attachLogs("hive-metastore", container)) {
            val thriftUri = container.thriftUri
            BigDataEndpoint(
                service = BigDataService.HIVE_METASTORE,
                host = container.host,
                ports = mapOf("thrift" to container.getMappedPort(9083)),
                properties = mapOf(
                    "hive.metastore.uris" to thriftUri,
                    "spring.bigdata.test.hive-metastore.thrift-uri" to thriftUri,
                ) + tlsProperties + kerberosProperties("hive.metastore", hive.kerberos),
            )
        }
    }

    private fun clouderaHms(): BigDataServiceContainer {
        val hive = options.hiveMetastore
        val container = GenericBigDataContainer(hive.image)
            .withNetwork(network)
            .withNetworkAliases("hive-metastore", "hive-metastore.example.com")
            .withServicePort(9083, options.portBindings.hostPort(9083, options.portBindings.hiveMetastore))
            .withEnv("HMS_WAREHOUSE_DIR", hive.warehouseDir)
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
        val tlsProperties = if (hive.tls.enabled) configureHiveMetastoreTls(container, hive.tls) else emptyMap()

        hiveMetastoreObjectStoreConfiguration().forEach { (key, value) ->
            container.withEnv("HMS_CONF_${encodeConfigKey(key)}", value)
        }
        hive.extraConfiguration.forEach { (key, value) ->
            container.withEnv("HMS_CONF_${encodeConfigKey(key)}", value)
        }

        return BigDataServiceContainer(BigDataService.HIVE_METASTORE, attachLogs("hive-metastore", container)) {
            val thriftUri = "thrift://${container.host}:${container.getMappedPort(9083)}"
            BigDataEndpoint(
                service = BigDataService.HIVE_METASTORE,
                host = container.host,
                ports = mapOf("thrift" to container.getMappedPort(9083)),
                properties = mapOf(
                    "hive.metastore.uris" to thriftUri,
                    "spring.bigdata.test.hive-metastore.thrift-uri" to thriftUri,
                ) + tlsProperties + kerberosProperties("hive.metastore", hive.kerberos),
            )
        }
    }

    private fun kafka(): BigDataServiceContainer {
        val kafka = options.kafka
        if (!kafka.kerberos.enabled) {
            return if (kafka.tls.enabled) tlsKafka(kafka) else plaintextKafka(kafka)
        }

        val kafkaHostPort = options.portBindings.hostPort(9092, options.portBindings.kafka)
        val externalProtocol = if (kafka.tls.enabled) "SASL_SSL" else "SASL_PLAINTEXT"
        val advertisedListener = if (kafkaHostPort > 0) {
            "$externalProtocol://localhost:$kafkaHostPort"
        } else {
            "$externalProtocol://broker1.example.com:9092"
        }
        val container = GenericBigDataContainer(kafka.image)
            .withNetwork(network)
            .withNetworkAliases("kafka", "broker1.example.com")
            .withServicePort(9092, kafkaHostPort)
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
        mountKerberos(container)
        container
            .withEnv(
                "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP",
                "CONTROLLER:PLAINTEXT,$externalProtocol:$externalProtocol,PLAINTEXT:PLAINTEXT",
            )
            .withEnv("KAFKA_ADVERTISED_LISTENERS", "$advertisedListener,PLAINTEXT://kafka:19092")
            .withEnv("KAFKA_LISTENERS", "$externalProtocol://0.0.0.0:9092,PLAINTEXT://0.0.0.0:19092,CONTROLLER://kafka:29093")
            .withEnv("KAFKA_CONTROLLER_QUORUM_VOTERS", "1@kafka:29093")
            .withEnv("KAFKA_INTER_BROKER_LISTENER_NAME", externalProtocol)
            .withEnv("KAFKA_SASL_ENABLED_MECHANISMS", "GSSAPI")
            .withEnv("KAFKA_SASL_MECHANISM_INTER_BROKER_PROTOCOL", "GSSAPI")
            .withEnv("KAFKA_SASL_KERBEROS_SERVICE_NAME", kafka.kerberos.servicePrincipal.substringBefore("/"))
            .withEnv("KAFKA_OPTS", "-Djava.security.krb5.conf=/kerby/client/krb5.conf -Djava.security.auth.login.config=/etc/kafka/kerberos/kafka_server_jaas.conf")
            .withEnv("KRB5_CONFIG", "/kerby/client/krb5.conf")
            .withCopyToContainer(
                Transferable.of(kafkaJaas(kafka.kerberos)),
                "/etc/kafka/kerberos/kafka_server_jaas.conf",
            )
        val sslProperties = if (kafka.tls.enabled) configureKafkaBrokerTls(container, kafka, "SASL_SSL") else emptyMap()

        return BigDataServiceContainer(BigDataService.KAFKA, attachLogs("kafka", container)) {
            val bootstrapServers = "${container.host}:${container.getMappedPort(9092)}"
            BigDataEndpoint(
                service = BigDataService.KAFKA,
                host = container.host,
                ports = mapOf("bootstrap" to container.getMappedPort(9092)),
                properties = mapOf(
                    "bootstrap.servers" to bootstrapServers,
                    "spring.kafka.bootstrap-servers" to bootstrapServers,
                ) + kerberosProperties("kafka", kafka.kerberos) +
                    kafkaClientKerberosProperties(kafka.kerberos, options.kerberos, kafka.tls.enabled) +
                    sslProperties,
            )
        }
    }

    private fun tlsKafka(kafka: KafkaOptions): BigDataServiceContainer {
        val kafkaHostPort = options.portBindings.hostPort(9092, options.portBindings.kafka)
        val container = if (kafkaHostPort == 0) {
            KafkaContainer(DockerImageName.parse(kafka.image))
        } else {
            FixedPortKafkaContainer(DockerImageName.parse(kafka.image)).withServicePort(9092, kafkaHostPort)
        }
        container
            .withNetwork(network)
            .withNetworkAliases("kafka")
            .withStartupTimeout(Duration.ofMinutes(3))
            .withEnv("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", "BROKER:SSL,PLAINTEXT:SSL,CONTROLLER:PLAINTEXT")
            .withEnv("KAFKA_INTER_BROKER_LISTENER_NAME", "BROKER")
            .withEnv("KAFKA_SSL_CLIENT_AUTH", "none")
            .withEnv("KAFKA_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM", "")
        val sslProperties = configureKafkaBrokerTls(container, kafka, "SSL")

        return BigDataServiceContainer(BigDataService.KAFKA, attachLogs("kafka", container)) {
            val bootstrapServers = container.bootstrapServers
            BigDataEndpoint(
                service = BigDataService.KAFKA,
                host = container.host,
                ports = mapOf("bootstrap" to container.getMappedPort(9092)),
                properties = mapOf(
                    "bootstrap.servers" to bootstrapServers,
                    "spring.kafka.bootstrap-servers" to bootstrapServers,
                    "bootstrap.servers.internal" to "kafka:9093",
                ) + sslProperties,
            )
        }
    }

    private fun plaintextKafka(kafka: KafkaOptions): BigDataServiceContainer {
        val kafkaHostPort = options.portBindings.hostPort(9092, options.portBindings.kafka)
        val container = if (kafkaHostPort == 0) {
            KafkaContainer(DockerImageName.parse(kafka.image))
        } else {
            FixedPortKafkaContainer(DockerImageName.parse(kafka.image)).withServicePort(9092, kafkaHostPort)
        }
        container
            .withNetwork(network)
            .withNetworkAliases("kafka")
            .withListener("kafka:19092")
            .withStartupTimeout(Duration.ofMinutes(3))

        return BigDataServiceContainer(BigDataService.KAFKA, attachLogs("kafka", container)) {
            val bootstrapServers = container.bootstrapServers
            BigDataEndpoint(
                service = BigDataService.KAFKA,
                host = container.host,
                ports = mapOf("bootstrap" to container.getMappedPort(9092)),
                properties = mapOf(
                    "bootstrap.servers" to bootstrapServers,
                    "spring.kafka.bootstrap-servers" to bootstrapServers,
                    "bootstrap.servers.internal" to "kafka:19092",
                ),
            )
        }
    }

    private fun schemaRegistry(): BigDataServiceContainer {
        val kafka = options.kafka
        val container = GenericBigDataContainer(kafka.schemaRegistryImage)
            .withNetwork(network)
            .withNetworkAliases("schema-registry")
            .withServicePort(8085, options.portBindings.hostPort(8085, options.portBindings.schemaRegistry))
            .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
            .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8085")
            .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://kafka:19092")
            .waitingFor(Wait.forHttp("/subjects").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(3)))
        if (kafka.tls.enabled && !kafka.kerberos.enabled) {
            container
                .withCopyFileToContainer(
                    MountableFile.forHostPath(tlsMaterial.trustStorePath),
                    "/etc/schema-registry/tls/truststore.p12",
                )
                .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "SSL://kafka:9093")
                .withEnv("SCHEMA_REGISTRY_KAFKASTORE_SECURITY_PROTOCOL", "SSL")
                .withEnv("SCHEMA_REGISTRY_KAFKASTORE_SSL_TRUSTSTORE_LOCATION", "/etc/schema-registry/tls/truststore.p12")
                .withEnv("SCHEMA_REGISTRY_KAFKASTORE_SSL_TRUSTSTORE_PASSWORD", tlsMaterial.trustStorePassword)
                .withEnv("SCHEMA_REGISTRY_KAFKASTORE_SSL_TRUSTSTORE_TYPE", "PKCS12")
                .withEnv("SCHEMA_REGISTRY_KAFKASTORE_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM", "")
        }
        return BigDataServiceContainer(BigDataService.SCHEMA_REGISTRY, attachLogs("schema-registry", container)) {
            val tlsEndpoint = httpTlsEndpoint(
                name = "schema-registry",
                tls = kafka.schemaRegistryTls,
                backendHost = "schema-registry",
                backendPort = 8085,
                hostPort = tlsHostPort(options.portBindings.schemaRegistryTls),
            )
            val url = tlsEndpoint.url ?: "http://${container.host}:${container.getMappedPort(8085)}"
            BigDataEndpoint(
                service = BigDataService.SCHEMA_REGISTRY,
                host = tlsEndpoint.host ?: container.host,
                ports = mapOf("http" to container.getMappedPort(8085)) + tlsEndpoint.port("https"),
                properties = mapOf("schema.registry.url" to url) + tlsEndpoint.jvmProperties(),
            )
        }
    }

    private fun kafkaUi(): BigDataServiceContainer {
        val kafka = options.kafka
        val container = GenericBigDataContainer(kafka.kafkaUiImage)
            .withNetwork(network)
            .withNetworkAliases("kafka-ui")
            .withServicePort(8080, options.portBindings.hostPort(8080, options.portBindings.kafkaUi))
            .withEnv("KAFKA_CLUSTERS_0_NAME", "local")
            .withEnv("KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS", "kafka:9092")
            .withEnv("DYNAMIC_CONFIG_ENABLED", "false")
            .waitingFor(Wait.forHttp("/").withStartupTimeout(Duration.ofMinutes(3)))
        if (kafka.kafkaUiKerberos.enabled || kafka.kerberos.enabled) {
            mountKerberos(container)
            container
                .withEnv("JAVA_OPTS", "-Djava.security.krb5.conf=/kerby/client/krb5.conf")
                .withEnv("KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS", "broker1.example.com:9092")
                .withEnv("KAFKA_CLUSTERS_0_PROPERTIES_SECURITY_PROTOCOL", if (kafka.tls.enabled) "SASL_SSL" else "SASL_PLAINTEXT")
                .withEnv("KAFKA_CLUSTERS_0_PROPERTIES_SASL_MECHANISM", "GSSAPI")
                .withEnv("KAFKA_CLUSTERS_0_PROPERTIES_SASL_KERBEROS_SERVICE_NAME", "kafka")
                .withEnv("KAFKA_CLUSTERS_0_PROPERTIES_SASL_JAAS_CONFIG", inlineJaas(kafka.kafkaUiKerberos))
        }
        if (kafka.tls.enabled) {
            val trustStore = tlsMaterial.trustStorePath
            container
                .withCopyFileToContainer(MountableFile.forHostPath(trustStore), "/etc/kafka/tls/truststore.p12")
                .withEnv("KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS", if (kafka.kerberos.enabled) "broker1.example.com:9092" else "kafka:9093")
                .withEnv("KAFKA_CLUSTERS_0_PROPERTIES_SECURITY_PROTOCOL", if (kafka.kerberos.enabled) "SASL_SSL" else "SSL")
                .withEnv("KAFKA_CLUSTERS_0_PROPERTIES_SSL_TRUSTSTORE_LOCATION", "/etc/kafka/tls/truststore.p12")
                .withEnv("KAFKA_CLUSTERS_0_PROPERTIES_SSL_TRUSTSTORE_PASSWORD", tlsMaterial.trustStorePassword)
                .withEnv("KAFKA_CLUSTERS_0_PROPERTIES_SSL_TRUSTSTORE_TYPE", "PKCS12")
                .withEnv("KAFKA_CLUSTERS_0_PROPERTIES_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM", "")
        }

        return BigDataServiceContainer(BigDataService.KAFKA_UI, attachLogs("kafka-ui", container)) {
            val tlsEndpoint = httpTlsEndpoint(
                name = "kafka-ui",
                tls = kafka.kafkaUiTls,
                backendHost = "kafka-ui",
                backendPort = 8080,
                hostPort = tlsHostPort(options.portBindings.kafkaUiTls),
            )
            val url = tlsEndpoint.url ?: "http://${container.host}:${container.getMappedPort(8080)}"
            BigDataEndpoint(
                service = BigDataService.KAFKA_UI,
                host = tlsEndpoint.host ?: container.host,
                ports = mapOf("http" to container.getMappedPort(8080)) + tlsEndpoint.port("https"),
                properties = mapOf("bigdata.test.kafka-ui.url" to url) + tlsEndpoint.jvmProperties(),
            )
        }
    }

    private fun localStackS3(): BigDataServiceContainer {
        val objectStore = options.localStackS3
        val container = GenericBigDataContainer(objectStore.image)
            .withNetwork(network)
            .withNetworkAliases("localstack")
            .withServicePort(4566, options.portBindings.hostPort(4566, options.portBindings.localStackS3))
            .withEnv("SERVICES", "s3")
            .waitingFor(Wait.forHttp("/_localstack/health").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(3)))

        return BigDataServiceContainer(BigDataService.LOCALSTACK_S3, attachLogs("localstack-s3", container)) {
            val tlsEndpoint = httpTlsEndpoint(
                name = "localstack-s3",
                tls = objectStore.tls,
                backendHost = "localstack",
                backendPort = 4566,
                hostPort = tlsHostPort(options.portBindings.localStackS3Tls),
            )
            val endpoint = tlsEndpoint.url ?: "http://${container.host}:${container.getMappedPort(4566)}"
            BigDataEndpoint(
                service = BigDataService.LOCALSTACK_S3,
                host = tlsEndpoint.host ?: container.host,
                ports = mapOf("edge" to container.getMappedPort(4566)) + tlsEndpoint.port("https"),
                properties = mapOf(
                    "spring.cloud.aws.s3.endpoint" to endpoint,
                    "aws.endpoint-url.s3" to endpoint,
                    "aws.accessKeyId" to "test",
                    "aws.secretAccessKey" to "test",
                    "aws.region" to "us-east-1",
                ) + tlsEndpoint.jvmProperties(),
            )
        }
    }

    private fun fakeGcs(): BigDataServiceContainer {
        val objectStore = options.fakeGcs
        val container = GenericBigDataContainer(objectStore.image)
            .withNetwork(network)
            .withNetworkAliases("fake-gcs")
            .withServicePort(4443, options.portBindings.hostPort(4443, options.portBindings.fakeGcs))
            .withCommand("-scheme", "http", "-port", "4443")
            .waitingFor(Wait.forHttp("/storage/v1/b").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(2)))

        return BigDataServiceContainer(BigDataService.FAKE_GCS, attachLogs("fake-gcs", container)) {
            val tlsEndpoint = httpTlsEndpoint(
                name = "fake-gcs",
                tls = objectStore.tls,
                backendHost = "fake-gcs",
                backendPort = 4443,
                hostPort = tlsHostPort(options.portBindings.fakeGcsTls),
            )
            val endpoint = tlsEndpoint.url ?: "http://${container.host}:${container.getMappedPort(4443)}"
            BigDataEndpoint(
                service = BigDataService.FAKE_GCS,
                host = tlsEndpoint.host ?: container.host,
                ports = mapOf("http" to container.getMappedPort(4443)) + tlsEndpoint.port("https"),
                properties = mapOf(
                    "bigdata.test.gcs.endpoint" to endpoint,
                    "google.cloud.storage.host" to endpoint,
                ) + tlsEndpoint.jvmProperties(),
            )
        }
    }


    private fun hiveMetastoreObjectStoreConfiguration(): Map<String, String> =
        buildMap {
            if (options.localStackS3.enabled) {
                put("fs.s3a.endpoint", "http://localstack:4566")
                put("fs.s3a.endpoint.region", "us-east-1")
                put("fs.s3a.aws.credentials.provider", "org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider")
                put("fs.s3a.access.key", "test")
                put("fs.s3a.secret.key", "test")
                put("fs.s3a.path.style.access", "true")
                put("fs.s3a.connection.ssl.enabled", "false")
                put("fs.s3a.change.detection.mode", "none")
            }
            if (options.fakeGcs.enabled) {
                put("fs.gs.impl", "com.google.cloud.hadoop.fs.gcs.GoogleHadoopFileSystem")
                put("fs.AbstractFileSystem.gs.impl", "com.google.cloud.hadoop.fs.gcs.GoogleHadoopFS")
                put("fs.gs.project.id", "bigdata-test")
                put("fs.gs.storage.root.url", "http://fake-gcs:4443/")
                put("fs.gs.storage.service.path", "storage/v1/")
                put("fs.gs.client.type", "HTTP_API_CLIENT")
                put("fs.gs.auth.type", "UNAUTHENTICATED")
                put("fs.gs.status.parallel.enable", "false")
                put("fs.gs.create.items.conflict.check.enable", "false")
                put("fs.gs.implicit.dir.repair.enable", "false")
                put("fs.gs.hierarchical.namespace.folders.enable", "false")
            }
        }

    private fun configureHiveDockerObjectStores(container: HiveMetastoreContainer) {
        if (options.localStackS3.enabled) {
            container
                .withEnv("S3A_FILE_SYSTEM_IMPL", "org.apache.hadoop.fs.s3a.S3AFileSystem")
                .withEnv("S3_ENDPOINT_URL", "http://localstack:4566")
                .withEnv("AWS_ACCESS_KEY_ID", "test")
                .withEnv("AWS_SECRET_ACCESS_KEY", "test")
                .withEnv("S3_PATH_STYLE_ACCESS", "true")
                .withEnv("S3_SSL_ENABLED", "false")
        }
        if (options.fakeGcs.enabled) {
            container
                .withEnv("GCS_FILE_SYSTEM_IMPL", "com.google.cloud.hadoop.fs.gcs.GoogleHadoopFileSystem")
                .withEnv("GCS_ABSTRACT_FILE_SYSTEM_IMPL", "com.google.cloud.hadoop.fs.gcs.GoogleHadoopFS")
                .withEnv("GCS_AUTH_TYPE", "UNAUTHENTICATED")
                .withEnv("GCS_PROJECT_ID", "bigdata-test")
                .withEnv("GCS_STORAGE_ROOT_URL", "http://fake-gcs:4443")
                .withEnv("GCS_STORAGE_SERVICE_PATH", "/storage/v1/")
                .withEnv("GCS_CREATE_ITEMS_CONFLICT_CHECK_ENABLED", "false")
                .withEnv("GCS_CLIENT_UPLOAD_TYPE", "WRITE_TO_DISK_THEN_UPLOAD")
                .withEnv("GCS_DIRECT_UPLOAD_ENABLED", "false")
                .withEnv("GCS_PERFORMANCE_CACHE_ENABLED", "false")
                .withEnv("GCS_STORAGE_CLIENT_CACHE_ENABLED", "false")
                .withEnv("GCS_GRPC_ENABLED", "false")
        }
    }

    private fun openSourceHiveConfigurationFiles(): Map<String, String> {
        val hive = options.hiveMetastore
        val metastoreProperties = linkedMapOf(
            "hive.metastore.warehouse.dir" to hive.warehouseDir,
            "javax.jdo.option.ConnectionURL" to "jdbc:postgresql://hive-metastore-postgres:5432/${hive.databaseName}",
            "javax.jdo.option.ConnectionDriverName" to "org.postgresql.Driver",
            "javax.jdo.option.ConnectionUserName" to hive.databaseUser,
            "javax.jdo.option.ConnectionPassword" to hive.databasePassword,
        )
        if (hive.kerberos.enabled) {
            metastoreProperties += mapOf(
                "hive.metastore.sasl.enabled" to "true",
                "hive.metastore.kerberos.principal" to hive.kerberos.servicePrincipal,
                "hive.metastore.kerberos.keytab.file" to hive.kerberos.keytabPath,
                "hadoop.security.authentication" to "kerberos",
            )
        }
        if (hive.tls.enabled) {
            metastoreProperties += hiveMetastoreServerTlsProperties("/bigdata-test/tls/hive-metastore.p12")
        }
        metastoreProperties += hive.extraConfiguration
        val hadoopProperties = hiveMetastoreObjectStoreConfiguration()

        return mapOf(
            "hive-site.xml" to configurationXml(metastoreProperties + hadoopProperties),
            "metastore-site.xml" to configurationXml(metastoreProperties + hadoopProperties),
            "core-site.xml" to configurationXml(hadoopProperties),
        )
    }

    private fun writeConfigurationXml(path: Path, properties: Map<String, String>) {
        Files.writeString(path, configurationXml(properties), StandardCharsets.UTF_8)
    }

    private fun configurationXml(properties: Map<String, String>): String =
        buildString {
            appendLine("<configuration>")
            properties.forEach { (key, value) ->
                appendLine("  <property>")
                appendLine("    <name>${xmlEscape(key)}</name>")
                appendLine("    <value>${xmlEscape(value)}</value>")
                appendLine("  </property>")
            }
            appendLine("</configuration>")
        }

    private fun httpTlsEndpoint(
        name: String,
        tls: HttpTlsOptions,
        backendHost: String,
        backendPort: Int,
        hostPort: Int,
    ): HttpTlsEndpoint {
        if (!tls.enabled) return HttpTlsEndpoint()
        val dir = Files.createTempDirectory("bigdata-test-haproxy-$name-")
        val pem = tlsMaterial.haproxyPem(name, tls.domain)
        val config = dir.resolve("haproxy.cfg")
        Files.writeString(
            config,
            """
            global
              log stdout format raw local0

            defaults
              mode http
              log global
              option httplog
              timeout connect 10s
              timeout client 60s
              timeout server 60s

            frontend https-in
              bind *:443 ssl crt /usr/local/etc/haproxy/certs/service.pem
              http-request set-header Host $backendHost:$backendPort
              default_backend app

            backend app
              server app $backendHost:$backendPort check
            """.trimIndent() + "\n",
            StandardCharsets.UTF_8,
        )
        val container = GenericBigDataContainer(options.tls.haproxyImage)
            .withNetwork(network)
            .withNetworkAliases("$name-tls")
            .withServicePort(443, hostPort)
            .withCopyFileToContainer(MountableFile.forHostPath(config), "/usr/local/etc/haproxy/haproxy.cfg")
            .withCopyFileToContainer(MountableFile.forHostPath(pem), "/usr/local/etc/haproxy/certs/service.pem")
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2)))
        val proxied = attachLogs("$name-tls", container)
        proxied.start()
        supportContainers += proxied
        val mappedPort = proxied.getMappedPort(443)
        return HttpTlsEndpoint(
            host = tls.domain,
            port = mappedPort,
            url = "https://${tls.domain}:$mappedPort",
            properties = tlsMaterial.properties(),
        )
    }

    private fun tlsHostPort(configuredHostPort: Int): Int {
        require(configuredHostPort >= 0) { "TLS host port must be 0 for random binding or a positive fixed port" }
        return configuredHostPort
    }

    private fun configureKafkaBrokerTls(
        container: GenericContainer<*>,
        kafka: KafkaOptions,
        securityProtocol: String,
    ): Map<String, String> {
        val keyStore = tlsMaterial.keyStore(
            name = "kafka",
            domain = kafka.tls.domain,
            sanDomains = listOf("kafka", "broker1.example.com"),
        )
        container
            .withCopyFileToContainer(MountableFile.forHostPath(keyStore.path), "/etc/kafka/secrets/kafka.keystore.p12")
            .withCopyFileToContainer(
                MountableFile.forHostPath(tlsMaterial.trustStorePath),
                "/etc/kafka/secrets/kafka.truststore.p12",
            )
            .withCopyToContainer(Transferable.of(keyStore.password), "/etc/kafka/secrets/kafka.key.credentials")
            .withCopyToContainer(Transferable.of(keyStore.password), "/etc/kafka/secrets/kafka.keystore.credentials")
            .withCopyToContainer(Transferable.of(tlsMaterial.trustStorePassword), "/etc/kafka/secrets/kafka.truststore.credentials")
            .withEnv("KAFKA_SSL_KEYSTORE_FILENAME", "kafka.keystore.p12")
            .withEnv("KAFKA_SSL_KEY_CREDENTIALS", "kafka.key.credentials")
            .withEnv("KAFKA_SSL_KEYSTORE_CREDENTIALS", "kafka.keystore.credentials")
            .withEnv("KAFKA_SSL_TRUSTSTORE_FILENAME", "kafka.truststore.p12")
            .withEnv("KAFKA_SSL_TRUSTSTORE_CREDENTIALS", "kafka.truststore.credentials")
            .withEnv("KAFKA_SSL_KEYSTORE_LOCATION", "/etc/kafka/secrets/kafka.keystore.p12")
            .withEnv("KAFKA_SSL_KEYSTORE_PASSWORD", keyStore.password)
            .withEnv("KAFKA_SSL_KEYSTORE_TYPE", keyStore.type)
            .withEnv("KAFKA_SSL_KEY_PASSWORD", keyStore.password)
            .withEnv("KAFKA_SSL_TRUSTSTORE_LOCATION", "/etc/kafka/secrets/kafka.truststore.p12")
            .withEnv("KAFKA_SSL_TRUSTSTORE_PASSWORD", tlsMaterial.trustStorePassword)
            .withEnv("KAFKA_SSL_TRUSTSTORE_TYPE", "PKCS12")

        return mapOf(
            "security.protocol" to securityProtocol,
            "ssl.truststore.location" to tlsMaterial.trustStorePath.toString(),
            "ssl.truststore.password" to tlsMaterial.trustStorePassword,
            "ssl.truststore.type" to "PKCS12",
        ) + tlsMaterial.properties()
    }

    private fun configureHiveMetastoreTls(
        container: GenericContainer<*>,
        tls: HttpTlsOptions,
    ): Map<String, String> {
        val keyStore = tlsMaterial.keyStore(
            name = "hive-metastore",
            domain = tls.domain,
            sanDomains = listOf("hive-metastore", "hive-metastore.example.com"),
        )
        val keyStorePath = "/bigdata-test/tls/hive-metastore.p12"
        container.withCopyToContainer(Transferable.of(Files.readAllBytes(keyStore.path)), keyStorePath)
        if (options.hiveMetastore.distribution == HiveMetastoreDistribution.CLOUDERA) {
            container.withEnv(
                "HMS_EXTRA_CONF",
                hiveMetastoreServerTlsProperties(keyStorePath)
                    .map { (key, value) -> "$key=$value" }
                    .joinToString("\n"),
            )
        }
        return mapOf(
            "hive.metastore.use.SSL" to "true",
            "hive.metastore.truststore.path" to tlsMaterial.trustStorePath.toString(),
            "hive.metastore.truststore.password" to tlsMaterial.trustStorePassword,
        ) + tlsMaterial.properties()
    }

    private fun hiveMetastoreServerTlsProperties(keyStorePath: String): Map<String, String> =
        mapOf(
            "hive.metastore.use.SSL" to "true",
            "hive.metastore.keystore.path" to keyStorePath,
            "hive.metastore.keystore.password" to tlsMaterial.trustStorePassword,
        )

    private data class HttpTlsEndpoint(
        val host: String? = null,
        val port: Int? = null,
        val url: String? = null,
        val properties: Map<String, String> = emptyMap(),
    ) {
        fun port(name: String): Map<String, Int> =
            port?.let { mapOf(name to it) }.orEmpty()

        fun property(name: String): Map<String, String> =
            url?.let { mapOf(name to it) }.orEmpty()

        fun jvmProperties(): Map<String, String> = properties
    }

    private fun xmlEscape(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")


    private fun <T : GenericContainer<*>> attachLogs(name: String, container: T): T {
        when (options.containerLogs.mode) {
            ContainerLogMode.NONE -> Unit
            ContainerLogMode.STDOUT -> container.withLogConsumer { frame -> writeConsoleFrame(name, frame) }
            ContainerLogMode.FILE -> {
                val logDir = Files.createDirectories(Path.of(options.containerLogs.directory))
                val writer = OutputStreamWriter(
                    Files.newOutputStream(
                        logDir.resolve("${sanitizeLogName(name)}.log"),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND,
                    ),
                    StandardCharsets.UTF_8,
                )
                logConsumers += writer
                container.withLogConsumer { frame -> writeFileFrame(writer, frame) }
            }
        }
        return container
    }

    private fun writeConsoleFrame(name: String, frame: OutputFrame) {
        val text = frame.utf8String.removeSuffix("\n")
        if (text.isEmpty()) return
        val stream = if (frame.type == OutputFrame.OutputType.STDERR) System.err else System.out
        text.lineSequence().forEach { line -> stream.println("[$name] $line") }
    }

    private fun writeFileFrame(writer: OutputStreamWriter, frame: OutputFrame) {
        synchronized(writer) {
            writer.write(frame.utf8String)
            writer.flush()
        }
    }

    private fun sanitizeLogName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun hdfsStartupCommand(nameNodePort: Int, webPort: Int): String =
        """
        set -eu
        cat > "${'$'}HADOOP_CONF_DIR/core-site.xml" <<EOF
        <configuration>
          <property><name>fs.defaultFS</name><value>hdfs://hdfs:$nameNodePort</value></property>
        </configuration>
        EOF
        cat > "${'$'}HADOOP_CONF_DIR/hdfs-site.xml" <<EOF
        <configuration>
          <property><name>dfs.replication</name><value>1</value></property>
          <property><name>dfs.permissions.enabled</name><value>false</value></property>
          <property><name>dfs.namenode.name.dir</name><value>file:///tmp/hadoop-name</value></property>
          <property><name>dfs.datanode.data.dir</name><value>file:///tmp/hadoop-data</value></property>
          <property><name>dfs.namenode.rpc-bind-host</name><value>0.0.0.0</value></property>
          <property><name>dfs.namenode.http-address</name><value>0.0.0.0:$webPort</value></property>
          <property><name>dfs.namenode.http-bind-host</name><value>0.0.0.0</value></property>
        </configuration>
        EOF
        hdfs namenode -format -force -nonInteractive
        hdfs namenode &
        hdfs datanode &
        wait
        """.trimIndent()

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

    private fun MutableList<String>.addUserKeytab(principal: String, keytabPath: String) {
        val entry = "$principal:${keytabPath.replace("/kerby/", "/var/lib/kerby/")}"
        if (!contains(entry)) add(entry)
    }

    private fun mountKerberos(container: GenericContainer<*>) {
        writeContainerKerberosConf(options.kerberos)
        copyKerberosFileToContainer(container, "/kerby/client/krb5.conf")
        kerberosContainerPaths().forEach { copyKerberosFileToContainer(container, it) }
    }

    private fun kerberosDirectory(): String =
        kerberosDir?.toString() ?: error("Kerberos directory was not initialized")

    private fun copyKerberosMaterialFromContainer(container: GenericContainer<*>) {
        val paths = kerberosContainerPaths()
        waitForKerberosFiles(container, paths)
        paths.forEach { containerPath ->
            copyKerberosFileFromContainer(
                container = container,
                source = containerPath.replace("/kerby/", "/var/lib/kerby/"),
                destination = containerPath,
            )
        }
    }

    private fun copyKerberosFileFromContainer(
        container: GenericContainer<*>,
        source: String,
        destination: String,
    ) {
        val path = hostKerberosMaterialPath(destination)
        Files.createDirectories(path.parent)
        container.copyFileFromContainer(source, path.toString())
    }

    private fun waitForKerberosFiles(container: GenericContainer<*>, containerPaths: Set<String>) {
        val sources = containerPaths.map { it.replace("/kerby/", "/var/lib/kerby/") }
        val deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos()
        while (true) {
            val missing = sources.filterNot { source ->
                container.execInContainer("sh", "-lc", "test -s '$source'").exitCode == 0
            }
            if (missing.isEmpty()) return
            if (System.nanoTime() >= deadline) {
                error("Kerberos KDC did not generate expected keytab files: ${missing.joinToString(", ")}")
            }
            Thread.sleep(250)
        }
    }

    private fun copyKerberosFileToContainer(container: GenericContainer<*>, containerPath: String) {
        val path = hostKerberosMaterialPath(containerPath)
        Files.createDirectories(path.parent)
        if (Files.notExists(path)) {
            Files.write(path, ByteArray(0))
        }
        container.withCopyFileToContainer(MountableFile.forHostPath(path), containerPath)
    }

    private fun kerberosContainerPaths(): Set<String> =
        buildSet {
            add("/kerby/keytabs/client.keytab")
            options.kerberos.users.forEach { add(it.keytabPath) }
            addIfEnabled(options.hdfs.kerberos)
            addIfEnabled(options.hiveMetastore.kerberos)
            addIfEnabled(options.kafka.kerberos)
            addIfEnabled(options.kafka.kafkaUiKerberos)
            if (options.kafka.kerberos.enabled && options.kafka.kafkaUiEnabled) add(options.kafka.kafkaUiKerberos.keytabPath)
        }

    private fun MutableSet<String>.addIfEnabled(options: KerberosAuthOptions) {
        if (options.enabled) add(options.keytabPath)
    }

    private fun hostKerberosMaterialPath(containerPath: String): Path {
        require(containerPath.startsWith("/kerby/")) { "Kerberos material path must be under /kerby: $containerPath" }
        return Path.of(kerberosDirectory()).resolve(containerPath.removePrefix("/kerby/"))
    }

    private fun kerberosProperties(prefix: String, options: KerberosAuthOptions): Map<String, String> =
        if (options.enabled) {
            mapOf(
                "$prefix.security.authentication" to "kerberos",
                "$prefix.kerberos.principal" to options.servicePrincipal,
                "$prefix.kerberos.service-name" to options.servicePrincipal.substringBefore("/"),
                "$prefix.kerberos.keytab" to options.keytabPath,
                "$prefix.kerberos.keytab.local" to localKerberosPath(options.keytabPath),
                "java.security.krb5.conf" to "/kerby/client/krb5.conf",
                "java.security.krb5.conf.local" to localKerberosPath("/kerby/client/krb5.conf"),
            )
        } else {
            emptyMap()
        }

    private fun kafkaClientKerberosProperties(
        service: KerberosAuthOptions,
        client: KerberosOptions,
        tlsEnabled: Boolean = false,
    ): Map<String, String> =
        if (service.enabled) {
            val clientKeytab = localKerberosPath("/kerby/keytabs/client.keytab")
            mapOf(
                "security.protocol" to if (tlsEnabled) "SASL_SSL" else "SASL_PLAINTEXT",
                "sasl.mechanism" to "GSSAPI",
                "sasl.kerberos.service.name" to service.servicePrincipal.substringBefore("/"),
                "sasl.jaas.config" to inlineJaas(client.clientPrincipal, clientKeytab),
                "sasl.jaas.config.container-keytab" to inlineJaas(client.clientPrincipal, "/kerby/keytabs/client.keytab"),
                "java.security.krb5.conf.local" to localKerberosPath("/kerby/client/krb5.conf"),
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

    private fun inlineJaas(principal: String, keytabPath: String): String =
        """com.sun.security.auth.module.Krb5LoginModule required useKeyTab=true storeKey=true keyTab="$keytabPath" principal="$principal";"""

    private fun localKerberosPath(containerPath: String): String =
        when {
            containerPath == "/kerby/client/krb5.conf" -> Path.of(kerberosDirectory())
                .resolve("krb5-local.conf")
                .toString()
            containerPath.startsWith("/kerby/") -> Path.of(kerberosDirectory())
                .resolve(containerPath.removePrefix("/kerby/"))
                .toString()
            else -> containerPath
        }

    private fun writeLocalKerberosConf(options: KerberosOptions, host: String, port: Int): String {
        val path = Path.of(localKerberosPath("/kerby/client/krb5.conf"))
        Files.createDirectories(path.parent)
        Files.writeString(path, kerberosConf(options, host, port), StandardCharsets.UTF_8)
        return path.toString()
    }

    private fun writeContainerKerberosConf(options: KerberosOptions) {
        val path = Path.of(kerberosDirectory()).resolve("client/krb5.conf")
        Files.createDirectories(path.parent)
        Files.writeString(path, kerberosConf(options, "kerby-kdc", 88), StandardCharsets.UTF_8)
    }

    private fun kerberosConf(options: KerberosOptions, host: String, port: Int): String =
        """
        [libdefaults]
          default_realm = ${options.realm}
          dns_lookup_realm = false
          dns_lookup_kdc = false
          rdns = false
          udp_preference_limit = 1

        [realms]
          ${options.realm} = {
            kdc = $host:$port
            admin_server = $host:$port
          }

        [domain_realm]
          .${options.domain} = ${options.realm}
          ${options.domain} = ${options.realm}
        """.trimIndent()
}
