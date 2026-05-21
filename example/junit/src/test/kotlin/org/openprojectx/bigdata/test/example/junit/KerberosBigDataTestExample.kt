package org.openprojectx.bigdata.test.example.junit

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.openprojectx.bigdata.test.core.BigDataTestKit
import org.openprojectx.bigdata.test.junit5.BigDataTest

@Disabled("Example only. Remove @Disabled to start the Kerberos-enabled Testcontainers stack.")
@BigDataTest(
    kerberos = true,
    hiveMetastore = true,
    hiveMetastoreKerberos = true,
    kafka = true,
    kafkaKerberos = true,
    schemaRegistry = true,
    schemaRegistryKerberos = true,
)
class KerberosBigDataTestExample {
    @Test
    fun exposesKerberosClientProperties(kit: BigDataTestKit) {
        kit.springProperties().forEach { (key, value) ->
            println("$key=$value")
        }
    }
}
