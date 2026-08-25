package org.openprojectx.bigdata.test.core.config

import org.openprojectx.bigdata.test.core.BigDataContainerLogLevels
import org.openprojectx.bigdata.test.core.BigDataTestKitOptions
import org.openprojectx.bigdata.test.core.ClouderaHmsDatabaseType
import org.openprojectx.bigdata.test.core.ContainerLogMode
import org.openprojectx.bigdata.test.core.ContainerLogOptions
import org.openprojectx.bigdata.test.core.DEFAULT_FAKE_GCS_IMAGE
import org.openprojectx.bigdata.test.core.DEFAULT_HAPROXY_IMAGE
import org.openprojectx.bigdata.test.core.DEFAULT_HDFS_IMAGE
import org.openprojectx.bigdata.test.core.DEFAULT_ICEBERG_REST_CATALOG_IMAGE
import org.openprojectx.bigdata.test.core.DEFAULT_KAFKA_IMAGE
import org.openprojectx.bigdata.test.core.DEFAULT_KAFKA_UI_IMAGE
import org.openprojectx.bigdata.test.core.DEFAULT_KERBEROS_IMAGE
import org.openprojectx.bigdata.test.core.DEFAULT_S3_IMAGE
import org.openprojectx.bigdata.test.core.DEFAULT_SCHEMA_REGISTRY_IMAGE
import org.openprojectx.bigdata.test.core.HdfsOptions
import org.openprojectx.bigdata.test.core.HiveMetastoreDatabaseType
import org.openprojectx.bigdata.test.core.IcebergRestCatalogOptions
import org.openprojectx.bigdata.test.core.HiveMetastoreDistribution
import org.openprojectx.bigdata.test.core.HiveMetastoreOptions
import org.openprojectx.bigdata.test.core.HttpTlsOptions
import org.openprojectx.bigdata.test.core.KafkaOptions
import org.openprojectx.bigdata.test.core.KerberosAuthOptions
import org.openprojectx.bigdata.test.core.KerberosOptions
import org.openprojectx.bigdata.test.core.ObjectStoreOptions
import org.openprojectx.bigdata.test.core.PortBindingOptions
import org.openprojectx.bigdata.test.core.TlsOptions

