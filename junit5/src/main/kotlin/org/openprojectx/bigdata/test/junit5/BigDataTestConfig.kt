package org.openprojectx.bigdata.test.junit5

import java.nio.file.Files
import java.nio.file.Path
import org.openprojectx.bigdata.test.core.BigDataService
import org.openprojectx.bigdata.test.core.BigDataHealthCheckMode
import org.openprojectx.bigdata.test.core.BigDataHealthCheckOptions
import org.openprojectx.bigdata.test.core.ContainerCustomizationOptions
import org.openprojectx.bigdata.test.core.ContainerFileTransferOptions
import org.openprojectx.bigdata.test.core.ContainerLogMode
import org.openprojectx.bigdata.test.core.ContainerMountOptions
import org.openprojectx.bigdata.test.core.ContainerPortOptions

internal data class BigDataTestConfig(
    val images: BigDataTestImageConfig = BigDataTestImageConfig(),
    val services: BigDataTestServiceConfig = BigDataTestServiceConfig(),
    val kerberos: BigDataTestKerberosConfig = BigDataTestKerberosConfig(),
    val tls: BigDataTestTlsConfig = BigDataTestTlsConfig(),
    val hdfs: BigDataTestHdfsConfig = BigDataTestHdfsConfig(),
    val hdfsWebTls: BigDataTestHttpTlsConfig = BigDataTestHttpTlsConfig(),
    val hiveMetastoreTls: BigDataTestHttpTlsConfig = BigDataTestHttpTlsConfig(),
    val kafkaTls: BigDataTestHttpTlsConfig = BigDataTestHttpTlsConfig(),
    val schemaRegistryTls: BigDataTestHttpTlsConfig = BigDataTestHttpTlsConfig(),
    val kafkaUiTls: BigDataTestHttpTlsConfig = BigDataTestHttpTlsConfig(),
    val localStackS3Tls: BigDataTestHttpTlsConfig = BigDataTestHttpTlsConfig(),
    val fakeGcsTls: BigDataTestHttpTlsConfig = BigDataTestHttpTlsConfig(),
    val ports: BigDataTestPortConfig = BigDataTestPortConfig(),
    val containerLogs: BigDataTestContainerLogConfig = BigDataTestContainerLogConfig(),
    val containerLogLevels: BigDataTestContainerLogLevelConfig = BigDataTestContainerLogLevelConfig(),
    val containerCustomizations: BigDataTestContainerCustomizationConfig =
        BigDataTestContainerCustomizationConfig(),
    val healthChecks: BigDataTestHealthCheckConfig = BigDataTestHealthCheckConfig(),
) {
    fun merge(override: BigDataTestConfig): BigDataTestConfig =
        BigDataTestConfig(
            images = images.merge(override.images),
            services = services.merge(override.services),
            kerberos = kerberos.merge(override.kerberos),
            tls = tls.merge(override.tls),
            hdfs = hdfs.merge(override.hdfs),
            hdfsWebTls = hdfsWebTls.merge(override.hdfsWebTls),
            hiveMetastoreTls = hiveMetastoreTls.merge(override.hiveMetastoreTls),
            kafkaTls = kafkaTls.merge(override.kafkaTls),
            schemaRegistryTls = schemaRegistryTls.merge(override.schemaRegistryTls),
            kafkaUiTls = kafkaUiTls.merge(override.kafkaUiTls),
            localStackS3Tls = localStackS3Tls.merge(override.localStackS3Tls),
            fakeGcsTls = fakeGcsTls.merge(override.fakeGcsTls),
            ports = ports.merge(override.ports),
            containerLogs = containerLogs.merge(override.containerLogs),
            containerLogLevels = containerLogLevels.merge(override.containerLogLevels),
            containerCustomizations = containerCustomizations.merge(override.containerCustomizations),
            healthChecks = healthChecks.merge(override.healthChecks),
        )
}

internal data class BigDataTestHdfsConfig(
    val dataNodeHostname: String? = null,
) {
    fun merge(override: BigDataTestHdfsConfig): BigDataTestHdfsConfig =
        BigDataTestHdfsConfig(
            dataNodeHostname = override.dataNodeHostname ?: dataNodeHostname,
        )
}

