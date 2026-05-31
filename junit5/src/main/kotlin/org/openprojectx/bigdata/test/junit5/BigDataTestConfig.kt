package org.openprojectx.bigdata.test.junit5

import java.nio.file.Files
import java.nio.file.Path
import org.openprojectx.bigdata.test.core.ContainerLogMode

internal data class BigDataTestConfig(
    val images: BigDataTestImageConfig = BigDataTestImageConfig(),
    val services: BigDataTestServiceConfig = BigDataTestServiceConfig(),
    val ports: BigDataTestPortConfig = BigDataTestPortConfig(),
    val containerLogs: BigDataTestContainerLogConfig = BigDataTestContainerLogConfig(),
) {
    fun merge(override: BigDataTestConfig): BigDataTestConfig =
        BigDataTestConfig(
            images = images.merge(override.images),
            services = services.merge(override.services),
            ports = ports.merge(override.ports),
            containerLogs = containerLogs.merge(override.containerLogs),
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
    val hdfsWeb: Int? = null,
    val hiveMetastore: Int? = null,
    val kafka: Int? = null,
    val schemaRegistry: Int? = null,
    val kafkaUi: Int? = null,
    val localStackS3: Int? = null,
    val fakeGcs: Int? = null,
) {
    fun merge(override: BigDataTestPortConfig): BigDataTestPortConfig =
        BigDataTestPortConfig(
            sameHostPorts = override.sameHostPorts ?: sameHostPorts,
            kerberosKdc = override.kerberosKdc ?: kerberosKdc,
            hdfsNameNode = override.hdfsNameNode ?: hdfsNameNode,
            hdfsWeb = override.hdfsWeb ?: hdfsWeb,
            hiveMetastore = override.hiveMetastore ?: hiveMetastore,
            kafka = override.kafka ?: kafka,
            schemaRegistry = override.schemaRegistry ?: schemaRegistry,
            kafkaUi = override.kafkaUi ?: kafkaUi,
            localStackS3 = override.localStackS3 ?: localStackS3,
            fakeGcs = override.fakeGcs ?: fakeGcs,
        )
}

internal data class BigDataTestContainerLogConfig(
    val mode: ContainerLogMode? = null,
    val directory: String? = null,
) {
    fun merge(override: BigDataTestContainerLogConfig): BigDataTestContainerLogConfig =
        BigDataTestContainerLogConfig(
            mode = override.mode ?: mode,
            directory = override.directory ?: directory,
        )
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
            ports = BigDataTestPortConfig(
                sameHostPorts = ports.boolean("sameHostPorts"),
                kerberosKdc = ports.int("kerberosKdc"),
                hdfsNameNode = ports.int("hdfsNameNode"),
                hdfsWeb = ports.int("hdfsWeb"),
                hiveMetastore = ports.int("hiveMetastore"),
                kafka = ports.int("kafka"),
                schemaRegistry = ports.int("schemaRegistry"),
                kafkaUi = ports.int("kafkaUi"),
                localStackS3 = ports.int("localStackS3"),
                fakeGcs = ports.int("fakeGcs"),
            ),
            containerLogs = BigDataTestContainerLogConfig(
                mode = containerLogs.string("mode")?.let { ContainerLogMode.valueOf(it.uppercase()) },
                directory = containerLogs.string("directory"),
            ),
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
        val knownTables = setOf("images", "services", "ports", "containerLogs")
        text.lineSequence().forEachIndexed { index, raw ->
            val line = stripComment(raw).trim()
            if (line.isEmpty()) return@forEachIndexed
            if (line.startsWith("[") && line.endsWith("]")) {
                currentTable = line.removePrefix("[").removeSuffix("]")
                if (currentTable in knownTables) {
                    tables.getOrPut(currentTable!!) { linkedMapOf() }
                }
                return@forEachIndexed
            }
            val table = currentTable ?: return@forEachIndexed
            if (table !in knownTables) return@forEachIndexed
            val separator = line.indexOf('=')
            require(separator > 0) { "TOML line ${index + 1}: expected key = value" }
            val key = line.substring(0, separator).trim()
            val value = line.substring(separator + 1).trim()
            tables.getValue(table)[key] = parseValue(value, index)
        }
        return tables
    }

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

    private sealed interface TomlValue {
        data class StringValue(val value: String) : TomlValue
        data class BooleanValue(val value: Boolean) : TomlValue
        data class IntValue(val value: Int) : TomlValue
    }
}
