package org.openprojectx.bigdata.test.junit5

import java.nio.file.Files
import java.nio.file.Path

internal data class BigDataTestImageConfig(
    val kerberos: String? = null,
    val hdfs: String? = null,
    val hiveMetastore: String? = null,
    val hiveMetastoreApache: String? = null,
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
            hiveMetastoreApache = override.hiveMetastoreApache ?: hiveMetastoreApache,
            kafka = override.kafka ?: kafka,
            schemaRegistry = override.schemaRegistry ?: schemaRegistry,
            kafkaUi = override.kafkaUi ?: kafkaUi,
            localStackS3 = override.localStackS3 ?: localStackS3,
            fakeGcs = override.fakeGcs ?: fakeGcs,
        )
}

internal class BigDataTestConfigLoader(
    private val classLoader: ClassLoader,
) {
    fun load(locations: Iterable<String>): BigDataTestImageConfig =
        locations.fold(BigDataTestImageConfig()) { current, location ->
            current.merge(load(location))
        }

    private fun load(location: String): BigDataTestImageConfig {
        val text = readText(location)
        val images = parseImagesTable(text)
        return BigDataTestImageConfig(
            kerberos = images["kerberos"],
            hdfs = images["hdfs"],
            hiveMetastore = images["hiveMetastore"],
            hiveMetastoreApache = images["hiveMetastoreApache"],
            kafka = images["kafka"],
            schemaRegistry = images["schemaRegistry"],
            kafkaUi = images["kafkaUi"],
            localStackS3 = images["localStackS3"],
            fakeGcs = images["fakeGcs"],
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

    private fun parseImagesTable(text: String): Map<String, String> {
        val values = linkedMapOf<String, String>()
        var inImages = false
        text.lineSequence().forEachIndexed { index, raw ->
            val line = stripComment(raw).trim()
            if (line.isEmpty()) return@forEachIndexed
            if (line.startsWith("[") && line.endsWith("]")) {
                inImages = line == "[images]"
                return@forEachIndexed
            }
            if (!inImages) return@forEachIndexed
            val separator = line.indexOf('=')
            require(separator > 0) { "TOML line ${index + 1}: expected key = value in [images]" }
            val key = line.substring(0, separator).trim()
            val value = line.substring(separator + 1).trim()
            values[key] = parseString(value, index)
        }
        return values
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

    private fun parseString(value: String, line: Int): String {
        require(value.startsWith('"') && value.endsWith('"')) {
            "TOML line ${line + 1}: [images] values must be quoted strings"
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
}
