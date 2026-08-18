package org.openprojectx.bigdata.test.extensions.hadoop

import org.openprojectx.bigdata.test.core.BigDataService
import org.openprojectx.bigdata.test.core.BigDataServiceId
import org.openprojectx.bigdata.test.extensions.core.BigDataExtension
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionContext
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionEvent

data class S3JceksExtension(
    override val id: String = "s3-jceks",
    val hdfsDir: String = "/bigdata-test/config",
    val fileName: String = "s3.jceks",
    val accessKeyAlias: String = "fs.s3a.access.key",
    val secretKeyAlias: String = "fs.s3a.secret.key",
    val aliases: Map<String, String> = defaultAliases,
    val additionalHdfsPaths: List<String> = emptyList(),
    override val instance: String = "default",
    val hdfsInstance: String = instance,
    val s3Instance: String = instance,
) : BigDataExtension {
    override val requiredServices: Set<BigDataService> = setOf(BigDataService.HDFS, BigDataService.S3)
    override val requiredServiceInstances: Set<BigDataServiceId> = setOf(
        BigDataServiceId(BigDataService.HDFS, hdfsInstance),
        BigDataServiceId(BigDataService.S3, s3Instance),
    )
    override val events: Set<BigDataExtensionEvent> = setOf(BigDataExtensionEvent.AFTER_KIT_START)

    override fun onEvent(event: BigDataExtensionEvent, context: BigDataExtensionContext) {
        val hdfs = if (hdfsInstance == context.instance) {
            context.endpoint(BigDataService.HDFS)
        } else {
            context.kit.endpoint(BigDataService.HDFS, hdfsInstance)
        }
        val s3 = if (s3Instance == context.instance) {
            context.endpoint(BigDataService.S3)
        } else {
            context.kit.endpoint(BigDataService.S3, s3Instance)
        }
        val kerberosProperties = if (hdfsInstance == "default") {
            context.kit.endpoints()[BigDataService.KERBEROS]?.properties.orEmpty()
        } else {
            context.kit.endpoints(BigDataService.KERBEROS)[hdfsInstance]?.properties.orEmpty()
        }
        val credentials = mapOf(
            accessKeyAlias to s3.property("aws.accessKeyId"),
            secretKeyAlias to s3.property("aws.secretAccessKey"),
        ) + aliases
        val hdfsPaths = (listOf("${hdfsDir.trimEnd('/')}/$fileName") + additionalHdfsPaths).distinct()
        val providerPaths = hdfsPaths.map { hdfsJceksProviderPath(it) }
        hdfsPaths.zip(providerPaths).forEach { (hdfsPath, providerPath) ->
            HadoopCredentialProviders.createHdfsJceks(
                hdfsProperties = hdfs.properties + kerberosProperties,
                configDir = hdfsPath.substringBeforeLast('/', missingDelimiterValue = "/").ifBlank { "/" },
                providerPath = providerPath,
                credentials = credentials,
            )
        }
        context.putOutput("$id.credential-provider.path", providerPaths.first())
        context.putOutput("$id.credential-provider.paths", providerPaths.joinToString(","))
        context.putOutput("$id.hdfs.path", hdfsPaths.first())
        context.putOutput("$id.hdfs.paths", hdfsPaths.joinToString(","))
        providerPaths.forEachIndexed { index, providerPath ->
            context.putOutput("$id.credential-provider.path.$index", providerPath)
        }
        hdfsPaths.forEachIndexed { index, hdfsPath ->
            context.putOutput("$id.hdfs.path.$index", hdfsPath)
        }
    }

    companion object {
        fun hdfsJceksProviderPath(hdfsPath: String): String =
            "jceks://hdfs/${hdfsPath.trimStart('/')}"

        val defaultAliases: Map<String, String> = mapOf(
            // Hadoop 3.4 S3A loads these through S3AUtils.lookupPassword().
            // Empty aliases keep credential-provider-only configurations from
            // failing when encryption is not enabled.
            "fs.s3a.encryption.algorithm" to "",
            "fs.s3a.server-side-encryption-algorithm" to "",
            "fs.s3a.encryption.key" to "",
            "fs.s3a.server-side-encryption.key" to "",
        )
    }
}
