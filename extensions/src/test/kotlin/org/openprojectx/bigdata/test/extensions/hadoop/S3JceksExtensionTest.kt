package org.openprojectx.bigdata.test.extensions.hadoop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.openprojectx.bigdata.test.extensions.config.BigDataExtensionsConfigLoader

class S3JceksExtensionTest {
    @Test
    fun `loads default and custom jceks aliases from toml`() {
        val extension = BigDataExtensionsConfigLoader().load(
            """
            [s3Jceks]
            hdfsDir = "/config"
            fileName = "s3.jceks"
            additionalHdfsPaths = [
              "/config/app/s3.jceks",
              "/config/worker/s3.jceks"
            ]

            [s3Jceks.aliases]
            "fs.s3a.encryption.algorithm" = "SSE-S3"
            "fs.s3a.encryption.key" = ""
            "custom.alias" = "custom-value"
            """.trimIndent(),
        ).single() as S3JceksExtension

        assertEquals("/config", extension.hdfsDir)
        assertEquals("s3.jceks", extension.fileName)
        assertEquals(listOf("/config/app/s3.jceks", "/config/worker/s3.jceks"), extension.additionalHdfsPaths)
        assertEquals("SSE-S3", extension.aliases["fs.s3a.encryption.algorithm"])
        assertEquals("", extension.aliases["fs.s3a.server-side-encryption-algorithm"])
        assertEquals("", extension.aliases["fs.s3a.server-side-encryption.key"])
        assertEquals("custom-value", extension.aliases["custom.alias"])
    }

    @Test
    fun `loads unquoted dotted jceks aliases from toml`() {
        val extension = BigDataExtensionsConfigLoader().load(
            """
            [s3Jceks.aliases]
            fs.s3a.encryption.algorithm = "SSE-KMS"
            """.trimIndent(),
        ).single() as S3JceksExtension

        assertEquals("SSE-KMS", extension.aliases["fs.s3a.encryption.algorithm"])
    }

    @Test
    fun `default jceks aliases include s3a encryption options`() {
        val defaults = S3JceksExtension.defaultAliases

        assertTrue(defaults.containsKey("fs.s3a.encryption.algorithm"))
        assertTrue(defaults.containsKey("fs.s3a.server-side-encryption-algorithm"))
        assertTrue(defaults.containsKey("fs.s3a.encryption.key"))
        assertTrue(defaults.containsKey("fs.s3a.server-side-encryption.key"))
    }

    @Test
    fun `builds hdfs jceks provider path from hdfs path`() {
        assertEquals(
            "jceks://hdfs/bigdata-test/config/s3.jceks",
            S3JceksExtension.hdfsJceksProviderPath("/bigdata-test/config/s3.jceks"),
        )
    }
}
