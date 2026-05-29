package org.openprojectx.bigdata.test.extensions.hadoop

import org.openprojectx.bigdata.test.core.BigDataService
import org.openprojectx.bigdata.test.extensions.core.BigDataExtension
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionContext
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionEvent

data class S3JceksExtension(
    override val id: String = "s3-jceks",
    val hdfsDir: String = "/bigdata-test/config",
    val fileName: String = "s3.jceks",
    val accessKeyAlias: String = "fs.s3a.access.key",
    val secretKeyAlias: String = "fs.s3a.secret.key",
) : BigDataExtension {
    override val requiredServices: Set<BigDataService> = setOf(BigDataService.HDFS, BigDataService.LOCALSTACK_S3)
    override val events: Set<BigDataExtensionEvent> = setOf(BigDataExtensionEvent.AFTER_KIT_START)

    override fun onEvent(event: BigDataExtensionEvent, context: BigDataExtensionContext) {
        val hdfs = context.endpoint(BigDataService.HDFS)
        val s3 = context.endpoint(BigDataService.LOCALSTACK_S3)
        val providerPath = HadoopCredentialProviders.hdfsJceksPath(hdfsDir, fileName)
        HadoopCredentialProviders.createHdfsJceks(
            hdfsUri = hdfs.property("fs.defaultFS"),
            configDir = hdfsDir,
            providerPath = providerPath,
            credentials = mapOf(
                accessKeyAlias to s3.property("aws.accessKeyId"),
                secretKeyAlias to s3.property("aws.secretAccessKey"),
            ),
        )
        context.putOutput("$id.credential-provider.path", providerPath)
        context.putOutput("$id.hdfs.path", "${hdfsDir.trimEnd('/')}/$fileName")
    }
}