/** Converts a config-only stack to runtime options. Annotation and Gradle DSL overrides are applied separately. */
fun BigDataTestConfig.toTestKitOptions(): BigDataTestKitOptions {
    val defaultKerberos = KerberosOptions()
    val realm = kerberos.realm ?: defaultKerberos.realm
    val domain = kerberos.domain ?: defaultKerberos.domain
    val hdfsKerberos = services.hdfsKerberos == true
    val hmsKerberos = services.hiveMetastoreKerberos == true
    val kafkaKerberos = services.kafkaKerberos == true
    val kafkaUiKerberos = services.kafkaUiKerberos == true
    val hdfsEnabled = services.hdfs == true || hdfsKerberos
    val openHms = services.hiveMetastore == true
    val clouderaHmsEnabled = services.clouderaHms == true
    require(!(openHms && clouderaHmsEnabled)) { "Use only one HMS implementation in an instance" }
    val hmsEnabled = openHms || clouderaHmsEnabled || hmsKerberos || hiveMetastoreTls.enabled == true
    val kafkaEnabled = services.kafka == true || kafkaKerberos || kafkaTls.enabled == true ||
        services.schemaRegistry == true || services.kafkaUi == true
    val kerberosEnabled = services.kerberos == true || hdfsKerberos || hmsKerberos || kafkaKerberos || kafkaUiKerberos
    val tlsEnabled = tls.enabled == true || listOf(
        hdfsWebTls,
        hiveMetastoreTls,
        kafkaTls,
        schemaRegistryTls,
        kafkaUiTls,
        s3Tls,
        fakeGcsTls,
        icebergRestCatalogTls,
    ).any { it.enabled == true }

    val customizations = containerCustomizations.toMutableMap()
    containerLogLevels.forEach { (service, level) ->
        val logOptions = org.openprojectx.bigdata.test.core.ContainerCustomizationOptions(
            environment = BigDataContainerLogLevels.environment(service, level),
        )
        customizations[service] = customizations[service]?.merge(logOptions) ?: logOptions
    }

    val hmsDistribution = if (clouderaHmsEnabled) HiveMetastoreDistribution.CLOUDERA else HiveMetastoreDistribution.OPEN_SOURCE
    val openDatabaseType = hiveMetastore.databaseType ?: HiveMetastoreDatabaseType.POSTGRESQL
    val clouderaDatabaseType = clouderaHms.databaseType ?: ClouderaHmsDatabaseType.POSTGRESQL
    val defaultHms = HiveMetastoreOptions()

    return BigDataTestKitOptions(
        kerberos = KerberosOptions(
            enabled = kerberosEnabled,
            image = images.kerberos ?: DEFAULT_KERBEROS_IMAGE,
            realm = realm,
            domain = domain,
            clientPrincipal = kerberos.clientPrincipal ?: defaultKerberos.clientPrincipal,
            clientPassword = kerberos.clientPassword ?: defaultKerberos.clientPassword,
            materialDirectory = kerberos.materialDirectory,
            localKrb5ConfPath = kerberos.localKrb5ConfPath,
            localClientKeytabPath = kerberos.localClientKeytabPath,
            startupTimeoutSeconds = kerberos.startupTimeoutSeconds ?: defaultKerberos.startupTimeoutSeconds,
            materialTimeoutSeconds = kerberos.materialTimeoutSeconds ?: defaultKerberos.materialTimeoutSeconds,
            adminAttempts = kerberos.adminAttempts ?: defaultKerberos.adminAttempts,
            adminRetryDelaySeconds = kerberos.adminRetryDelaySeconds ?: defaultKerberos.adminRetryDelaySeconds,
            debug = kerberos.debug ?: defaultKerberos.debug,
        ),
        tls = TlsOptions(
            enabled = tlsEnabled,
            caCertPath = tls.caCertPath,
            caKeyPath = tls.caKeyPath,
            trustStorePath = tls.trustStorePath,
            trustStorePassword = tls.trustStorePassword ?: "changeit",
            haproxyImage = tls.haproxyImage ?: DEFAULT_HAPROXY_IMAGE,
        ),
        hdfs = HdfsOptions(
            enabled = hdfsEnabled,
            image = images.hdfs ?: DEFAULT_HDFS_IMAGE,
            dataNodeHostname = hdfs.dataNodeHostname ?: HdfsOptions().dataNodeHostname,
            localHdfsSitePath = hdfs.localHdfsSitePath,
            webTls = hdfsWebTls.toHttpTls("localhost"),
            kerberos = KerberosAuthOptions(
                enabled = hdfsKerberos,
                servicePrincipal = "nn/hdfs.$domain@$realm",
                keytabPath = "/kerby/keytabs/hdfs-namenode.keytab",
            ),
        ),
        hiveMetastore = HiveMetastoreOptions(
            enabled = hmsEnabled,
            distribution = hmsDistribution,
            image = when (hmsDistribution) {
                HiveMetastoreDistribution.OPEN_SOURCE -> images.hiveMetastore ?: HiveMetastoreOptions.DEFAULT_IMAGE
                HiveMetastoreDistribution.CLOUDERA -> clouderaHmsImage(clouderaDatabaseType)
            },
            databaseType = openDatabaseType,
            clouderaDatabaseType = clouderaDatabaseType,
            databaseImage = when (openDatabaseType) {
                HiveMetastoreDatabaseType.POSTGRESQL ->
                    images.hiveMetastorePostgres ?: HiveMetastoreOptions.DEFAULT_POSTGRES_IMAGE
                HiveMetastoreDatabaseType.MYSQL ->
                    images.hiveMetastoreMysql ?: HiveMetastoreOptions.DEFAULT_MYSQL_IMAGE
            },
            databaseHostPort = when (hmsDistribution) {
                HiveMetastoreDistribution.OPEN_SOURCE -> hiveMetastore.databaseHostPort ?: 0
                HiveMetastoreDistribution.CLOUDERA -> clouderaHms.databaseHostPort ?: 0
            },
            databaseName = when (hmsDistribution) {
                HiveMetastoreDistribution.OPEN_SOURCE -> hiveMetastore.databaseName
                HiveMetastoreDistribution.CLOUDERA -> clouderaHms.databaseName
            } ?: defaultHms.databaseName,
            databaseUser = when (hmsDistribution) {
                HiveMetastoreDistribution.OPEN_SOURCE -> hiveMetastore.databaseUser
                HiveMetastoreDistribution.CLOUDERA -> clouderaHms.databaseUser
            } ?: defaultHms.databaseUser,
            databasePassword = when (hmsDistribution) {
                HiveMetastoreDistribution.OPEN_SOURCE -> hiveMetastore.databasePassword
                HiveMetastoreDistribution.CLOUDERA -> clouderaHms.databasePassword
            } ?: defaultHms.databasePassword,
            warehouseDir = when (hmsDistribution) {
                HiveMetastoreDistribution.OPEN_SOURCE -> hiveMetastore.warehouseDir ?: defaultHms.warehouseDir
                HiveMetastoreDistribution.CLOUDERA ->
                    clouderaHms.warehouseDir ?: HiveMetastoreOptions.DEFAULT_CLOUDERA_WAREHOUSE_DIR
            },
            localHiveSitePath = hiveMetastore.localHiveSitePath,
            localMetastoreSitePath = hiveMetastore.localMetastoreSitePath,
            tls = hiveMetastoreTls.toHttpTls("localhost"),
            kerberos = KerberosAuthOptions(
                enabled = hmsKerberos,
                servicePrincipal = "hive/hive-metastore.$domain@$realm",
                keytabPath = "/kerby/keytabs/hive-metastore.keytab",
            ),
        ),
        kafka = KafkaOptions(
            enabled = kafkaEnabled,
            image = images.kafka ?: DEFAULT_KAFKA_IMAGE,
            startupTimeoutSeconds = kafka.startupTimeoutSeconds?.toLong() ?: KafkaOptions().startupTimeoutSeconds,
            tls = kafkaTls.toHttpTls("localhost"),
            schemaRegistryEnabled = services.schemaRegistry == true,
            schemaRegistryImage = images.schemaRegistry ?: kafka.schemaRegistryImage ?: DEFAULT_SCHEMA_REGISTRY_IMAGE,
            schemaRegistryTls = schemaRegistryTls.toHttpTls("localhost"),
            kafkaUiEnabled = services.kafkaUi == true,
            kafkaUiImage = images.kafkaUi ?: kafka.kafkaUiImage ?: DEFAULT_KAFKA_UI_IMAGE,
            kafkaUiTls = kafkaUiTls.toHttpTls("localhost"),
            kerberos = KerberosAuthOptions(
                enabled = kafkaKerberos,
                servicePrincipal = "kafka/localhost@$realm",
                keytabPath = "/kerby/keytabs/kafka-broker1.keytab",
            ),
            kafkaUiKerberos = KerberosAuthOptions(
                enabled = kafkaUiKerberos,
                servicePrincipal = "kafbat-ui/kafbat-ui.$domain@$realm",
                keytabPath = "/kerby/keytabs/kafbat-ui.keytab",
            ),
        ),
        s3 = ObjectStoreOptions(
            enabled = services.s3 == true,
            image = images.s3 ?: DEFAULT_S3_IMAGE,
            tls = s3Tls.toHttpTls("localhost"),
        ),
        fakeGcs = ObjectStoreOptions(
            enabled = services.fakeGcs == true,
            image = images.fakeGcs ?: DEFAULT_FAKE_GCS_IMAGE,
            tls = fakeGcsTls.toHttpTls("storage.googleapis.com"),
        ),
        icebergRestCatalog = IcebergRestCatalogOptions(
            enabled = services.icebergRestCatalog == true,
            image = images.icebergRestCatalog ?: DEFAULT_ICEBERG_REST_CATALOG_IMAGE,
            warehouse = icebergRestCatalog.warehouse ?: IcebergRestCatalogOptions().warehouse,
            catalogBackend = icebergRestCatalog.catalogBackend ?: IcebergRestCatalogOptions().catalogBackend,
            uri = icebergRestCatalog.uri ?: IcebergRestCatalogOptions().uri,
            jdbcDriver = icebergRestCatalog.jdbcDriver ?: IcebergRestCatalogOptions().jdbcDriver,
            jdbcUser = icebergRestCatalog.jdbcUser ?: IcebergRestCatalogOptions().jdbcUser,
            jdbcPassword = icebergRestCatalog.jdbcPassword ?: IcebergRestCatalogOptions().jdbcPassword,
            ioImpl = icebergRestCatalog.ioImpl,
            credentialProviders = icebergRestCatalog.credentialProviders,
            s3RoleArn = icebergRestCatalog.s3RoleArn,
            s3ExternalId = icebergRestCatalog.s3ExternalId,
            s3TokenServiceEndpoint = icebergRestCatalog.s3TokenServiceEndpoint,
            tls = icebergRestCatalogTls.toHttpTls("localhost"),
        ),
        portBindings = ports.toPortBindings(),
        containerLogs = ContainerLogOptions(
            mode = containerLogs.mode ?: ContainerLogMode.NONE,
            directory = containerLogs.directory ?: "build/bigdata-test-container-logs",
            append = containerLogs.append ?: true,
        ),
        containerCustomizations = customizations,
        healthChecks = healthChecks,
        instances = instances.mapValues { (_, config) -> config.toTestKitOptions() },
    )
}

