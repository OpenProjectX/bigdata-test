package org.openprojectx.bigdata.test.gradle

import java.nio.file.Files
import java.nio.file.Path
import org.openprojectx.bigdata.test.core.ContainerLogMode

internal data class BigDataTestGradleConfig(
    val images: Images = Images(),
    val services: Services = Services(),
    val kerberos: Kerberos = Kerberos(),
    val tls: Tls = Tls(),
    val hdfs: Hdfs = Hdfs(),
    val hiveMetastore: HiveMetastore = HiveMetastore(),
    val clouderaHms: ClouderaHms = ClouderaHms(),
    val kafka: Kafka = Kafka(),
    val ports: Ports = Ports(),
    val containerLogs: ContainerLogs = ContainerLogs(),
    val containerLogLevels: Map<String, String> = emptyMap(),
) {
    data class Images(
        val kerberos: String? = null,
        val hdfs: String? = null,
        val hiveMetastore: String? = null,
        val clouderaHms: String? = null,
        val hiveMetastorePostgres: String? = null,
        val kafka: String? = null,
        val schemaRegistry: String? = null,
        val kafkaUi: String? = null,
        val localStackS3: String? = null,
        val fakeGcs: String? = null,
    )

    data class Services(
        val kerberos: Boolean? = null,
        val hdfs: Boolean? = null,
        val hdfsKerberos: Boolean? = null,
        val hiveMetastore: Boolean? = null,
        val clouderaHms: Boolean? = null,
        val hiveMetastoreKerberos: Boolean? = null,
        val kafka: Boolean? = null,
        val kafkaKerberos: Boolean? = null,
        val schemaRegistry: Boolean? = null,
        val kafkaUi: Boolean? = null,
        val kafkaUiKerberos: Boolean? = null,
        val localStackS3: Boolean? = null,
        val fakeGcs: Boolean? = null,
    )

    data class Kerberos(
        val realm: String? = null,
        val domain: String? = null,
        val clientPrincipal: String? = null,
        val clientPassword: String? = null,
        val materialDirectory: String? = null,
        val localKrb5ConfPath: String? = null,
        val localClientKeytabPath: String? = null,
        val startupTimeoutSeconds: Int? = null,
        val materialTimeoutSeconds: Int? = null,
        val adminAttempts: Int? = null,
        val adminRetryDelaySeconds: Int? = null,
        val debug: Boolean? = null,
    )

    data class Tls(
        val enabled: Boolean? = null,
        val caCertPath: String? = null,
        val caKeyPath: String? = null,
        val trustStorePath: String? = null,
        val trustStorePassword: String? = null,
        val haproxyImage: String? = null,
    )

    data class Hdfs(
        val dataNodeHostname: String? = null,
    )

    data class HiveMetastore(
        val databaseName: String? = null,
        val databaseUser: String? = null,
        val databasePassword: String? = null,
        val warehouseDir: String? = null,
    )

    data class ClouderaHms(
        val warehouseDir: String? = null,
    )

    data class Kafka(
        val schemaRegistryImage: String? = null,
        val kafkaUiImage: String? = null,
    )

    data class Ports(
        val sameHostPorts: Boolean? = null,
        val kerberosKdc: Int? = null,
        val hdfsNameNode: Int? = null,
        val hdfsDataNode: Int? = null,
        val hdfsWeb: Int? = null,
        val hiveMetastore: Int? = null,
        val kafka: Int? = null,
        val schemaRegistry: Int? = null,
        val kafkaUi: Int? = null,
        val localStackS3: Int? = null,
        val fakeGcs: Int? = null,
    )

    data class ContainerLogs(
        val mode: ContainerLogMode? = null,
        val directory: String? = null,
        val append: Boolean? = null,
    )

    fun merge(override: BigDataTestGradleConfig): BigDataTestGradleConfig =
        BigDataTestGradleConfig(
            images = Images(
                kerberos = override.images.kerberos ?: images.kerberos,
                hdfs = override.images.hdfs ?: images.hdfs,
                hiveMetastore = override.images.hiveMetastore ?: images.hiveMetastore,
                clouderaHms = override.images.clouderaHms ?: images.clouderaHms,
                hiveMetastorePostgres = override.images.hiveMetastorePostgres ?: images.hiveMetastorePostgres,
                kafka = override.images.kafka ?: images.kafka,
                schemaRegistry = override.images.schemaRegistry ?: images.schemaRegistry,
                kafkaUi = override.images.kafkaUi ?: images.kafkaUi,
                localStackS3 = override.images.localStackS3 ?: images.localStackS3,
                fakeGcs = override.images.fakeGcs ?: images.fakeGcs,
            ),
            services = Services(
                kerberos = override.services.kerberos ?: services.kerberos,
                hdfs = override.services.hdfs ?: services.hdfs,
                hdfsKerberos = override.services.hdfsKerberos ?: services.hdfsKerberos,
                hiveMetastore = override.services.hiveMetastore ?: services.hiveMetastore,
                clouderaHms = override.services.clouderaHms ?: services.clouderaHms,
                hiveMetastoreKerberos = override.services.hiveMetastoreKerberos ?: services.hiveMetastoreKerberos,
                kafka = override.services.kafka ?: services.kafka,
                kafkaKerberos = override.services.kafkaKerberos ?: services.kafkaKerberos,
                schemaRegistry = override.services.schemaRegistry ?: services.schemaRegistry,
                kafkaUi = override.services.kafkaUi ?: services.kafkaUi,
                kafkaUiKerberos = override.services.kafkaUiKerberos ?: services.kafkaUiKerberos,
                localStackS3 = override.services.localStackS3 ?: services.localStackS3,
                fakeGcs = override.services.fakeGcs ?: services.fakeGcs,
            ),
            kerberos = Kerberos(
                realm = override.kerberos.realm ?: kerberos.realm,
                domain = override.kerberos.domain ?: kerberos.domain,
                clientPrincipal = override.kerberos.clientPrincipal ?: kerberos.clientPrincipal,
                clientPassword = override.kerberos.clientPassword ?: kerberos.clientPassword,
                materialDirectory = override.kerberos.materialDirectory ?: kerberos.materialDirectory,
                localKrb5ConfPath = override.kerberos.localKrb5ConfPath ?: kerberos.localKrb5ConfPath,
                localClientKeytabPath = override.kerberos.localClientKeytabPath ?: kerberos.localClientKeytabPath,
                startupTimeoutSeconds = override.kerberos.startupTimeoutSeconds ?: kerberos.startupTimeoutSeconds,
                materialTimeoutSeconds = override.kerberos.materialTimeoutSeconds ?: kerberos.materialTimeoutSeconds,
                adminAttempts = override.kerberos.adminAttempts ?: kerberos.adminAttempts,
                adminRetryDelaySeconds = override.kerberos.adminRetryDelaySeconds ?: kerberos.adminRetryDelaySeconds,
                debug = override.kerberos.debug ?: kerberos.debug,
            ),
            tls = Tls(
                enabled = override.tls.enabled ?: tls.enabled,
                caCertPath = override.tls.caCertPath ?: tls.caCertPath,
                caKeyPath = override.tls.caKeyPath ?: tls.caKeyPath,
                trustStorePath = override.tls.trustStorePath ?: tls.trustStorePath,
                trustStorePassword = override.tls.trustStorePassword ?: tls.trustStorePassword,
                haproxyImage = override.tls.haproxyImage ?: tls.haproxyImage,
            ),
            hdfs = Hdfs(dataNodeHostname = override.hdfs.dataNodeHostname ?: hdfs.dataNodeHostname),
            hiveMetastore = HiveMetastore(
                databaseName = override.hiveMetastore.databaseName ?: hiveMetastore.databaseName,
                databaseUser = override.hiveMetastore.databaseUser ?: hiveMetastore.databaseUser,
                databasePassword = override.hiveMetastore.databasePassword ?: hiveMetastore.databasePassword,
                warehouseDir = override.hiveMetastore.warehouseDir ?: hiveMetastore.warehouseDir,
            ),
            clouderaHms = ClouderaHms(warehouseDir = override.clouderaHms.warehouseDir ?: clouderaHms.warehouseDir),
            kafka = Kafka(
                schemaRegistryImage = override.kafka.schemaRegistryImage ?: kafka.schemaRegistryImage,
                kafkaUiImage = override.kafka.kafkaUiImage ?: kafka.kafkaUiImage,
            ),
            ports = Ports(
                sameHostPorts = override.ports.sameHostPorts ?: ports.sameHostPorts,
                kerberosKdc = override.ports.kerberosKdc ?: ports.kerberosKdc,
                hdfsNameNode = override.ports.hdfsNameNode ?: ports.hdfsNameNode,
                hdfsDataNode = override.ports.hdfsDataNode ?: ports.hdfsDataNode,
                hdfsWeb = override.ports.hdfsWeb ?: ports.hdfsWeb,
                hiveMetastore = override.ports.hiveMetastore ?: ports.hiveMetastore,
                kafka = override.ports.kafka ?: ports.kafka,
                schemaRegistry = override.ports.schemaRegistry ?: ports.schemaRegistry,
                kafkaUi = override.ports.kafkaUi ?: ports.kafkaUi,
                localStackS3 = override.ports.localStackS3 ?: ports.localStackS3,
                fakeGcs = override.ports.fakeGcs ?: ports.fakeGcs,
            ),
            containerLogs = ContainerLogs(
                mode = override.containerLogs.mode ?: containerLogs.mode,
                directory = override.containerLogs.directory ?: containerLogs.directory,
                append = override.containerLogs.append ?: containerLogs.append,
            ),
            containerLogLevels = containerLogLevels + override.containerLogLevels,
        )
}

