package org.openprojectx.bigdata.test.example.spring

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.apache.hadoop.security.UserGroupInformation
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import software.amazon.awssdk.services.s3.model.S3Exception
import java.net.URI

@SpringBootApplication
@EnableConfigurationProperties(S3aStorageProperties::class)
class BigDataTestExampleApplication {
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "example.s3", name = ["enabled"], havingValue = "true")
    fun s3aFileSystem(properties: S3aStorageProperties): FileSystem {
        val configuration = Configuration(false)
        properties.hadoop.forEach { (key, value) -> configuration.set(key, value) }
        properties.loginFromKeytabIfNeeded(configuration)
        configuration.setIfUnset("fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem")
        configuration.setIfUnset("fs.s3a.path.style.access", "true")
        configuration.setIfUnset("fs.s3a.change.detection.mode", "none")
        return FileSystem.get(URI.create("s3a://${properties.bucket}"), configuration)
    }

    @Bean
    @ConditionalOnProperty(prefix = "example.s3", name = ["enabled"], havingValue = "true")
    fun initializeStorage(fileSystem: FileSystem, properties: S3aStorageProperties): ApplicationRunner =
        ApplicationRunner {
            if (properties.createBucketOnStartup) {
                properties.createBucketIfMissing()
            }
            fileSystem.mkdirs(properties.rootPath())
        }
}

@ConfigurationProperties("example.s3")
data class S3aStorageProperties(
    var enabled: Boolean = false,
    var bucket: String = "spring-example",
    var prefix: String = "objects",
    var createBucketOnStartup: Boolean = false,
    var accessKey: String = "test",
    var secretKey: String = "test",
    var hadoop: MutableMap<String, String> = linkedMapOf(),
) {
    fun rootPath(): Path = Path("s3a://$bucket/${prefix.trim('/')}")

    fun objectPath(key: String): Path {
        val normalizedKey = key.trim('/')
        require(normalizedKey.isNotBlank()) { "S3 object key must not be blank" }
        return Path(rootPath(), normalizedKey)
    }

    fun createBucketIfMissing() {
        val endpoint = hadoop["fs.s3a.endpoint"] ?: return
        val region = hadoop["fs.s3a.endpoint.region"] ?: "us-east-1"
        S3Client.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
            .forcePathStyle(true)
            .build()
            .use { client ->
                try {
                    client.headBucket { it.bucket(bucket) }
                } catch (_: NoSuchBucketException) {
                    client.createBucket(CreateBucketRequest.builder().bucket(bucket).build())
                } catch (error: S3Exception) {
                    if (error.statusCode() == 404) {
                        client.createBucket(CreateBucketRequest.builder().bucket(bucket).build())
                    } else {
                        throw error
                    }
                }
            }
    }

    fun loginFromKeytabIfNeeded(configuration: Configuration) {
        if (!hadoop["hadoop.security.authentication"].equals("kerberos", ignoreCase = true)) return

        hadoop["java.security.krb5.conf"]?.let { System.setProperty("java.security.krb5.conf", it) }
        val principal = hadoop["bigdata.test.kerberos.client-principal"] ?: return
        val keytab = hadoop["bigdata.test.kerberos.client-keytab"] ?: return
        UserGroupInformation.setConfiguration(configuration)
        UserGroupInformation.loginUserFromKeytab(principal, keytab)
    }
}

@RestController
@ConditionalOnProperty(prefix = "example.s3", name = ["enabled"], havingValue = "true")
@RequestMapping("/api/s3", produces = [MediaType.APPLICATION_JSON_VALUE])
class S3ObjectController(
    private val fileSystem: FileSystem,
    private val properties: S3aStorageProperties,
) {
    @GetMapping("/objects")
    fun list(): List<S3ObjectSummary> {
        val root = properties.rootPath()
        if (!fileSystem.exists(root)) return emptyList()
        return fileSystem.listStatus(root)
            .filter { it.isFile }
            .map { status ->
                S3ObjectSummary(
                    key = status.path.name,
                    size = status.len,
                )
            }
    }

    @PutMapping("/objects/{*key}", consumes = [MediaType.TEXT_PLAIN_VALUE])
    fun put(
        @PathVariable key: String,
        @RequestBody content: String,
    ): S3ObjectSummary {
        val path = properties.objectPath(key)
        fileSystem.mkdirs(path.parent)
        fileSystem.create(path, true).bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(content)
        }
        return S3ObjectSummary(key = path.name, size = fileSystem.getFileStatus(path).len)
    }

    @GetMapping("/objects/{*key}", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun get(@PathVariable key: String): String =
        fileSystem.open(properties.objectPath(key)).bufferedReader(Charsets.UTF_8).use { it.readText() }

    @DeleteMapping("/objects/{*key}")
    fun delete(@PathVariable key: String): Map<String, Boolean> =
        mapOf("deleted" to fileSystem.delete(properties.objectPath(key), false))
}

data class S3ObjectSummary(
    val key: String,
    val size: Long,
)

fun main(args: Array<String>) {
    runApplication<BigDataTestExampleApplication>(*args)
}
