package org.openprojectx.bigdata.test.extensions.objectstore

import org.openprojectx.bigdata.test.core.BigDataService
import org.openprojectx.bigdata.test.extensions.core.BigDataExtension
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionContext
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionEvent
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

data class S3BucketExtension(
    override val id: String,
    val bucket: String,
) : BigDataExtension {
    override val requiredServices: Set<BigDataService> = setOf(BigDataService.S3)
    override val events: Set<BigDataExtensionEvent> = setOf(BigDataExtensionEvent.AFTER_KIT_START)

    override fun onEvent(event: BigDataExtensionEvent, context: BigDataExtensionContext) {
        val endpoint = context.endpoint(BigDataService.S3).property("aws.endpoint-url.s3")
        val request = HttpRequest.newBuilder(URI.create("${endpoint.trimEnd('/')}/$bucket"))
            .timeout(Duration.ofSeconds(10))
            .PUT(HttpRequest.BodyPublishers.noBody())
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.discarding())
        check(response.statusCode() in setOf(200, 409)) {
            "Failed to create S3 bucket $bucket: HTTP ${response.statusCode()}"
        }
        context.putOutput("$id.bucket", bucket)
        context.putOutput("$id.s3a.uri", "s3a://$bucket")
    }
}

data class GcsBucketExtension(
    override val id: String,
    val bucket: String,
    val project: String = "bigdata-test",
) : BigDataExtension {
    override val requiredServices: Set<BigDataService> = setOf(BigDataService.FAKE_GCS)
    override val events: Set<BigDataExtensionEvent> = setOf(BigDataExtensionEvent.AFTER_KIT_START)

    override fun onEvent(event: BigDataExtensionEvent, context: BigDataExtensionContext) {
        val endpoint = context.endpoint(BigDataService.FAKE_GCS).property("google.cloud.storage.host")
        val request = HttpRequest.newBuilder(URI.create("${endpoint.trimEnd('/')}/storage/v1/b?project=$project"))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("""{"name":"$bucket"}""", StandardCharsets.UTF_8))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.discarding())
        check(response.statusCode() in setOf(200, 201, 409)) {
            "Failed to create GCS bucket $bucket: HTTP ${response.statusCode()}"
        }
        context.putOutput("$id.bucket", bucket)
        context.putOutput("$id.gs.uri", "gs://$bucket")
    }
}

private val httpClient: HttpClient = HttpClient.newHttpClient()
