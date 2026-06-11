package org.openprojectx.bigdata.test.core.container

import org.openprojectx.bigdata.test.core.BigDataEndpoint
import org.openprojectx.bigdata.test.core.BigDataHealthCheckMode
import org.openprojectx.bigdata.test.core.BigDataService
import org.openprojectx.bigdata.test.core.BigDataTestKitOptions
import org.openprojectx.bigdata.test.core.ContainerFileTransferOptions
import org.openprojectx.bigdata.test.core.ContainerLogMode
import org.openprojectx.bigdata.test.core.ContainerPortOptions
import org.openprojectx.bigdata.test.core.HdfsOptions
import org.openprojectx.bigdata.test.core.HttpTlsOptions
import org.openprojectx.bigdata.test.core.HiveMetastoreDistribution
import org.openprojectx.bigdata.test.core.HiveMetastoreOptions
import org.openprojectx.bigdata.test.core.KerberosAuthOptions
import org.openprojectx.bigdata.test.core.KafkaOptions
import org.openprojectx.bigdata.test.core.KerberosOptions
import org.openprojectx.hive.docker.testcontainers.HiveMetastoreContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.Network
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.containers.wait.strategy.WaitAllStrategy
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
    private val kerberosDir: Path? =
        if (kerberosRequired()) {
            options.kerberos.materialDirectory
                ?.let { Files.createDirectories(Path.of(it)) }
                ?: Files.createTempDirectory("bigdata-test-kerberos-")
        } else {
            null
        }
    private val hdfsClientDir: Path by lazy {
        options.hdfs.localHdfsSitePath
            ?.let { Path.of(it).parent }
            ?.let { Files.createDirectories(it) }
            ?: kerberosDir
            ?: Files.createTempDirectory("bigdata-test-hdfs-client-")
    }
    private val hiveMetastoreClientDir: Path by lazy {
        listOf(options.hiveMetastore.localHiveSitePath, options.hiveMetastore.localMetastoreSitePath)
            .firstNotNullOfOrNull { it?.let { path -> Path.of(path).parent } }
            ?.let { Files.createDirectories(it) }
            ?: kerberosDir
            ?: Files.createTempDirectory("bigdata-test-hms-client-")
    }
    private val tlsMaterial: TlsMaterial by lazy { TlsMaterial(options.tls.copy(enabled = true)) }

    fun healthCheck(service: BigDataService, container: GenericContainer<*>, endpoint: BigDataEndpoint) {
        val healthCheck = options.healthChecks[service] ?: return
        when (healthCheck.mode) {
            BigDataHealthCheckMode.NONE,
            BigDataHealthCheckMode.BASIC,
            -> Unit
            BigDataHealthCheckMode.CLI -> runCliHealthCheck(service, container, healthCheck.timeoutSeconds)
        }
    }

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
        validateKerberosTiming(kerberos)
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
            .withEnv("KERBY_KDC_HOST", "127.0.0.1")
            .withEnv("KERBY_KDC_BIND_HOST", "0.0.0.0")
            .withEnv("KERBY_CLIENT_KDC_HOST", "kerby-kdc")
            .withEnv("KERBY_CLIENT_DOMAIN", kerberos.domain)
            .withEnv("JAVA_TOOL_OPTIONS", "-Djava.security.krb5.conf=/opt/kerby/conf/krb5.conf")
            .withEnv("KERBY_PREAUTH_REQUIRED", "false")
            .withEnv("KERBY_PA_ENC_TIMESTAMP_REQUIRED", "false")
            .withEnv("KERBY_CLIENT_PRINCIPAL", kerberos.clientPrincipal)
            .withEnv("KERBY_CLIENT_PASSWORD", kerberos.clientPassword)
            .withEnv("KERBY_KADMIN_ATTEMPTS", kerberos.adminAttempts.toString())
            .withEnv("KERBY_KADMIN_RETRY_DELAY_SECONDS", kerberos.adminRetryDelaySeconds.toString())
            .withEnv("KERBY_DEBUG", kerberos.debug.toString())
            .waitingFor(
                Wait.forLogMessage(".*Kerby KDC container ready\\..*", 1)
                    .withStartupTimeout(Duration.ofSeconds(kerberos.startupTimeoutSeconds.toLong())),
            )
        if (servicePrincipals.isNotEmpty()) {
            container.withEnv("KERBY_EXTRA_SERVICE_PRINCIPALS", servicePrincipals.joinToString(","))
        }
        val users = kerberos.users.joinToString(",") { "${it.principal}:${it.password}" }
        if (users.isNotEmpty()) {
            container.withEnv("KERBY_EXTRA_PRINCIPALS", users)
        }

        return BigDataServiceContainer(
            service = BigDataService.KERBEROS,
            container = attachLogs("kerberos", applyContainerCustomizations(BigDataService.KERBEROS, container)),
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
            .withServicePort(
                hdfs.dataNodePort,
                options.portBindings.hostPort(hdfs.dataNodePort, options.portBindings.hdfsDataNode),
            )
            .withServicePort(hdfs.webPort, options.portBindings.hostPort(hdfs.webPort, options.portBindings.hdfsWeb))
            .withCommand(
                "sh",
                "-lc",
                hdfsStartupCommand(hdfs),
            )
            .waitingFor(
                WaitAllStrategy()
                    .withStrategy(Wait.forListeningPort())
                    .withStrategy(Wait.forHttp("/").forPort(hdfs.webPort))
                    .withStartupTimeout(Duration.ofMinutes(3)),
            )
        if (hdfs.kerberos.enabled) {
            mountKerberos(container)
            container
                .withEnv("KRB5_CONFIG", "/kerby/client/krb5.conf")
                .withEnv("HADOOP_OPTS", "-Djava.security.krb5.conf=/kerby/client/krb5.conf")
                .withEnv("HADOOP_SECURITY_AUTHENTICATION", "kerberos")
                .withEnv("HDFS_NAMENODE_KERBEROS_PRINCIPAL", hdfs.kerberos.servicePrincipal)
                .withEnv("HDFS_NAMENODE_KEYTAB_FILE", hdfs.kerberos.keytabPath)
        }

        return BigDataServiceContainer(BigDataService.HDFS, attachLogs("hdfs", applyContainerCustomizations(BigDataService.HDFS, container))) {
            val nameNode = "${container.host}:${container.getMappedPort(hdfs.nameNodePort)}"
            val webTls = httpTlsEndpoint(
                name = "hdfs-web",
                tls = hdfs.webTls,
                backendHost = "hdfs",
                backendPort = hdfs.webPort,
                hostPort = tlsHostPort(options.portBindings.hdfsWebTls),
            )
            val clientProperties = mapOf(
                "fs.defaultFS" to "hdfs://$nameNode",
                "dfs.client.use.datanode.hostname" to "true",
                "dfs.datanode.hostname" to hdfs.dataNodeHostname,
            ) + hdfsKerberosClientProperties(hdfs.kerberos) +
                webTls.property("dfs.namenode.https-address")
            val localHdfsSite = writeLocalHdfsSiteXml(hdfs, clientProperties)
            BigDataEndpoint(
                service = BigDataService.HDFS,
                host = container.host,
                ports = mapOf(
                    "namenode" to container.getMappedPort(hdfs.nameNodePort),
                    "datanode" to container.getMappedPort(hdfs.dataNodePort),
                    "web" to container.getMappedPort(hdfs.webPort),
                ) + webTls.port("web-tls"),
                properties = mapOf(
                    "spring.hadoop.fs-uri" to "hdfs://$nameNode",
                    "bigdata.test.hdfs.hdfs-site" to localHdfsSite,
                    "bigdata.test.hdfs.hdfs-site.container" to "/opt/hadoop/etc/hadoop/hdfs-site.xml",
                ) + clientProperties +
                    webTls.jvmProperties() +
                    kerberosProperties("hadoop", hdfs.kerberos),
            )
        }
    }

    private fun hiveMetastore(): BigDataServiceContainer {
        val hive = options.hiveMetastore
        if (hive.distribution == HiveMetastoreDistribution.CLOUDERA) {
            return clouderaHms()
        }
        val postgres = PostgreSQLContainer(compatiblePostgresImage(hive.databaseImage))
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
                .withEnv("HMS_CONF_HIVE_METASTORE_SASL_ENABLED", "true")
                .withEnv("HMS_CONF_HIVE_METASTORE_KERBEROS_PRINCIPAL", hive.kerberos.servicePrincipal)
                .withEnv("HMS_CONF_HIVE_METASTORE_CLIENT_KERBEROS_PRINCIPAL", hive.kerberos.servicePrincipal)
                .withEnv("HMS_CONF_HIVE_METASTORE_KERBEROS_KEYTAB_FILE", hive.kerberos.keytabPath)
                .withEnv("HMS_CONF_HADOOP_SECURITY_AUTHENTICATION", "kerberos")
        }
        val tlsProperties = if (hive.tls.enabled) configureHiveMetastoreTls(container, hive.tls) else emptyMap()
        configureHiveDockerObjectStores(container)
        if (
            hive.extraConfiguration.isNotEmpty() ||
            hive.kerberos.enabled ||
            hive.tls.enabled ||
            hiveMetastoreObjectStoreConfiguration().isNotEmpty()
        ) {
            container.withEnv("HIVE_CUSTOM_CONF_DIR", "/bigdata-test/hive-conf")
            container.withEnv("HIVE_ROOT_LOGGER", "console")
            container.withEnv("HIVE_LOG4J2_CONFIGURATION_FILE", "/bigdata-test/hive-conf/hive-log4j2.properties")
            openSourceHiveConfigurationFiles().forEach { (fileName, content) ->
                container.withCopyToContainer(Transferable.of(content), "/bigdata-test/hive-conf/$fileName")
            }
        }

        return BigDataServiceContainer(
            BigDataService.HIVE_METASTORE,
            attachLogs("hive-metastore", applyContainerCustomizations(BigDataService.HIVE_METASTORE, container)),
        ) {
            val thriftUri = container.thriftUri
            val clientProperties = hiveMetastoreClientProperties(thriftUri, tlsProperties)
            val clientXmlPaths = writeLocalHiveMetastoreClientXml(hive, clientProperties)
            BigDataEndpoint(
                service = BigDataService.HIVE_METASTORE,
                host = container.host,
                ports = mapOf("thrift" to container.getMappedPort(9083)),
                properties = mapOf(
                    "hive.metastore.uris" to thriftUri,
                    "spring.bigdata.test.hive-metastore.thrift-uri" to thriftUri,
                    "bigdata.test.hive-metastore.hive-site" to clientXmlPaths.hiveSite,
                    "bigdata.test.hive-metastore.metastore-site" to clientXmlPaths.metastoreSite,
                ) + tlsProperties +
                    clientProperties +
                    kerberosProperties("hive.metastore", hive.kerberos),
            )
        }
    }

    private fun compatiblePostgresImage(image: String): DockerImageName =
        DockerImageName.parse(image).asCompatibleSubstituteFor("postgres")

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
                .withEnv("HMS_CONF_HIVE_METASTORE_CLIENT_KERBEROS_PRINCIPAL", hive.kerberos.servicePrincipal)
                .withEnv("HMS_CONF_HIVE_METASTORE_KERBEROS_KEYTAB_FILE", hive.kerberos.keytabPath)
                .withEnv("HMS_CONF_HADOOP_SECURITY_AUTHENTICATION", "kerberos")
        }
        val tlsProperties = if (hive.tls.enabled) configureHiveMetastoreTls(container, hive.tls) else emptyMap()

        hiveMetastoreHadoopConfiguration().forEach { (key, value) ->
            container.withEnv("HMS_CONF_${encodeConfigKey(key)}", value)
        }
        hive.extraConfiguration.forEach { (key, value) ->
            container.withEnv("HMS_CONF_${encodeConfigKey(key)}", value)
        }

        return BigDataServiceContainer(
            BigDataService.HIVE_METASTORE,
            attachLogs("hive-metastore", applyContainerCustomizations(BigDataService.HIVE_METASTORE, container)),
        ) {
            val thriftUri = "thrift://${container.host}:${container.getMappedPort(9083)}"
            val clientProperties = hiveMetastoreClientProperties(thriftUri, tlsProperties)
            val clientXmlPaths = writeLocalHiveMetastoreClientXml(hive, clientProperties)
            BigDataEndpoint(
                service = BigDataService.HIVE_METASTORE,
                host = container.host,
                ports = mapOf("thrift" to container.getMappedPort(9083)),
                properties = mapOf(
                    "hive.metastore.uris" to thriftUri,
                    "spring.bigdata.test.hive-metastore.thrift-uri" to thriftUri,
                    "bigdata.test.hive-metastore.hive-site" to clientXmlPaths.hiveSite,
                    "bigdata.test.hive-metastore.metastore-site" to clientXmlPaths.metastoreSite,
                ) + tlsProperties +
                    clientProperties +
                    kerberosProperties("hive.metastore", hive.kerberos),
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

        return BigDataServiceContainer(BigDataService.KAFKA, attachLogs("kafka", applyContainerCustomizations(BigDataService.KAFKA, container))) {
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
            KafkaContainer(compatibleKafkaImage(kafka.image))
        } else {
            FixedPortKafkaContainer(compatibleKafkaImage(kafka.image)).withServicePort(9092, kafkaHostPort)
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

        return BigDataServiceContainer(BigDataService.KAFKA, attachLogs("kafka", applyContainerCustomizations(BigDataService.KAFKA, container))) {
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
            KafkaContainer(compatibleKafkaImage(kafka.image))
        } else {
            FixedPortKafkaContainer(compatibleKafkaImage(kafka.image)).withServicePort(9092, kafkaHostPort)
        }
        container
            .withNetwork(network)
            .withNetworkAliases("kafka")
            .withListener("kafka:19092")
            .withStartupTimeout(Duration.ofMinutes(3))

        return BigDataServiceContainer(BigDataService.KAFKA, attachLogs("kafka", applyContainerCustomizations(BigDataService.KAFKA, container))) {
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

    private fun compatibleKafkaImage(image: String): DockerImageName =
        DockerImageName.parse(image).asCompatibleSubstituteFor("apache/kafka")

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
        return BigDataServiceContainer(
            BigDataService.SCHEMA_REGISTRY,
            attachLogs("schema-registry", applyContainerCustomizations(BigDataService.SCHEMA_REGISTRY, container)),
        ) {
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

        return BigDataServiceContainer(
            BigDataService.KAFKA_UI,
            attachLogs("kafka-ui", applyContainerCustomizations(BigDataService.KAFKA_UI, container)),
        ) {
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

        return BigDataServiceContainer(
            BigDataService.LOCALSTACK_S3,
            attachLogs("localstack-s3", applyContainerCustomizations(BigDataService.LOCALSTACK_S3, container)),
        ) {
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

        return BigDataServiceContainer(
            BigDataService.FAKE_GCS,
            attachLogs("fake-gcs", applyContainerCustomizations(BigDataService.FAKE_GCS, container)),
        ) {
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
            if (options.hdfs.enabled) {
                put("fs.defaultFS", "hdfs://hdfs:${options.hdfs.nameNodePort}")
                put("dfs.client.use.datanode.hostname", "true")
                put("dfs.datanode.hostname", options.hdfs.dataNodeHostname)
            }
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
                put("fs.gs.max.requests.per.batch", "1")
                put("fs.gs.operation.move.enable", "false")
                put("fs.gs.copy.with.rewrite.enable", "false")
                put("fs.gs.client.upload.type", "WRITE_TO_DISK_THEN_UPLOAD")
                put("fs.gs.outputstream.direct.upload.enable", "false")
            }
        }

    private fun hiveMetastoreHadoopConfiguration(): Map<String, String> =
        hiveMetastoreObjectStoreConfiguration() +
            hdfsKerberosClientProperties(options.hdfs.kerberos) +
            hiveMetastoreProxyUserProperties(options.hiveMetastore.kerberos)

    private fun hiveMetastoreProxyUserProperties(kerberos: KerberosAuthOptions): Map<String, String> =
        if (kerberos.enabled) {
            val shortName = kerberos.servicePrincipal.substringBefore("/")
            mapOf(
                "hadoop.proxyuser.$shortName.hosts" to "*",
                "hadoop.proxyuser.$shortName.groups" to "*",
            )
        } else {
            emptyMap()
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
        val hadoopProperties = hiveMetastoreHadoopConfiguration()

        return mapOf(
            "hive-site.xml" to configurationXml(metastoreProperties + hadoopProperties),
            "metastore-site.xml" to configurationXml(metastoreProperties + hadoopProperties),
            "core-site.xml" to configurationXml(hadoopProperties),
            "hive-log4j2.properties" to hiveMetastoreConsoleLog4j2Properties(),
            "metastore-log4j2.properties" to hiveMetastoreConsoleLog4j2Properties(),
        )
    }

    private fun hiveMetastoreConsoleLog4j2Properties(): String =
        """
        name = BigDataTestHiveMetastoreLog4j2

        property.hive.log.level = INFO
        property.hive.root.logger = console
        property.hive.perflogger.log.level = INFO

        appenders = console

        appender.console.name = console
        appender.console.type = Console
        appender.console.layout.type = PatternLayout
        appender.console.layout.pattern = %d{ISO8601} %5p [%t] %c{2}: %m%n

        logger.DataNucleus.name = DataNucleus
        logger.DataNucleus.level = ERROR

        logger.Datastore.name = Datastore
        logger.Datastore.level = ERROR

        logger.JPOX.name = JPOX
        logger.JPOX.level = ERROR

        logger.PerfLogger.name = org.apache.hadoop.hive.ql.log.PerfLogger
        logger.PerfLogger.level = ${'$'}{sys:hive.perflogger.log.level}

        rootLogger.level = ${'$'}{sys:hive.log.level}
        rootLogger.appenderRefs = console
        rootLogger.appenderRef.console.ref = ${'$'}{sys:hive.root.logger}
        """.trimIndent()

    private fun writeConfigurationXml(path: Path, properties: Map<String, String>) {
        path.parent?.let { Files.createDirectories(it) }
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

    private fun <T : GenericContainer<*>> applyContainerCustomizations(service: BigDataService, container: T): T {
        val customization = options.containerCustomizations[service] ?: return container
        customization.networkMode?.let { container.withNetworkMode(it) }
        customization.ports.forEach { container.addPort(it) }
        customization.environment.forEach { (name, value) -> container.withEnv(name, value) }
        customization.files.forEach { container.addFile(it) }
        customization.mounts.forEach { mount ->
            container.withFileSystemBind(
                mount.hostPath,
                mount.containerPath,
                if (mount.readOnly) BindMode.READ_ONLY else BindMode.READ_WRITE,
            )
        }
        customization.customizers.forEach { it.customize(container) }
        return container
    }

    private fun runCliHealthCheck(service: BigDataService, container: GenericContainer<*>, timeoutSeconds: Long) {
        val command = cliHealthCheckCommand(service) ?: return
        val result = container.execInContainer("sh", "-lc", withShellTimeout(timeoutSeconds, command))
        if (result.exitCode != 0) {
            error(
                buildString {
                    appendLine("CLI health check failed for $service with exit code ${result.exitCode}")
                    appendLine("Command: $command")
                    if (result.stdout.isNotBlank()) {
                        appendLine("stdout:")
                        appendLine(result.stdout.trimEnd())
                    }
                    if (result.stderr.isNotBlank()) {
                        appendLine("stderr:")
                        appendLine(result.stderr.trimEnd())
                    }
                },
            )
        }
    }

    private fun cliHealthCheckCommand(service: BigDataService): String? =
        when (service) {
            BigDataService.KERBEROS ->
                "test -s /kerby/client/krb5.conf && test -s /kerby/keytabs/client.keytab"
            BigDataService.HDFS ->
                """
                tmp=/tmp/bigdata-test-hdfs-health.txt
                path=/tmp/bigdata-test-health/health.txt
                printf 'ok\n' > "${'$'}tmp"
                hdfs dfs -mkdir -p /tmp/bigdata-test-health
                hdfs dfs -put -f "${'$'}tmp" "${'$'}path"
                test "$(hdfs dfs -cat "${'$'}path")" = "ok"
                hdfs dfs -rm -f "${'$'}path"
                """.trimIndent()
            BigDataService.HIVE_METASTORE ->
                "if command -v nc >/dev/null 2>&1; then nc -z localhost 9083; else bash -c '</dev/tcp/localhost/9083'; fi"
            BigDataService.KAFKA ->
                """
                if command -v kafka-topics.sh >/dev/null 2>&1; then
                  kafka-topics.sh --bootstrap-server localhost:9092 --list >/dev/null
                else
                  /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list >/dev/null
                fi
                """.trimIndent()
            BigDataService.SCHEMA_REGISTRY ->
                httpContainerProbe("http://localhost:8085/subjects")
            BigDataService.KAFKA_UI ->
                httpContainerProbe("http://localhost:8080/")
            BigDataService.LOCALSTACK_S3 ->
                """
                if command -v awslocal >/dev/null 2>&1; then
                  awslocal s3 ls >/dev/null
                elif command -v aws >/dev/null 2>&1; then
                  AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test aws --endpoint-url=http://localhost:4566 s3 ls >/dev/null
                else
                  ${httpContainerProbe("http://localhost:4566/_localstack/health")}
                fi
                """.trimIndent()
            BigDataService.FAKE_GCS ->
                httpContainerProbe("http://localhost:4443/storage/v1/b")
        }

    private fun httpContainerProbe(url: String): String =
        "if command -v wget >/dev/null 2>&1; then wget -qO- ${shellQuote(url)} >/dev/null; else curl -fsS ${shellQuote(url)} >/dev/null; fi"

    private fun withShellTimeout(timeoutSeconds: Long, command: String): String =
        if (timeoutSeconds > 0) {
            "if command -v timeout >/dev/null 2>&1; then timeout $timeoutSeconds sh -c ${shellQuote(command)}; else $command; fi"
        } else {
            command
        }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"

    private fun GenericContainer<*>.addPort(port: ContainerPortOptions) {
        when (this) {
            is GenericBigDataContainer -> withServicePort(port.containerPort, port.hostPort)
            is FixedPortHiveMetastoreContainer -> withServicePort(port.containerPort, port.hostPort)
            is FixedPortKafkaContainer -> {
                require(port.hostPort > 0) { "Kafka extra fixed port requires a positive host port" }
                withServicePort(port.containerPort, port.hostPort)
            }
            else -> {
                require(port.hostPort == 0) {
                    "Fixed extra ports are only supported for bigdata-test managed container classes"
                }
                addExposedPort(port.containerPort)
            }
        }
    }

    private fun GenericContainer<*>.addFile(file: ContainerFileTransferOptions) {
        when {
            file.hostPath != null -> withCopyFileToContainer(
                MountableFile.forHostPath(file.hostPath, file.fileMode),
                file.containerPath,
            )
            file.content != null -> withCopyToContainer(file.transferable(), file.containerPath)
        }
    }

    private fun ContainerFileTransferOptions.transferable(): Transferable {
        val bytes = requireNotNull(content) { "Container file content is required" }
        return if (fileMode == null) {
            Transferable.of(bytes)
        } else {
            Transferable.of(bytes, fileMode)
        }
    }

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
                        if (options.containerLogs.append) {
                            StandardOpenOption.APPEND
                        } else {
                            StandardOpenOption.TRUNCATE_EXISTING
                        },
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

    private fun hdfsStartupCommand(hdfs: HdfsOptions): String =
        buildString {
            appendLine("set -e")
            appendLine("set -x")
            appendLine("cat > \"${'$'}HADOOP_CONF_DIR/core-site.xml\" <<EOF")
            appendLine("<configuration>")
            appendLine("  <property><name>fs.defaultFS</name><value>hdfs://hdfs:${hdfs.nameNodePort}</value></property>")
            appendLine(hdfsCoreSiteSecurity(hdfs.kerberos))
            appendLine("</configuration>")
            appendLine("EOF")
            appendLine("cat > \"${'$'}HADOOP_CONF_DIR/hdfs-site.xml\" <<EOF")
            appendLine("<configuration>")
            appendLine("  <property><name>dfs.replication</name><value>1</value></property>")
            appendLine("  <property><name>dfs.permissions.enabled</name><value>false</value></property>")
            appendLine("  <property><name>dfs.namenode.name.dir</name><value>file:///tmp/hadoop-name</value></property>")
            appendLine("  <property><name>dfs.datanode.data.dir</name><value>file:///tmp/hadoop-data</value></property>")
            appendLine("  <property><name>dfs.namenode.rpc-address</name><value>hdfs:${hdfs.nameNodePort}</value></property>")
            appendLine("  <property><name>dfs.namenode.rpc-bind-host</name><value>0.0.0.0</value></property>")
            appendLine("  <property><name>dfs.datanode.address</name><value>0.0.0.0:${hdfs.dataNodePort}</value></property>")
            appendLine("  <property><name>dfs.datanode.hostname</name><value>${hdfs.dataNodeHostname}</value></property>")
            appendLine("  <property><name>dfs.namenode.http-address</name><value>0.0.0.0:${hdfs.webPort}</value></property>")
            appendLine("  <property><name>dfs.namenode.http-bind-host</name><value>0.0.0.0</value></property>")
            appendLine(hdfsSiteSecurity(hdfs.kerberos))
            appendLine("</configuration>")
            appendLine("EOF")
            appendLine("hdfs namenode -format -force -nonInteractive")
            appendLine("hdfs namenode &")
            appendLine("namenode_pid=\"${'$'}!\"")
            appendLine("hdfs datanode &")
            appendLine("datanode_pid=\"${'$'}!\"")
            appendLine("sleep 3")
            appendLine("namenode_running=0")
            appendLine("datanode_running=0")
            appendLine("kill -0 \"${'$'}namenode_pid\" 2>/dev/null || namenode_running=1")
            appendLine("kill -0 \"${'$'}datanode_pid\" 2>/dev/null || datanode_running=1")
            appendLine("if [ \"${'$'}namenode_running\" -ne 0 ] || [ \"${'$'}datanode_running\" -ne 0 ]; then")
            appendLine("  echo \"HDFS process exited during startup\" >&2")
            appendLine("  find \"${'$'}HADOOP_LOG_DIR\" -maxdepth 1 -type f -print -exec sed -n '1,240p' {} \\; || true")
            appendLine("  exit 1")
            appendLine("fi")
            appendLine("for log_file in \"${'$'}HADOOP_LOG_DIR\"/*.log \"${'$'}HADOOP_LOG_DIR\"/*.out; do")
            appendLine("  if [ -e \"${'$'}log_file\" ]; then")
            appendLine("    tail -n +1 -F \"${'$'}log_file\" &")
            appendLine("  fi")
            appendLine("done")
            appendLine("wait")
        }

    private fun hdfsCoreSiteSecurity(kerberos: KerberosAuthOptions): String =
        if (kerberos.enabled) {
            """
            <property><name>hadoop.security.authentication</name><value>kerberos</value></property>
            ${hdfsProxyUserSecurity(options.hiveMetastore.kerberos)}
            """.trimIndent()
        } else {
            ""
        }

    private fun hdfsProxyUserSecurity(kerberos: KerberosAuthOptions): String =
        if (kerberos.enabled) {
            val shortName = kerberos.servicePrincipal.substringBefore("/")
            """
            <property><name>hadoop.proxyuser.$shortName.hosts</name><value>*</value></property>
            <property><name>hadoop.proxyuser.$shortName.groups</name><value>*</value></property>
            """.trimIndent()
        } else {
            ""
        }

    private fun hdfsKerberosClientProperties(kerberos: KerberosAuthOptions): Map<String, String> =
        if (kerberos.enabled) {
            mapOf(
                "hadoop.security.authentication" to "kerberos",
                "dfs.namenode.kerberos.principal" to kerberos.servicePrincipal,
                "dfs.datanode.kerberos.principal" to kerberos.servicePrincipal,
                "dfs.data.transfer.protection" to "authentication",
            )
        } else {
            emptyMap()
        }

    private fun writeLocalHdfsSiteXml(hdfs: HdfsOptions, properties: Map<String, String>): String {
        val path = hdfs.localHdfsSitePath
            ?.let { Path.of(it) }
            ?: hdfsClientDir.resolve("hdfs-site.xml")
        writeConfigurationXml(path, properties)
        return path.toString()
    }

    private fun hiveMetastoreClientProperties(
        thriftUri: String,
        tlsProperties: Map<String, String>,
    ): Map<String, String> =
        mapOf(
            "hive.metastore.uris" to thriftUri,
        ) + hiveMetastoreHadoopConfiguration() +
            hiveMetastoreClientKerberosProperties(options.hiveMetastore.kerberos) +
            tlsProperties

    private fun writeLocalHiveMetastoreClientXml(
        hive: HiveMetastoreOptions,
        properties: Map<String, String>,
    ): HiveMetastoreClientXmlPaths {
        val hiveSitePath = hive.localHiveSitePath
            ?.let { Path.of(it) }
            ?: hiveMetastoreClientDir.resolve("hive-site.xml")
        val metastoreSitePath = hive.localMetastoreSitePath
            ?.let { Path.of(it) }
            ?: hiveMetastoreClientDir.resolve("metastore-site.xml")
        writeConfigurationXml(hiveSitePath, properties)
        writeConfigurationXml(metastoreSitePath, properties)
        return HiveMetastoreClientXmlPaths(
            hiveSite = hiveSitePath.toString(),
            metastoreSite = metastoreSitePath.toString(),
        )
    }

    private data class HiveMetastoreClientXmlPaths(
        val hiveSite: String,
        val metastoreSite: String,
    )

    private fun hdfsSiteSecurity(kerberos: KerberosAuthOptions): String =
        if (kerberos.enabled) {
            """
          <property><name>dfs.namenode.kerberos.principal</name><value>${kerberos.servicePrincipal}</value></property>
          <property><name>dfs.namenode.keytab.file</name><value>${kerberos.keytabPath}</value></property>
          <property><name>dfs.datanode.kerberos.principal</name><value>${kerberos.servicePrincipal}</value></property>
          <property><name>dfs.datanode.keytab.file</name><value>${kerberos.keytabPath}</value></property>
          <property><name>dfs.block.access.token.enable</name><value>true</value></property>
          <property><name>dfs.data.transfer.protection</name><value>authentication</value></property>
          <property><name>ignore.secure.ports.for.testing</name><value>true</value></property>
            """.trimIndent()
        } else {
            ""
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
        val path = Path.of(localKerberosPath(destination))
        Files.createDirectories(path.parent)
        container.copyFileFromContainer(source, path.toString())
    }

    private fun waitForKerberosFiles(container: GenericContainer<*>, containerPaths: Set<String>) {
        val sources = containerPaths.map { it.replace("/kerby/", "/var/lib/kerby/") }
        val deadline = System.nanoTime() + Duration.ofSeconds(options.kerberos.materialTimeoutSeconds.toLong()).toNanos()
        while (true) {
            if (!container.isRunning) {
                error(
                    "Kerberos KDC stopped before generating expected keytab files: " +
                        "${sources.joinToString(", ")}\n${container.logs}",
                )
            }
            val missing = sources.filterNot { source ->
                try {
                    container.execInContainer("sh", "-lc", "test -s '$source'").exitCode == 0
                } catch (e: RuntimeException) {
                    if (!container.isRunning) {
                        error(
                            "Kerberos KDC stopped before generating expected keytab files: " +
                                "${sources.joinToString(", ")}\n${container.logs}",
                        )
                    }
                    throw e
                }
            }
            if (missing.isEmpty()) return
            if (System.nanoTime() >= deadline) {
                error("Kerberos KDC did not generate expected keytab files: ${missing.joinToString(", ")}")
            }
            Thread.sleep(250)
        }
    }

    private fun validateKerberosTiming(kerberos: KerberosOptions) {
        require(kerberos.startupTimeoutSeconds > 0) { "Kerberos startupTimeoutSeconds must be positive" }
        require(kerberos.materialTimeoutSeconds > 0) { "Kerberos materialTimeoutSeconds must be positive" }
        require(kerberos.adminAttempts > 0) { "Kerberos adminAttempts must be positive" }
        require(kerberos.adminRetryDelaySeconds > 0) { "Kerberos adminRetryDelaySeconds must be positive" }
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

    private fun hiveMetastoreClientKerberosProperties(options: KerberosAuthOptions): Map<String, String> =
        if (options.enabled) {
            mapOf(
                "hive.metastore.sasl.enabled" to "true",
                "hive.metastore.kerberos.principal" to options.servicePrincipal,
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
          keyTab="${jaasKeytab(options.keytabPath)}"
          principal="${jaasValue(options.servicePrincipal)}";
        };
        """.trimIndent()

    private fun inlineJaas(options: KerberosAuthOptions): String =
        """com.sun.security.auth.module.Krb5LoginModule required useKeyTab=true storeKey=true keyTab="${jaasKeytab(options.keytabPath)}" principal="${jaasValue(options.servicePrincipal)}";"""

    private fun inlineJaas(principal: String, keytabPath: String): String =
        """com.sun.security.auth.module.Krb5LoginModule required useKeyTab=true storeKey=true keyTab="${jaasKeytab(keytabPath)}" principal="${jaasValue(principal)}";"""

    private fun jaasKeytab(path: String): String =
        jaasValue(path.replace('\\', '/'))

    private fun jaasValue(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun localKerberosPath(containerPath: String): String =
        when {
            containerPath == "/kerby/client/krb5.conf" ->
                options.kerberos.localKrb5ConfPath ?: Path.of(kerberosDirectory())
                    .resolve("krb5-local.conf")
                    .toString()
            containerPath == "/kerby/keytabs/client.keytab" ->
                options.kerberos.localClientKeytabPath ?: Path.of(kerberosDirectory())
                    .resolve(containerPath.removePrefix("/kerby/"))
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
