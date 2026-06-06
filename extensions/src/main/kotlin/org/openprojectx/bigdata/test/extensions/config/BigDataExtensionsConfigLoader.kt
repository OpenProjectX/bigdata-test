package org.openprojectx.bigdata.test.extensions.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.openprojectx.bigdata.test.extensions.core.BigDataExtension
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionProvider
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionResourceLoader
import org.openprojectx.bigdata.test.extensions.hadoop.S3JceksExtension
import org.openprojectx.bigdata.test.extensions.kerberos.KerberosMaterialExtension
import org.openprojectx.bigdata.test.extensions.kafka.KafkaAvroRecordSeed
import org.openprojectx.bigdata.test.extensions.kafka.KafkaAvroSeedExtension
import org.openprojectx.bigdata.test.extensions.kafka.KafkaAvroTopicSeed
import org.openprojectx.bigdata.test.extensions.objectstore.GcsBucketExtension
import org.openprojectx.bigdata.test.extensions.objectstore.S3BucketExtension
import org.openprojectx.bigdata.test.extensions.spark.SparkSqlPreparationExtensionProvider
import java.util.ServiceLoader

class BigDataExtensionsConfigLoader(
    private val resources: BigDataExtensionResourceLoader = BigDataExtensionResourceLoader(),
    providers: Iterable<BigDataExtensionProvider> = ServiceLoader.load(BigDataExtensionProvider::class.java),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val providers = (builtInProviders + providers).associateBy { it.type }

    fun load(locations: Iterable<String>): List<BigDataExtension> =
        locations.flatMap { load(it) }

    fun load(location: String): List<BigDataExtension> {
        val root = TomlConfigParser.parse(resources.readText(location))
        val extensions = mutableListOf<BigDataExtension>()
        root["s3Jceks"]?.jsonObject?.let { config ->
            if (config.boolean("enabled", default = true)) {
                extensions += S3JceksExtension(
                    id = config.string("id", "s3-jceks"),
                    hdfsDir = config.string("hdfsDir", "/bigdata-test/config"),
                    fileName = config.string("fileName", "s3.jceks"),
                    accessKeyAlias = config.string("accessKeyAlias", "fs.s3a.access.key"),
                    secretKeyAlias = config.string("secretKeyAlias", "fs.s3a.secret.key"),
                )
            }
        }
        root["kafkaAvro"]?.jsonObject?.let { config ->
            if (config.boolean("enabled", default = true)) {
                extensions += KafkaAvroSeedExtension(
                    id = config.string("id", "kafka-avro-seed"),
                    topics = config["topics"]?.jsonArray?.map { it.jsonObject.toKafkaAvroTopic() }.orEmpty(),
                )
            }
        }
        root["kerberosMaterial"]?.jsonObject?.let { config ->
            if (config.boolean("enabled", default = true)) {
                extensions += KerberosMaterialExtension(
                    id = config.string("id", "kerberos-material"),
                )
            }
        }
        root["s3Buckets"]?.jsonArray?.forEach { item ->
            val config = item.jsonObject
            if (config.boolean("enabled", default = true)) {
                val bucket = config.string("bucket")
                extensions += S3BucketExtension(
                    id = config.string("id", "s3-bucket-$bucket"),
                    bucket = bucket,
                )
            }
        }
        root["gcsBuckets"]?.jsonArray?.forEach { item ->
            val config = item.jsonObject
            if (config.boolean("enabled", default = true)) {
                val bucket = config.string("bucket")
                extensions += GcsBucketExtension(
                    id = config.string("id", "gcs-bucket-$bucket"),
                    bucket = bucket,
                    project = config.string("project", "bigdata-test"),
                )
            }
        }
        root["extensions"]?.jsonArray?.forEach { item ->
            val config = item.jsonObject
            val type = config.string("type")
            val provider = providers[type] ?: error("Unknown bigdata-test extension type '$type'")
            extensions += provider.create(config, resources)
        }
        return extensions
    }

    private fun JsonObject.toKafkaAvroTopic(): KafkaAvroTopicSeed {
        val inlineRecords = this["records"] as? JsonArray
        val resourceRecords = this["recordsResource"]?.jsonPrimitive?.contentOrNull
            ?.let { json.parseToJsonElement(resources.readText(it)).jsonArray }
        val records = (inlineRecords ?: resourceRecords ?: JsonArray(emptyList()))
            .map { record ->
                val obj = record.jsonObject
                KafkaAvroRecordSeed(
                    key = obj.string("key"),
                    value = obj["value"]?.jsonObject ?: error("Kafka Avro record requires object field 'value'"),
                )
            }
        return KafkaAvroTopicSeed(
            name = string("name"),
            schema = string("schema"),
            records = records,
            partitions = int("partitions", 1),
            replicationFactor = int("replicationFactor", 1).toShort(),
        )
    }

    private fun JsonObject.string(name: String): String =
        this[name]?.jsonPrimitive?.contentOrNull ?: error("Missing required extension config field '$name'")

    private fun JsonObject.string(name: String, default: String): String =
        this[name]?.jsonPrimitive?.contentOrNull ?: default

    private fun JsonObject.int(name: String, default: Int): Int =
        this[name]?.jsonPrimitive?.intOrNull ?: default

    private fun JsonObject.boolean(name: String, default: Boolean): Boolean =
        this[name]?.jsonPrimitive?.booleanOrNull ?: default

    private companion object {
        val builtInProviders = listOf<BigDataExtensionProvider>(
            SparkSqlPreparationExtensionProvider,
        )
    }
}

