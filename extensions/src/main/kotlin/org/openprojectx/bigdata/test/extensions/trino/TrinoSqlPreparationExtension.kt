package org.openprojectx.bigdata.test.extensions.trino

import java.time.Duration
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.openprojectx.bigdata.test.core.BigDataService
import org.openprojectx.bigdata.test.extensions.core.BigDataExtension
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionContext
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionEvent
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionProvider
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionResourceLoader

class TrinoSqlPreparationExtension(
    override val id: String = "trino-sql-prep",
    private val user: String = "bigdata-test",
    private val catalog: String? = null,
    private val schema: String? = null,
    private val source: String = "bigdata-test",
    private val queryTimeoutSeconds: Int = 60,
    private val sql: List<TrinoSqlPreparationStatement> = emptyList(),
    override val instance: String = "default",
) : BigDataExtension {
    override val requiredServices: Set<BigDataService> = setOf(BigDataService.TRINO)
    override val events: Set<BigDataExtensionEvent> = setOf(BigDataExtensionEvent.AFTER_KIT_START)

    override fun onEvent(event: BigDataExtensionEvent, context: BigDataExtensionContext) {
        if (event !in events) return
        require(queryTimeoutSeconds > 0) { "Trino SQL queryTimeoutSeconds must be positive" }
        val endpoint = context.endpoint(BigDataService.TRINO)
        val client = TrinoSqlClient(
            jdbcUrl = endpoint.property("trino.jdbc.url"),
            user = user,
            catalog = catalog,
            schema = schema,
            source = source,
            queryTimeout = Duration.ofSeconds(queryTimeoutSeconds.toLong()),
        )

        var count = 0
        client.use {
            sql.forEach { statement ->
                splitSql(statement.resolve(context.resources)).forEach { executable ->
                    client.execute(executable)
                    count++
                }
            }
        }
        context.putOutput("$id.engine", "trino-jdbc")
        context.putOutput("$id.executed-statements", count.toString())
    }

    private fun splitSql(text: String): List<String> =
        text.splitToSequence(';')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()
}

data class TrinoSqlPreparationStatement(
    val statement: String? = null,
    val resource: String? = null,
) {
    fun resolve(resources: BigDataExtensionResourceLoader): String =
        statement ?: resource?.let(resources::readText) ?: ""
}

object TrinoSqlPreparationExtensionProvider : BigDataExtensionProvider {
    override val type: String = "trino-sql-prep"

    override fun create(config: JsonObject, resources: BigDataExtensionResourceLoader): BigDataExtension =
        TrinoSqlPreparationExtension(
            id = config.string("id", "trino-sql-prep"),
            instance = config.string("instance", "default"),
            user = config.string("user", "bigdata-test"),
            catalog = config.optionalString("catalog"),
            schema = config.optionalString("schema"),
            source = config.string("source", "bigdata-test"),
            queryTimeoutSeconds = config.int("queryTimeoutSeconds", 60),
            sql = config.sqlStatements(),
        )

    private fun JsonObject.sqlStatements(): List<TrinoSqlPreparationStatement> {
        val statements = (this["statements"] as? JsonArray)
            ?.jsonArray
            ?.map { TrinoSqlPreparationStatement(statement = it.jsonPrimitive.contentOrNull.orEmpty()) }
            .orEmpty()
        val scripts = (this["scripts"] as? JsonArray)
            ?.jsonArray
            ?.map { TrinoSqlPreparationStatement(resource = it.jsonPrimitive.contentOrNull.orEmpty()) }
            .orEmpty()
        return statements + scripts
    }

    private fun JsonObject.string(name: String, default: String): String =
        this[name]?.jsonPrimitive?.contentOrNull ?: default

    private fun JsonObject.optionalString(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.int(name: String, default: Int): Int =
        this[name]?.jsonPrimitive?.intOrNull ?: default
}