private fun BigDataTestHttpTlsConfig.toHttpTls(defaultDomain: String): HttpTlsOptions =
    HttpTlsOptions(enabled = enabled == true, domain = domain ?: defaultDomain)

private fun BigDataTestPortConfig.toPortBindings(): PortBindingOptions = PortBindingOptions(
    sameHostPorts = sameHostPorts ?: false,
    kerberosKdc = kerberosKdc ?: 0,
    hdfsNameNode = hdfsNameNode ?: 0,
    hdfsDataNode = hdfsDataNode ?: 0,
    hdfsWeb = hdfsWeb ?: 0,
    hdfsWebTls = hdfsWebTls ?: 0,
    hiveMetastore = hiveMetastore ?: 0,
    kafka = kafka ?: 0,
    schemaRegistry = schemaRegistry ?: 0,
    schemaRegistryTls = schemaRegistryTls ?: 0,
    kafkaUi = kafkaUi ?: 0,
    kafkaUiTls = kafkaUiTls ?: 0,
    s3 = s3 ?: 0,
    s3Tls = s3Tls ?: 0,
    fakeGcs = fakeGcs ?: 0,
    fakeGcsTls = fakeGcsTls ?: 0,
    icebergRestCatalog = icebergRestCatalog ?: 0,
    icebergRestCatalogTls = icebergRestCatalogTls ?: 0,
)

private fun BigDataTestConfig.clouderaHmsImage(type: ClouderaHmsDatabaseType): String =
    when (type) {
        ClouderaHmsDatabaseType.POSTGRESQL -> images.clouderaHms ?: HiveMetastoreOptions.DEFAULT_CLOUDERA_IMAGE
        ClouderaHmsDatabaseType.MARIADB ->
            images.clouderaHmsMariadb ?: HiveMetastoreOptions.DEFAULT_CLOUDERA_MARIADB_IMAGE
    }