internal data class BigDataTestKerberosConfig(
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
) {
    fun merge(override: BigDataTestKerberosConfig): BigDataTestKerberosConfig =
        BigDataTestKerberosConfig(
            realm = override.realm ?: realm,
            domain = override.domain ?: domain,
            clientPrincipal = override.clientPrincipal ?: clientPrincipal,
            clientPassword = override.clientPassword ?: clientPassword,
            materialDirectory = override.materialDirectory ?: materialDirectory,
            localKrb5ConfPath = override.localKrb5ConfPath ?: localKrb5ConfPath,
            localClientKeytabPath = override.localClientKeytabPath ?: localClientKeytabPath,
            startupTimeoutSeconds = override.startupTimeoutSeconds ?: startupTimeoutSeconds,
            materialTimeoutSeconds = override.materialTimeoutSeconds ?: materialTimeoutSeconds,
            adminAttempts = override.adminAttempts ?: adminAttempts,
            adminRetryDelaySeconds = override.adminRetryDelaySeconds ?: adminRetryDelaySeconds,
            debug = override.debug ?: debug,
        )
}

internal data class BigDataTestTlsConfig(
    val enabled: Boolean? = null,
    val caCertPath: String? = null,
    val caKeyPath: String? = null,
    val trustStorePath: String? = null,
    val trustStorePassword: String? = null,
    val haproxyImage: String? = null,
) {
    fun merge(override: BigDataTestTlsConfig): BigDataTestTlsConfig =
        BigDataTestTlsConfig(
            enabled = override.enabled ?: enabled,
            caCertPath = override.caCertPath ?: caCertPath,
            caKeyPath = override.caKeyPath ?: caKeyPath,
            trustStorePath = override.trustStorePath ?: trustStorePath,
            trustStorePassword = override.trustStorePassword ?: trustStorePassword,
            haproxyImage = override.haproxyImage ?: haproxyImage,
        )

    fun hasValues(): Boolean =
        listOf(enabled, caCertPath, caKeyPath, trustStorePath, trustStorePassword, haproxyImage).any { it != null }
}

internal data class BigDataTestHttpTlsConfig(
    val enabled: Boolean? = null,
    val domain: String? = null,
) {
    fun merge(override: BigDataTestHttpTlsConfig): BigDataTestHttpTlsConfig =
        BigDataTestHttpTlsConfig(
            enabled = override.enabled ?: enabled,
            domain = override.domain ?: domain,
        )
}

internal data class BigDataTestImageConfig(
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
) {
    fun merge(override: BigDataTestImageConfig): BigDataTestImageConfig =
        BigDataTestImageConfig(
            kerberos = override.kerberos ?: kerberos,
            hdfs = override.hdfs ?: hdfs,
            hiveMetastore = override.hiveMetastore ?: hiveMetastore,
            clouderaHms = override.clouderaHms ?: clouderaHms,
            hiveMetastorePostgres = override.hiveMetastorePostgres ?: hiveMetastorePostgres,
            kafka = override.kafka ?: kafka,
            schemaRegistry = override.schemaRegistry ?: schemaRegistry,
            kafkaUi = override.kafkaUi ?: kafkaUi,
            localStackS3 = override.localStackS3 ?: localStackS3,
            fakeGcs = override.fakeGcs ?: fakeGcs,
        )
}

internal data class BigDataTestServiceConfig(
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
) {
    fun merge(override: BigDataTestServiceConfig): BigDataTestServiceConfig =
        BigDataTestServiceConfig(
            kerberos = override.kerberos ?: kerberos,
            hdfs = override.hdfs ?: hdfs,
            hdfsKerberos = override.hdfsKerberos ?: hdfsKerberos,
            hiveMetastore = override.hiveMetastore ?: hiveMetastore,
            clouderaHms = override.clouderaHms ?: clouderaHms,
            hiveMetastoreKerberos = override.hiveMetastoreKerberos ?: hiveMetastoreKerberos,
            kafka = override.kafka ?: kafka,
            kafkaKerberos = override.kafkaKerberos ?: kafkaKerberos,
            schemaRegistry = override.schemaRegistry ?: schemaRegistry,
            kafkaUi = override.kafkaUi ?: kafkaUi,
            kafkaUiKerberos = override.kafkaUiKerberos ?: kafkaUiKerberos,
            localStackS3 = override.localStackS3 ?: localStackS3,
            fakeGcs = override.fakeGcs ?: fakeGcs,
        )
}