internal class BigDataTestGradleConfigLoader(
    private val classLoader: ClassLoader,
) {
    fun load(locations: Iterable<String>): BigDataTestGradleConfig =
        locations.fold(BigDataTestGradleConfig()) { current, location ->
            current.merge(load(location))
        }

    private fun load(location: String): BigDataTestGradleConfig {
        val tables = parseTables(readText(location))
        val images = tables["images"].orEmpty()
        val services = tables["services"].orEmpty()
        val kerberos = tables["kerberos"].orEmpty()
        val tls = tables["tls"].orEmpty()
        val hdfs = tables["hdfs"].orEmpty()
        val hiveMetastore = tables["hiveMetastore"].orEmpty()
        val clouderaHms = tables["clouderaHms"].orEmpty()
        val kafka = tables["kafka"].orEmpty()
        val ports = tables["ports"].orEmpty()
        val containerLogs = tables["containerLogs"].orEmpty()
        val containerLogLevels = tables["containerLogLevels"].orEmpty()
        return BigDataTestGradleConfig(
            images = BigDataTestGradleConfig.Images(
                kerberos = images.string("kerberos"),
                hdfs = images.string("hdfs"),
                hiveMetastore = images.string("hiveMetastore"),
                clouderaHms = images.string("clouderaHms"),
                hiveMetastorePostgres = images.string("hiveMetastorePostgres"),
                kafka = images.string("kafka"),
                schemaRegistry = images.string("schemaRegistry"),
                kafkaUi = images.string("kafkaUi"),
                localStackS3 = images.string("localStackS3"),
                fakeGcs = images.string("fakeGcs"),
            ),
            services = BigDataTestGradleConfig.Services(
                kerberos = services.boolean("kerberos"),
                hdfs = services.boolean("hdfs"),
                hdfsKerberos = services.boolean("hdfsKerberos"),
                hiveMetastore = services.boolean("hiveMetastore"),
                clouderaHms = services.boolean("clouderaHms"),
                hiveMetastoreKerberos = services.boolean("hiveMetastoreKerberos"),
                kafka = services.boolean("kafka"),
                kafkaKerberos = services.boolean("kafkaKerberos"),
                schemaRegistry = services.boolean("schemaRegistry"),
                kafkaUi = services.boolean("kafkaUi"),
                kafkaUiKerberos = services.boolean("kafkaUiKerberos"),
                localStackS3 = services.boolean("localStackS3"),
                fakeGcs = services.boolean("fakeGcs"),
            ),
            kerberos = BigDataTestGradleConfig.Kerberos(
                realm = kerberos.string("realm"),
                domain = kerberos.string("domain"),
                clientPrincipal = kerberos.string("clientPrincipal"),
                clientPassword = kerberos.string("clientPassword"),
                materialDirectory = kerberos.string("materialDirectory"),
                localKrb5ConfPath = kerberos.string("localKrb5ConfPath"),
                localClientKeytabPath = kerberos.string("localClientKeytabPath"),
                startupTimeoutSeconds = kerberos.int("startupTimeoutSeconds"),
                materialTimeoutSeconds = kerberos.int("materialTimeoutSeconds"),
                adminAttempts = kerberos.int("adminAttempts"),
                adminRetryDelaySeconds = kerberos.int("adminRetryDelaySeconds"),
                debug = kerberos.boolean("debug"),
            ),
            tls = BigDataTestGradleConfig.Tls(
                enabled = tls.boolean("enabled"),
                caCertPath = tls.string("caCertPath"),
                caKeyPath = tls.string("caKeyPath"),
                trustStorePath = tls.string("trustStorePath"),
                trustStorePassword = tls.string("trustStorePassword"),
                haproxyImage = tls.string("haproxyImage"),
            ),
            hdfs = BigDataTestGradleConfig.Hdfs(dataNodeHostname = hdfs.string("dataNodeHostname")),
            hiveMetastore = BigDataTestGradleConfig.HiveMetastore(
                databaseName = hiveMetastore.string("databaseName"),
                databaseUser = hiveMetastore.string("databaseUser"),
                databasePassword = hiveMetastore.string("databasePassword"),
                warehouseDir = hiveMetastore.string("warehouseDir"),
            ),
            clouderaHms = BigDataTestGradleConfig.ClouderaHms(
                warehouseDir = clouderaHms.string("warehouseDir"),
            ),
            kafka = BigDataTestGradleConfig.Kafka(
                schemaRegistryImage = kafka.string("schemaRegistryImage"),
                kafkaUiImage = kafka.string("kafkaUiImage"),
            ),
            ports = BigDataTestGradleConfig.Ports(
                sameHostPorts = ports.boolean("sameHostPorts"),
                kerberosKdc = ports.int("kerberosKdc"),
                hdfsNameNode = ports.int("hdfsNameNode"),
                hdfsDataNode = ports.int("hdfsDataNode"),
                hdfsWeb = ports.int("hdfsWeb"),
                hiveMetastore = ports.int("hiveMetastore"),
                kafka = ports.int("kafka"),
                schemaRegistry = ports.int("schemaRegistry"),
                kafkaUi = ports.int("kafkaUi"),
                localStackS3 = ports.int("localStackS3"),
                fakeGcs = ports.int("fakeGcs"),
            ),
            containerLogs = BigDataTestGradleConfig.ContainerLogs(
                mode = containerLogs.string("mode")?.let { ContainerLogMode.valueOf(it.uppercase()) },
                directory = containerLogs.string("directory"),
                append = containerLogs.boolean("append"),
            ),
            containerLogLevels = containerLogLevels.mapValues { (key, value) ->
                value.asString("containerLogLevels.$key")
            },
        )
    }

    private fun readText(location: String): String =
        when {
            location.startsWith("classpath:") -> {
                val path = location.removePrefix("classpath:").removePrefix("/")
                classLoader.getResource(path)?.readText()
                    ?: error("BigDataTest Gradle config resource '$location' was not found")
            }
            location.startsWith("file:") -> Files.readString(Path.of(location.removePrefix("file:")))
            else -> Files.readString(Path.of(location))
        }

    private fun parseTables(text: String): Map<String, Map<String, TomlValue>> {
        val tables = linkedMapOf<String, MutableMap<String, TomlValue>>()
        var table = ""
        tables[table] = linkedMapOf()
        text.lineSequence().forEachIndexed { index, raw ->
            val line = stripComment(raw).trim()
            if (line.isBlank()) return@forEachIndexed
            if (line.startsWith("[") && line.endsWith("]")) {
                table = line.removePrefix("[").removeSuffix("]").trim()
                require(table.isNotBlank()) { "TOML table name is blank at line ${index + 1}" }
                tables.getOrPut(table) { linkedMapOf() }
                return@forEachIndexed
            }
            val separator = line.indexOf('=')
            require(separator > 0) { "TOML line ${index + 1} must be key = value" }
            val key = parseKey(line.substring(0, separator).trim(), index + 1)
            val value = line.substring(separator + 1).trim()
            tables.getValue(table)[key] = parseValue(value, index + 1)
        }
        return tables
    }

    private fun stripComment(raw: String): String {
        var inString = false
        var escaped = false
        raw.forEachIndexed { index, char ->
            when {
                escaped -> escaped = false
                char == '\\' && inString -> escaped = true
                char == '"' -> inString = !inString
                char == '#' && !inString -> return raw.substring(0, index)
            }
        }
        return raw
    }

    private fun parseKey(key: String, line: Int): String =
        if (key.startsWith('"')) parseString(key, line) else key

    private fun parseValue(value: String, line: Int): TomlValue =
        when {
            value.startsWith('"') -> TomlValue.StringValue(parseString(value, line))
            value == "true" -> TomlValue.BooleanValue(true)
            value == "false" -> TomlValue.BooleanValue(false)
            value.matches(Regex("[0-9]+")) -> TomlValue.IntValue(value.toInt())
            else -> error("Unsupported TOML value at line $line: $value")
        }

    private fun parseString(value: String, line: Int): String {
        require(value.length >= 2 && value.first() == '"' && value.last() == '"') {
            "TOML string at line $line must be quoted"
        }
        return buildString {
            var escaped = false
            value.substring(1, value.length - 1).forEach { char ->
                if (escaped) {
                    append(
                        when (char) {
                            '"' -> '"'
                            '\\' -> '\\'
                            'n' -> '\n'
                            'r' -> '\r'
                            't' -> '\t'
                            else -> char
                        },
                    )
                    escaped = false
                } else if (char == '\\') {
                    escaped = true
                } else {
                    append(char)
                }
            }
        }
    }

    private fun Map<String, TomlValue>.string(key: String): String? =
        this[key]?.let {
            require(it is TomlValue.StringValue) { "TOML key '$key' must be a quoted string" }
            it.value
        }

    private fun Map<String, TomlValue>.boolean(key: String): Boolean? =
        this[key]?.let {
            require(it is TomlValue.BooleanValue) { "TOML key '$key' must be true or false" }
            it.value
        }

    private fun Map<String, TomlValue>.int(key: String): Int? =
        this[key]?.let {
            require(it is TomlValue.IntValue) { "TOML key '$key' must be an integer" }
            it.value
        }

    private fun TomlValue.asString(name: String): String {
        require(this is TomlValue.StringValue) { "$name must be a quoted string" }
        return value
    }

    private sealed interface TomlValue {
        data class StringValue(val value: String) : TomlValue
        data class BooleanValue(val value: Boolean) : TomlValue
        data class IntValue(val value: Int) : TomlValue
    }
}
