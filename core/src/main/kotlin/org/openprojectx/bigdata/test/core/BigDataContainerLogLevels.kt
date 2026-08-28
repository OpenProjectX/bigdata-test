package org.openprojectx.bigdata.test.core

object BigDataContainerLogLevels {
    @JvmStatic
    fun environment(service: BigDataService, level: String): Map<String, String> {
        val normalized = normalize(level)
        return when (service) {
            BigDataService.KERBEROS -> mapOf(
                "KERBY_DEBUG" to (normalized == "DEBUG" || normalized == "TRACE").toString(),
                "KERBY_LOG_LEVEL" to normalized,
            )
            BigDataService.HDFS -> mapOf(
                "HADOOP_ROOT_LOGGER" to "$normalized,console",
                "HADOOP_LOGLEVEL" to normalized,
            )
            BigDataService.HIVE_METASTORE -> mapOf(
                "HIVE_LOG_LEVEL" to normalized,
                "HMS_LOG_LEVEL" to normalized,
            )
            BigDataService.KAFKA -> mapOf(
                "KAFKA_LOG4J_ROOT_LOGLEVEL" to normalized,
                "KAFKA_TOOLS_LOG4J_LOGLEVEL" to normalized,
            )
            BigDataService.SCHEMA_REGISTRY -> mapOf(
                "SCHEMA_REGISTRY_LOG4J_ROOT_LOGLEVEL" to normalized,
            )
            BigDataService.KAFKA_UI -> mapOf(
                "LOGGING_LEVEL_ROOT" to normalized,
            )
            BigDataService.S3 -> mapOf(
                "QUARKUS_LOG_LEVEL" to normalized,
            )
            BigDataService.FAKE_GCS -> mapOf(
                "LOG_LEVEL" to normalized,
            )
            BigDataService.ICEBERG_REST_CATALOG -> error(
                "The Gravitino Iceberg REST image does not expose an environment-based log-level setting; " +
                    "supply a log4j2.properties file with container customization instead",
            )
            BigDataService.TRINO -> error(
                "The official Trino image configures logging through /etc/trino/log.properties; " +
                    "supply that file with container customization instead",
            )
        }
    }

    private fun normalize(level: String): String {
        val normalized = level.trim().uppercase()
        require(normalized in SUPPORTED_LEVELS) {
            "Container log level must be one of ${SUPPORTED_LEVELS.joinToString()}, got '$level'"
        }
        return normalized
    }

    private val SUPPORTED_LEVELS = setOf("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "OFF")
}
