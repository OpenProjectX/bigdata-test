package org.openprojectx.bigdata.test.extensions.objectstore

import com.google.cloud.NoCredentials
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.BucketInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageException
import com.google.cloud.storage.StorageOptions
import org.openprojectx.bigdata.test.core.BigDataService
import org.openprojectx.bigdata.test.extensions.core.BigDataExtension
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionContext
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionEvent
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

data class ObjectStoreUploadSource(
    val source: String,
    val key: String? = null,
    val prefix: String = "",
    val recursive: Boolean = true,
    val contentType: String? = null,
)

data class S3UploadExtension(
    override val id: String,
    val bucket: String,
    val prefix: String = "",
    val createBucket: Boolean = true,
    val sources: List<ObjectStoreUploadSource>,
) : BigDataExtension {
    override val requiredServices: Set<BigDataService> = setOf(BigDataService.LOCALSTACK_S3)
    override val events: Set<BigDataExtensionEvent> = setOf(BigDataExtensionEvent.AFTER_KIT_START)

    override fun onEvent(event: BigDataExtensionEvent, context: BigDataExtensionContext) {
        val endpoint = context.endpoint(BigDataService.LOCALSTACK_S3)
        val client = S3Client.builder()
            .endpointOverride(URI.create(endpoint.property("aws.endpoint-url.s3")))
            .region(Region.of(endpoint.properties["aws.region"] ?: "us-east-1"))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        endpoint.properties["aws.accessKeyId"] ?: "test",
                        endpoint.properties["aws.secretAccessKey"] ?: "test",
                    ),
                ),
            )
            .forcePathStyle(true)
            .build()
        client.use { s3 ->
            if (createBucket) {
                createS3Bucket(s3, bucket)
            }
            val uploaded = expandSources(context).onEach { objectToUpload ->
                putS3Object(s3, bucket, objectToUpload)
            }
            context.putOutput("$id.bucket", bucket)
            context.putOutput("$id.s3a.uri", "s3a://$bucket/${normalizePrefix(prefix)}".trimEnd('/'))
            context.putOutput("$id.uploaded-count", uploaded.size.toString())
            uploaded.forEachIndexed { index, item -> context.putOutput("$id.uploaded.$index.key", item.key) }
        }
    }

    private fun expandSources(context: BigDataExtensionContext): List<ObjectToUpload> =
        expandUploadSources(context, sources, prefix)
}

data class GcsUploadExtension(
    override val id: String,
    val bucket: String,
    val prefix: String = "",
    val project: String = "bigdata-test",
    val createBucket: Boolean = true,
    val sources: List<ObjectStoreUploadSource>,
) : BigDataExtension {
    override val requiredServices: Set<BigDataService> = setOf(BigDataService.FAKE_GCS)
    override val events: Set<BigDataExtensionEvent> = setOf(BigDataExtensionEvent.AFTER_KIT_START)

    override fun onEvent(event: BigDataExtensionEvent, context: BigDataExtensionContext) {
        val endpoint = context.endpoint(BigDataService.FAKE_GCS)
        val storage = StorageOptions.newBuilder()
            .setHost(endpoint.property("google.cloud.storage.host").trimEnd('/'))
            .setProjectId(project)
            .setCredentials(NoCredentials.getInstance())
            .build()
            .service
        if (createBucket) {
            createGcsBucket(storage, bucket)
        }
        val uploaded = expandSources(context).onEach { objectToUpload ->
            putGcsObject(storage, bucket, objectToUpload)
        }
        context.putOutput("$id.bucket", bucket)
        context.putOutput("$id.gs.uri", "gs://$bucket/${normalizePrefix(prefix)}".trimEnd('/'))
        context.putOutput("$id.uploaded-count", uploaded.size.toString())
        uploaded.forEachIndexed { index, item -> context.putOutput("$id.uploaded.$index.key", item.key) }
    }

    private fun expandSources(context: BigDataExtensionContext): List<ObjectToUpload> =
        expandUploadSources(context, sources, prefix)
}

private data class ObjectToUpload(
    val key: String,
    val bytes: ByteArray,
    val contentType: String?,
)

private fun expandUploadSources(
    context: BigDataExtensionContext,
    sources: List<ObjectStoreUploadSource>,
    extensionPrefix: String,
): List<ObjectToUpload> {
    require(sources.isNotEmpty()) { "Object-store upload extension requires at least one source" }
    return sources.flatMap { source ->
        val path = source.localPathOrNull()
        if (path != null && Files.isDirectory(path)) {
            expandDirectory(context, source, path, extensionPrefix)
        } else {
            val key = source.key ?: joinObjectKey(extensionPrefix, source.prefix, source.source.substringAfterLast('/'))
            listOf(ObjectToUpload(key = key, bytes = context.resources.readBytes(source.source), contentType = source.contentType))
        }
    }
}

private fun expandDirectory(
    context: BigDataExtensionContext,
    source: ObjectStoreUploadSource,
    directory: Path,
    extensionPrefix: String,
): List<ObjectToUpload> {
    val stream = if (source.recursive) Files.walk(directory) else Files.list(directory)
    stream.use { paths ->
        return paths
            .filter(Files::isRegularFile)
            .sorted()
            .map { file ->
                val relative = directory.relativize(file).joinToString("/")
                ObjectToUpload(
                    key = joinObjectKey(extensionPrefix, source.prefix, relative.ifBlank { file.name }),
                    bytes = Files.readAllBytes(file),
                    contentType = source.contentType,
                )
            }
            .toList()
    }
}

private fun ObjectStoreUploadSource.localPathOrNull(): Path? =
    when {
        source.startsWith("classpath:") -> null
        source.startsWith("file:") -> Path.of(source.removePrefix("file:"))
        else -> Path.of(source)
    }

private fun createS3Bucket(client: S3Client, bucket: String) {
    try {
        client.createBucket(CreateBucketRequest.builder().bucket(bucket).build())
    } catch (_: BucketAlreadyOwnedByYouException) {
    } catch (_: BucketAlreadyExistsException) {
    }
}

private fun createGcsBucket(storage: Storage, bucket: String) {
    try {
        storage.create(BucketInfo.of(bucket))
    } catch (error: StorageException) {
        if (error.code != 409) throw error
    }
}

private fun putS3Object(client: S3Client, bucket: String, item: ObjectToUpload) {
    val request = PutObjectRequest.builder()
        .bucket(bucket)
        .key(item.key)
        .contentType(item.contentType ?: "application/octet-stream")
        .build()
    client.putObject(request, RequestBody.fromBytes(item.bytes))
}

private fun putGcsObject(storage: Storage, bucket: String, item: ObjectToUpload) {
    val blob = BlobInfo.newBuilder(bucket, item.key)
        .setContentType(item.contentType ?: "application/octet-stream")
        .build()
    storage.create(blob, item.bytes)
}

private fun joinObjectKey(vararg parts: String): String =
    parts.asSequence()
        .map(::normalizePrefix)
        .filter(String::isNotBlank)
        .joinToString("/")

private fun normalizePrefix(value: String): String = value.trim().trim('/')
