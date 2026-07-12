package org.openprojectx.bigdata.test.example.junit

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.openprojectx.bigdata.test.core.BigDataService
import org.openprojectx.bigdata.test.core.BigDataTestKit
import org.openprojectx.bigdata.test.junit5.BigDataTest

//@Disabled("Example only. Remove @Disabled to start the configured Testcontainers stack.")
@BigDataTest(
    hiveMetastore = true,
    kafka = true,
    schemaRegistry = true,
    s3 = true,
)
class PlainBigDataTestExample {
    @Test
    fun exposesEndpoints(kit: BigDataTestKit) {
        val metastoreUri = kit.endpoint(BigDataService.HIVE_METASTORE).property("hive.metastore.uris")
        val bootstrapServers = kit.endpoint(BigDataService.KAFKA).property("bootstrap.servers")
        val schemaRegistryUrl = kit.endpoint(BigDataService.SCHEMA_REGISTRY).property("schema.registry.url")

        println("metastore=$metastoreUri")
        println("kafka=$bootstrapServers")
        println("schemaRegistry=$schemaRegistryUrl")
    }
}