internal data class BigDataTestPortConfig(
    val sameHostPorts: Boolean? = null,
    val kerberosKdc: Int? = null,
    val hdfsNameNode: Int? = null,
    val hdfsDataNode: Int? = null,
    val hdfsWeb: Int? = null,
    val hdfsWebTls: Int? = null,
    val hiveMetastore: Int? = null,
    val kafka: Int? = null,
    val schemaRegistry: Int? = null,
    val schemaRegistryTls: Int? = null,
    val kafkaUi: Int? = null,
    val kafkaUiTls: Int? = null,
    val localStackS3: Int? = null,
    val localStackS3Tls: Int? = null,
    val fakeGcs: Int? = null,
    val fakeGcsTls: Int? = null,
) {
    fun merge(override: BigDataTestPortConfig): BigDataTestPortConfig =
        BigDataTestPortConfig(
            sameHostPorts = override.sameHostPorts ?: sameHostPorts,
            kerberosKdc = override.kerberosKdc ?: kerberosKdc,
            hdfsNameNode = override.hdfsNameNode ?: hdfsNameNode,
            hdfsDataNode = override.hdfsDataNode ?: hdfsDataNode,
            hdfsWeb = override.hdfsWeb ?: hdfsWeb,
            hdfsWebTls = override.hdfsWebTls ?: hdfsWebTls,
            hiveMetastore = override.hiveMetastore ?: hiveMetastore,
            kafka = override.kafka ?: kafka,
            schemaRegistry = override.schemaRegistry ?: schemaRegistry,
            schemaRegistryTls = override.schemaRegistryTls ?: schemaRegistryTls,
            kafkaUi = override.kafkaUi ?: kafkaUi,
            kafkaUiTls = override.kafkaUiTls ?: kafkaUiTls,
            localStackS3 = override.localStackS3 ?: localStackS3,
            localStackS3Tls = override.localStackS3Tls ?: localStackS3Tls,
            fakeGcs = override.fakeGcs ?: fakeGcs,
            fakeGcsTls = override.fakeGcsTls ?: fakeGcsTls,
        )
}

internal data class BigDataTestContainerLogConfig(
    val mode: ContainerLogMode? = null,
    val directory: String? = null,
    val append: Boolean? = null,
) {
    fun merge(override: BigDataTestContainerLogConfig): BigDataTestContainerLogConfig =
        BigDataTestContainerLogConfig(
            mode = override.mode ?: mode,
            directory = override.directory ?: directory,
            append = override.append ?: append,
        )
}

internal data class BigDataTestContainerLogLevelConfig(
    val levels: Map<BigDataService, String> = emptyMap(),
) {
    fun merge(override: BigDataTestContainerLogLevelConfig): BigDataTestContainerLogLevelConfig =
        BigDataTestContainerLogLevelConfig(levels + override.levels)
}

internal data class BigDataTestContainerCustomizationConfig(
    val customizations: Map<BigDataService, ContainerCustomizationOptions> = emptyMap(),
) {
    fun merge(override: BigDataTestContainerCustomizationConfig): BigDataTestContainerCustomizationConfig {
        val merged = customizations.toMutableMap()
        override.customizations.forEach { (service, customization) ->
            merged[service] = merged[service]?.merge(customization) ?: customization
        }
        return BigDataTestContainerCustomizationConfig(merged)
    }
}

internal data class BigDataTestHealthCheckConfig(
    val healthChecks: Map<BigDataService, BigDataHealthCheckOptions> = emptyMap(),
) {
    fun merge(override: BigDataTestHealthCheckConfig): BigDataTestHealthCheckConfig {
        val merged = healthChecks.toMutableMap()
        override.healthChecks.forEach { (service, options) ->
            merged[service] = options
        }
        return BigDataTestHealthCheckConfig(merged)
    }
}