private object TomlConfigParser {
    fun parse(text: String): JsonObject {
        val root: MutableMap<String, Any> = linkedMapOf()
        var current: MutableMap<String, Any> = root
        logicalLines(text).forEach { logicalLine ->
            val index = logicalLine.line
            val line = logicalLine.text
            when {
                line.isEmpty() -> Unit
                line.startsWith("[[") && line.endsWith("]]") ->
                    current = resolveArrayTable(root, line.substring(2, line.length - 2).trim(), index)
                line.startsWith("[") && line.endsWith("]") ->
                    current = resolveTable(root, line.substring(1, line.length - 1).trim(), index)
                else -> {
                    val (key, value) = splitKeyValue(line, index)
                    current[key] = parseValue(value, index)
                }
            }
        }
        return toJson(root).jsonObject
    }

    private data class TomlLine(val text: String, val line: Int)

    private fun logicalLines(text: String): List<TomlLine> {
        val lines = mutableListOf<TomlLine>()
        val buffer = StringBuilder()
        var startLine = 0
        var depth = 0
        text.lineSequence().forEachIndexed { index, raw ->
            val line = stripComment(raw).trim()
            if (line.isEmpty() && buffer.isEmpty()) return@forEachIndexed
            if (buffer.isEmpty()) startLine = index else buffer.append(' ')
            buffer.append(line)
            depth += nestingDelta(line)
            if (depth == 0) {
                lines += TomlLine(buffer.toString().trim(), startLine)
                buffer.clear()
            }
        }
        require(buffer.isEmpty()) { "TOML line ${startLine + 1}: unclosed array or inline table" }
        return lines
    }

    private fun resolveTable(root: MutableMap<String, Any>, path: String, line: Int): MutableMap<String, Any> {
        var current = root
        parsePath(path, line).forEach { segment ->
            current = when (val existing = current[segment]) {
                null -> linkedMapOf<String, Any>().also { current[segment] = it }
                is MutableMap<*, *> -> existing.asStringMap(line)
                is MutableList<*> -> existing.lastOrNull().asStringMap(line)
                else -> error("TOML line ${line + 1}: '$segment' is not a table")
            }
        }
        return current
    }

    private fun resolveArrayTable(root: MutableMap<String, Any>, path: String, line: Int): MutableMap<String, Any> {
        val segments = parsePath(path, line)
        var current = root
        segments.dropLast(1).forEach { segment ->
            current = when (val existing = current[segment]) {
                null -> linkedMapOf<String, Any>().also { current[segment] = it }
                is MutableMap<*, *> -> existing.asStringMap(line)
                is MutableList<*> -> existing.lastOrNull().asStringMap(line)
                else -> error("TOML line ${line + 1}: '$segment' is not a table")
            }
        }
        val name = segments.last()
        val array = when (val existing = current[name]) {
            null -> mutableListOf<MutableMap<String, Any>>().also { current[name] = it }
            is MutableList<*> -> existing.asMutableList(line)
            else -> error("TOML line ${line + 1}: '$name' is not an array of tables")
        }
        return linkedMapOf<String, Any>().also { array += it }
    }

    private fun parseValue(value: String, line: Int): Any {
        val trimmed = value.trim()
        return when {
            trimmed.startsWith('"') && trimmed.endsWith('"') -> parseString(trimmed, line)
            trimmed.startsWith("{") && trimmed.endsWith("}") -> parseInlineTable(trimmed, line)
            trimmed.startsWith("[") && trimmed.endsWith("]") -> parseArray(trimmed, line)
            trimmed == "true" -> true
            trimmed == "false" -> false
            trimmed.toIntOrNull() != null -> trimmed.toInt()
            trimmed.toLongOrNull() != null -> trimmed.toLong()
            trimmed.toDoubleOrNull() != null -> trimmed.toDouble()
            else -> error("TOML line ${line + 1}: unsupported value '$trimmed'")
        }
    }

