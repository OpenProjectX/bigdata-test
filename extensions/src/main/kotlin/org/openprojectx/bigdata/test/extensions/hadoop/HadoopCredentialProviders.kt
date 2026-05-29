package org.openprojectx.bigdata.test.extensions.hadoop

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
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
        val conf = Configuration(false)
        conf.set("fs.defaultFS", hdfsUri)
        conf.set(CredentialProviderFactory.CREDENTIAL_PROVIDER_PATH, providerPath)
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
        val conf = Configuration(false)
        conf.set("fs.defaultFS", hdfsUri)
        return FileSystem.get(URI.create(hdfsUri), conf).use { fs -> fs.exists(Path(path)) }
    }
}