internal class BigDataTestConfigLoader(
    private val classLoader: ClassLoader,
) {
    fun load(locations: Iterable<String>): BigDataTestConfig =
        locations.fold(BigDataTestConfig()) { current, location ->
            current.merge(load(location))
        }

    private fun load(location: String): BigDataTestConfig {
        val tables = parseTables(readText(location))
        val images = tables["images"].orEmpty()
        val services = tables["services"].orEmpty()
        val kerberos = tables["kerberos"].orEmpty()
        val tls = tables["tls"].orEmpty()
        val hdfs = tables["hdfs"].orEmpty()
        val hdfsWebTls = tables["hdfsWebTls"].orEmpty()
        val hiveMetastoreTls = tables["hiveMetastoreTls"].orEmpty()
        val kafkaTls = tables["kafkaTls"].orEmpty()
        val schemaRegistryTls = tables["schemaRegistryTls"].orEmpty()
        val kafkaUiTls = tables["kafkaUiTls"].orEmpty()
        val localStackS3Tls = tables["localStackS3Tls"].orEmpty()
        val fakeGcsTls = tables["fakeGcsTls"].orEmpty()
        val ports = tables["ports"].orEmpty()
        val containerLogs = tables["containerLogs"].orEmpty()
        return BigDataTestConfig(
            images = BigDataTestImageConfig(
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
            services = BigDataTestServiceConfig(
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
            kerberos = BigDataTestKerberosConfig(
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
            tls = BigDataTestTlsConfig(
                enabled = tls.boolean("enabled"),
                caCertPath = tls.string("caCertPath"),
                caKeyPath = tls.string("caKeyPath"),
                trustStorePath = tls.string("trustStorePath"),
                trustStorePassword = tls.string("trustStorePassword"),
                haproxyImage = tls.string("haproxyImage"),
            ),
            hdfs = BigDataTestHdfsConfig(
                dataNodeHostname = hdfs.string("dataNodeHostname"),
            ),
            hdfsWebTls = httpTls(hdfsWebTls),
            hiveMetastoreTls = httpTls(hiveMetastoreTls),
            kafkaTls = httpTls(kafkaTls),
            schemaRegistryTls = httpTls(schemaRegistryTls),
            kafkaUiTls = httpTls(kafkaUiTls),
            localStackS3Tls = httpTls(localStackS3Tls),
            fakeGcsTls = httpTls(fakeGcsTls),
            ports = BigDataTestPortConfig(
                sameHostPorts = ports.boolean("sameHostPorts"),
                kerberosKdc = ports.int("kerberosKdc"),
                hdfsNameNode = ports.int("hdfsNameNode"),
                hdfsDataNode = ports.int("hdfsDataNode"),
                hdfsWeb = ports.int("hdfsWeb"),
                hdfsWebTls = ports.int("hdfsWebTls"),
                hiveMetastore = ports.int("hiveMetastore"),
                kafka = ports.int("kafka"),
                schemaRegistry = ports.int("schemaRegistry"),
                schemaRegistryTls = ports.int("schemaRegistryTls"),
                kafkaUi = ports.int("kafkaUi"),
                kafkaUiTls = ports.int("kafkaUiTls"),
                localStackS3 = ports.int("localStackS3"),
                localStackS3Tls = ports.int("localStackS3Tls"),
                fakeGcs = ports.int("fakeGcs"),
                fakeGcsTls = ports.int("fakeGcsTls"),
            ),
            containerLogs = BigDataTestContainerLogConfig(
                mode = containerLogs.string("mode")?.let { ContainerLogMode.valueOf(it.uppercase()) },
                directory = containerLogs.string("directory"),
                append = containerLogs.boolean("append"),
            ),
            containerLogLevels = parseContainerLogLevels(tables),
            containerCustomizations = parseContainerCustomizations(tables),
            healthChecks = parseHealthChecks(tables),
        )
    }

    private fun readText(location: String): String =
        when {
            location.startsWith("classpath:") -> {
                val path = location.removePrefix("classpath:").removePrefix("/")
                classLoader.getResource(path)?.readText()
                    ?: error("BigDataTest config resource '$location' was not found")
            }
            else -> Files.readString(Path.of(location))
        }

    private fun parseTables(text: String): Map<String, Map<String, TomlValue>> {
        val tables = linkedMapOf<String, MutableMap<String, TomlValue>>()
        var currentTable: String? = null
        val knownTables = setOf(
            "images",
            "services",
            "kerberos",
            "tls",
            "hdfs",
            "hdfsWebTls",
            "hiveMetastoreTls",
            "kafkaTls",
            "schemaRegistryTls",
            "kafkaUiTls",
            "localStackS3Tls",
            "fakeGcsTls",
            "ports",
            "containerLogs",
            "containerLogLevels",
            "healthChecks",
            "healthCheckTimeouts",
        )
        text.lineSequence().forEachIndexed { index, raw ->
            val line = stripComment(raw).trim()
            if (line.isEmpty()) return@forEachIndexed
            if (line.startsWith("[") && line.endsWith("]")) {
                currentTable = line.removePrefix("[").removeSuffix("]")
                if (currentTable in knownTables || currentTable?.startsWith("containers.") == true) {
                    tables.getOrPut(currentTable!!) { linkedMapOf() }
                }
                return@forEachIndexed
            }
            val table = currentTable ?: return@forEachIndexed
            if (table !in knownTables && !table.startsWith("containers.")) return@forEachIndexed
            val separator = line.indexOf('=')
            require(separator > 0) { "TOML line ${index + 1}: expected key = value" }
            val key = parseKey(line.substring(0, separator).trim(), index)
            val value = line.substring(separator + 1).trim()
            tables.getValue(table)[key] = parseValue(value, index)
        }
        return tables
    }

    private fun parseKey(key: String, line: Int): String =
        if (key.startsWith('"')) parseString(key, line) else key

    private fun stripComment(line: String): String {
        var quoted = false
        var escaped = false
        line.forEachIndexed { index, char ->
            when {
                escaped -> escaped = false
                char == '\\' && quoted -> escaped = true
                char == '"' -> quoted = !quoted
                char == '#' && !quoted -> return line.substring(0, index)
            }
        }
        return line
    }

    private fun parseValue(value: String, line: Int): TomlValue =
        when {
            value.startsWith('"') -> TomlValue.StringValue(parseString(value, line))
            value == "true" -> TomlValue.BooleanValue(true)
            value == "false" -> TomlValue.BooleanValue(false)
            value.matches(Regex("[0-9]+")) -> TomlValue.IntValue(value.toInt())
            else -> error("TOML line ${line + 1}: unsupported value '$value'")
        }

    private fun parseString(value: String, line: Int): String {
        require(value.startsWith('"') && value.endsWith('"')) {
            "TOML line ${line + 1}: string values must be quoted"
        }
        return buildString {
            var escaped = false
            value.substring(1, value.length - 1).forEach { char ->
                when {
                    escaped -> {
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
                    }
                    char == '\\' -> escaped = true
                    else -> append(char)
                }
            }
            require(!escaped) { "TOML line ${line + 1}: unterminated escape in string" }
        }
    }

    private fun httpTls(values: Map<String, TomlValue>): BigDataTestHttpTlsConfig =
        BigDataTestHttpTlsConfig(
            enabled = values.boolean("enabled"),
            domain = values.string("domain"),
        )

    private fun parseContainerCustomizations(
        tables: Map<String, Map<String, TomlValue>>,
    ): BigDataTestContainerCustomizationConfig {
        val customizations = linkedMapOf<BigDataService, ContainerCustomizationOptions>()
        tables.forEach { (table, values) ->
            if (!table.startsWith("containers.")) return@forEach
            val parts = table.split('.')
            require(parts.size == 2 || parts.size == 3) {
                "TOML table '$table' must use containers.<service> or containers.<service>.<env|files|mounts|ports>"
            }
            val service = parseService(parts[1], table)
            val options = if (parts.size == 2) {
                ContainerCustomizationOptions(networkMode = values.string("networkMode"))
            } else {
                when (parts[2]) {
                    "env" -> ContainerCustomizationOptions(environment = values.stringMap(table))
                    "files" -> ContainerCustomizationOptions(files = values.containerFiles())
                    "mounts" -> ContainerCustomizationOptions(mounts = values.containerMounts())
                    "ports" -> ContainerCustomizationOptions(ports = values.containerPorts(table))
                    else -> error("TOML table '$table' must end with env, files, mounts, or ports")
                }
            }
            customizations[service] = customizations[service]?.merge(options) ?: options
        }
        return BigDataTestContainerCustomizationConfig(customizations)
    }

    private fun parseContainerLogLevels(tables: Map<String, Map<String, TomlValue>>): BigDataTestContainerLogLevelConfig {
        val values = tables["containerLogLevels"].orEmpty()
        val levels = values.mapKeys { (serviceName, _) -> parseService(serviceName, "containerLogLevels") }
            .mapValues { (service, value) -> normalizeContainerLogLevel(value.asString("container log level '$service'")) }
        return BigDataTestContainerLogLevelConfig(levels)
    }

    private fun normalizeContainerLogLevel(level: String): String {
        val normalized = level.trim().uppercase()
        val supported = setOf("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "OFF")
        require(normalized in supported) {
            "Container log level must be one of ${supported.joinToString()}, got '$level'"
        }
        return normalized
    }

    private fun parseService(name: String, table: String): BigDataService {
        val normalized = name.replace("-", "").replace("_", "").lowercase()
        return SERVICE_NAMES[normalized] ?: error("TOML table '$table' uses unknown service '$name'")
    }

    private fun parseHealthChecks(tables: Map<String, Map<String, TomlValue>>): BigDataTestHealthCheckConfig {
        val modes = tables["healthChecks"].orEmpty()
        val timeouts = tables["healthCheckTimeouts"].orEmpty()
        val healthChecks = linkedMapOf<BigDataService, BigDataHealthCheckOptions>()
        modes.forEach { (serviceName, value) ->
            val service = parseService(serviceName, "healthChecks")
            val mode = BigDataHealthCheckMode.valueOf(value.asString("health check '$serviceName'").uppercase())
            healthChecks[service] = BigDataHealthCheckOptions(
                mode = mode,
                timeoutSeconds = timeouts.int(serviceName)?.toLong() ?: 60,
            )
        }
        return BigDataTestHealthCheckConfig(healthChecks)
    }

    private fun Map<String, TomlValue>.containerFiles(): List<ContainerFileTransferOptions> =
        map { (containerPath, value) ->
            val source = value.asString("container file '$containerPath'")
            when {
                source.startsWith("classpath:") -> {
                    val path = source.removePrefix("classpath:").removePrefix("/")
                    val content = classLoader.getResource(path)?.readBytes()
                        ?: error("Container file resource '$source' was not found")
                    ContainerFileTransferOptions.content(content, containerPath)
                }
                source.startsWith("file:") ->
                    ContainerFileTransferOptions.hostPath(source.removePrefix("file:"), containerPath)
                source.startsWith("text:") ->
                    ContainerFileTransferOptions.content(source.removePrefix("text:"), containerPath)
                else -> error(
                    "Container file '$containerPath' must use classpath:, file:, or text: source prefix",
                )
            }
        }

    private fun Map<String, TomlValue>.containerMounts(): List<ContainerMountOptions> =
        map { (hostPath, value) ->
            val target = value.asString("container mount '$hostPath'")
            val readOnly = !target.endsWith(":rw")
            val containerPath = target.removeSuffix(":ro").removeSuffix(":rw")
            ContainerMountOptions(hostPath = hostPath, containerPath = containerPath, readOnly = readOnly)
        }

    private fun Map<String, TomlValue>.containerPorts(table: String): List<ContainerPortOptions> =
        map { (containerPort, value) ->
            ContainerPortOptions(
                containerPort = containerPort.toIntOrNull()
                    ?: error("TOML table '$table' port key '$containerPort' must be an integer"),
                hostPort = value.asInt("container port '$containerPort'"),
            )
        }

    private fun Map<String, TomlValue>.stringMap(table: String): Map<String, String> =
        mapValues { (key, value) -> value.asString("TOML key '$table.$key'") }

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

    private fun TomlValue.asInt(name: String): Int {
        require(this is TomlValue.IntValue) { "$name must be an integer" }
        return value
    }

    private sealed interface TomlValue {
        data class StringValue(val value: String) : TomlValue
        data class BooleanValue(val value: Boolean) : TomlValue
        data class IntValue(val value: Int) : TomlValue
    }

    private companion object {
        val SERVICE_NAMES: Map<String, BigDataService> = mapOf(
            "kerberos" to BigDataService.KERBEROS,
            "hdfs" to BigDataService.HDFS,
            "hivemetastore" to BigDataService.HIVE_METASTORE,
            "hms" to BigDataService.HIVE_METASTORE,
            "kafka" to BigDataService.KAFKA,
            "schemaregistry" to BigDataService.SCHEMA_REGISTRY,
            "kafkaui" to BigDataService.KAFKA_UI,
            "localstacks3" to BigDataService.LOCALSTACK_S3,
            "fakegcs" to BigDataService.FAKE_GCS,
        )
    }
}
