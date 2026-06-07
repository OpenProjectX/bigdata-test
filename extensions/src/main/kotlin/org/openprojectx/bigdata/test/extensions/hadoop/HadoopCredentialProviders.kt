package org.openprojectx.bigdata.test.extensions.hadoop

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.apache.hadoop.security.UserGroupInformation
import org.apache.hadoop.security.alias.CredentialProviderFactory
import java.net.URI

object HadoopCredentialProviders {
    fun hdfsJceksPath(configDir: String, fileName: String = "credentials.jceks"): String =
        "jceks://hdfs${configDir.trimEnd('/')}/$fileName"

    fun createHdfsJceks(
        hdfsUri: String,
        configDir: String,
        providerPath: String,
        credentials: Map<String, String>,
    ) {
        createHdfsJceks(
            hdfsProperties = mapOf("fs.defaultFS" to hdfsUri),
            configDir = configDir,
            providerPath = providerPath,
            credentials = credentials,
        )
    }

    fun createHdfsJceks(
        hdfsProperties: Map<String, String>,
        configDir: String,
        providerPath: String,
        credentials: Map<String, String>,
    ) {
        val conf = hdfsConfiguration(hdfsProperties)
        conf.set(CredentialProviderFactory.CREDENTIAL_PROVIDER_PATH, providerPath)
        loginFromKeytabIfNeeded(conf, hdfsProperties)
        val hdfsUri = requireNotNull(hdfsProperties["fs.defaultFS"]?.takeIf { it.isNotBlank() }) {
            "HDFS properties must include non-blank fs.defaultFS"
        }
        FileSystem.get(URI.create(hdfsUri), conf).use { fs -> fs.mkdirs(Path(configDir)) }

        val provider = CredentialProviderFactory.getProviders(conf).single()
        credentials.forEach { (alias, value) ->
            if (provider.getCredentialEntry(alias) != null) {
                provider.deleteCredentialEntry(alias)
            }
            provider.createCredentialEntry(alias, value.toCharArray())
        }
        provider.flush()
    }

    fun exists(hdfsUri: String, path: String): Boolean {
        val conf = hdfsConfiguration(mapOf("fs.defaultFS" to hdfsUri))
        return FileSystem.get(URI.create(hdfsUri), conf).use { fs -> fs.exists(Path(path)) }
    }

    private fun hdfsConfiguration(properties: Map<String, String>): Configuration =
        Configuration(false).apply {
            properties.forEach { (key, value) ->
                if (value.isNotBlank()) {
                    set(key, value)
                }
            }
        }

    private fun loginFromKeytabIfNeeded(conf: Configuration, properties: Map<String, String>) {
        if (!properties["hadoop.security.authentication"].equals("kerberos", ignoreCase = true)) return

        val krb5Conf = properties["java.security.krb5.conf.local"]
            ?: properties["java.security.krb5.conf"]
        krb5Conf?.let { System.setProperty("java.security.krb5.conf", it) }

        val principal = properties["bigdata.test.kerberos.client-principal"] ?: return
        val keytab = properties["bigdata.test.kerberos.client-keytab"] ?: return
        UserGroupInformation.setConfiguration(conf)
        UserGroupInformation.loginUserFromKeytab(principal, keytab)
    }
}
