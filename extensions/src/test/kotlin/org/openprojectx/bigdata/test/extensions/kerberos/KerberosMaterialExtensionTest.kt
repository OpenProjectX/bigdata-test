package org.openprojectx.bigdata.test.extensions.kerberos

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.openprojectx.bigdata.test.extensions.config.BigDataExtensionsConfigLoader

class KerberosMaterialExtensionTest {
    @Test
    fun `loads local kerberos material copy paths from toml`() {
        val extension = BigDataExtensionsConfigLoader().load(
            """
            [kerberosMaterial]
            enabled = true
            id = "kerberos-copy"
            localClientKeytabCopyPaths = [
              "build/app/client.keytab",
              "build/worker/client.keytab"
            ]
            localKrb5ConfCopyPaths = [
              "build/app/krb5.conf",
              "build/worker/krb5.conf"
            ]
            """.trimIndent(),
        ).single() as KerberosMaterialExtension

        assertEquals("kerberos-copy", extension.id)
        assertEquals(
            listOf("build/app/client.keytab", "build/worker/client.keytab"),
            extension.localClientKeytabCopyPaths,
        )
        assertEquals(
            listOf("build/app/krb5.conf", "build/worker/krb5.conf"),
            extension.localKrb5ConfCopyPaths,
        )
    }
}