    private fun parseInlineTable(value: String, line: Int): Map<String, Any> {
        val body = value.substring(1, value.length - 1).trim()
        if (body.isEmpty()) return emptyMap()
        return splitTopLevel(body, ',').associate { item ->
            val (key, itemValue) = splitKeyValue(item, line)
            key to parseValue(itemValue, line)
        }
    }

    private fun parseArray(value: String, line: Int): List<Any> {
        val body = value.substring(1, value.length - 1).trim()
        if (body.isEmpty()) return emptyList()
        return splitTopLevel(body, ',').map { parseValue(it, line) }
    }

    private fun splitKeyValue(lineText: String, line: Int): Pair<String, String> {
        val index = lineText.indexOfTopLevel('=')
        require(index > 0) { "TOML line ${line + 1}: expected key = value" }
        return parseKey(lineText.substring(0, index).trim(), line) to lineText.substring(index + 1).trim()
    }

    private fun parseKey(key: String, line: Int): String {
        require(key.isNotEmpty()) { "TOML line ${line + 1}: empty key" }
        return if (key.startsWith('"') && key.endsWith('"')) parseString(key, line) else key
    }

    private fun parsePath(path: String, line: Int): List<String> =
        path.split('.').map { parseKey(it.trim(), line) }.also {
            require(it.isNotEmpty() && it.none(String::isEmpty)) { "TOML line ${line + 1}: invalid table path '$path'" }
        }

    private fun parseString(value: String, line: Int): String {
        val body = value.substring(1, value.length - 1)
        val result = StringBuilder()
        var escaped = false
        body.forEach { char ->
            if (escaped) {
                result.append(
                    when (char) {
                        'b' -> '\b'
                        't' -> '\t'
                        'n' -> '\n'
                        'f' -> '\u000C'
                        'r' -> '\r'
                        '"' -> '"'
                        '\\' -> '\\'
                        else -> error("TOML line ${line + 1}: unsupported escape '\\$char'")
                    },
                )
                escaped = false
            } else if (char == '\\') {
                escaped = true
            } else {
                result.append(char)
            }
        }
        require(!escaped) { "TOML line ${line + 1}: dangling string escape" }
        return result.toString()
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

    private fun String.indexOfTopLevel(target: Char): Int {
        var inString = false
        var escaped = false
        var depth = 0
        forEachIndexed { index, char ->
            when {
                escaped -> escaped = false
                char == '\\' && inString -> escaped = true
                char == '"' -> inString = !inString
                !inString && (char == '{' || char == '[') -> depth++
                !inString && (char == '}' || char == ']') -> depth--
                !inString && depth == 0 && char == target -> return index
            }
        }
        return -1
    }

    private fun splitTopLevel(value: String, delimiter: Char): List<String> {
        val items = mutableListOf<String>()
        var start = 0
        var inString = false
        var escaped = false
        var depth = 0
        value.forEachIndexed { index, char ->
            when {
                escaped -> escaped = false
                char == '\\' && inString -> escaped = true
                char == '"' -> inString = !inString
                !inString && (char == '{' || char == '[') -> depth++
                !inString && (char == '}' || char == ']') -> depth--
                !inString && depth == 0 && char == delimiter -> {
                    items += value.substring(start, index).trim()
                    start = index + 1
                }
            }
        }
        items += value.substring(start).trim()
        return items.filter { it.isNotEmpty() }
    }

    private fun nestingDelta(value: String): Int {
        var inString = false
        var escaped = false
        var delta = 0
        value.forEach { char ->
            when {
                escaped -> escaped = false
                char == '\\' && inString -> escaped = true
                char == '"' -> inString = !inString
                !inString && (char == '{' || char == '[') -> delta++
                !inString && (char == '}' || char == ']') -> delta--
            }
        }
        return delta
    }

    private fun toJson(value: Any): JsonElement = when (value) {
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Int -> JsonPrimitive(value)
        is Long -> JsonPrimitive(value)
        is Double -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(value.mapKeys { it.key as String }.mapValues { toJson(it.value ?: "null") })
        is List<*> -> JsonArray(value.map { toJson(it ?: "null") })
        else -> error("Unsupported TOML value type: ${value::class}")
    }

    @Suppress("UNCHECKED_CAST")
    private fun Any?.asStringMap(line: Int): MutableMap<String, Any> =
        this as? MutableMap<String, Any> ?: error("TOML line ${line + 1}: expected table")

    @Suppress("UNCHECKED_CAST")
    private fun Any?.asMutableList(line: Int): MutableList<MutableMap<String, Any>> =
        this as? MutableList<MutableMap<String, Any>> ?: error("TOML line ${line + 1}: expected array of tables")
}
