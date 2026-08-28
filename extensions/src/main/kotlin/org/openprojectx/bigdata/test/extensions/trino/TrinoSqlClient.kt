package org.openprojectx.bigdata.test.extensions.trino

import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.time.Duration
import java.util.Properties

data class TrinoSqlColumn(
    val name: String,
    val type: String,
)

data class TrinoSqlResult(
    val columns: List<TrinoSqlColumn>,
    val rows: List<List<Any?>>,
    val updateCount: Long,
)

class TrinoSqlClient @JvmOverloads constructor(
    jdbcUrl: String,
    user: String = "bigdata-test",
    catalog: String? = null,
    schema: String? = null,
    source: String = "bigdata-test",
    private val queryTimeout: Duration = Duration.ofSeconds(60),
) : AutoCloseable {
    private val connection: Connection

    init {
        require(jdbcUrl.startsWith("jdbc:trino:")) { "Trino JDBC URL must start with 'jdbc:trino:'" }
        require(!queryTimeout.isZero && !queryTimeout.isNegative) { "Trino query timeout must be positive" }
        val properties = Properties().apply {
            setProperty("user", user)
            setProperty("source", source)
            catalog?.takeIf(String::isNotBlank)?.let { setProperty("catalog", it) }
            schema?.takeIf(String::isNotBlank)?.let { setProperty("schema", it) }
        }
        connection = DriverManager.getConnection(jdbcUrl, properties)
    }

    fun execute(sql: String): TrinoSqlResult {
        require(sql.isNotBlank()) { "Trino SQL must not be blank" }
        connection.createStatement().use { statement ->
            statement.queryTimeout = queryTimeout.seconds.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val hasResultSet = statement.execute(sql)
            if (!hasResultSet) {
                return TrinoSqlResult(
                    columns = emptyList(),
                    rows = emptyList(),
                    updateCount = statement.largeUpdateCount,
                )
            }
            statement.resultSet.use { resultSet ->
                return resultSet.toResult()
            }
        }
    }

    override fun close() {
        connection.close()
    }

    private fun ResultSet.toResult(): TrinoSqlResult {
        val metadata = metaData
        val columns = (1..metadata.columnCount).map { index ->
            TrinoSqlColumn(
                name = metadata.getColumnLabel(index),
                type = metadata.getColumnTypeName(index),
            )
        }
        val rows = buildList {
            while (next()) {
                add((1..metadata.columnCount).map { index -> getObject(index) })
            }
        }
        return TrinoSqlResult(columns = columns, rows = rows, updateCount = -1)
    }
}
