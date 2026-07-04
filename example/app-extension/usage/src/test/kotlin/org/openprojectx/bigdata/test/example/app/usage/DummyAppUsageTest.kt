package org.openprojectx.bigdata.test.example.app.usage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.openprojectx.bigdata.test.example.app.extension.DummyAppConfigCustomizer
import org.openprojectx.bigdata.test.example.app.extension.DummyAppTest
import org.openprojectx.bigdata.test.example.app.framework.DummyApp
import org.openprojectx.bigdata.test.example.app.framework.DummyAppConfig
import org.openprojectx.bigdata.test.core.BigDataService
import org.openprojectx.bigdata.test.extensions.core.BigDataExtensionResult
import org.openprojectx.bigdata.test.extensions.junit5.BigDataExtensions
import org.openprojectx.bigdata.test.core.BigDataTestKit
import org.openprojectx.bigdata.test.junit5.BigDataTest
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.elasticsearch.ElasticsearchContainer
import org.testcontainers.utility.DockerImageName
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@BigDataTest(localStackS3 = true)
@BigDataExtensions
@DummyAppTest
@Testcontainers
class DummyAppUsageTest : DummyAppConfigCustomizer {
    override fun customize(
        kit: BigDataTestKit,
        extensionResult: BigDataExtensionResult?,
        config: DummyAppConfig,
    ): DummyAppConfig =
        config.copy(
            properties = config.properties + ("dummy.project.name" to "usage-example"),
        )

    @Test
    fun `injects started app with config derived from bigdata context`(
        app: DummyApp,
        config: DummyAppConfig,
    ) {
        assertTrue(app.started)
        assertEquals("usage-example", app.property("dummy.project.name"))
        assertEquals(config, app.config)
    }

    @Test
    fun `coexists with user managed elasticsearch testcontainer`(
        app: DummyApp,
        kit: BigDataTestKit,
    ) {
        val response = httpClient.send(
            HttpRequest.newBuilder(URI.create("http://${elasticsearch.httpHostAddress}/_cluster/health"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

        assertTrue(app.started)
        assertTrue(kit.endpoints().containsKey(BigDataService.LOCALSTACK_S3))
        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains("\"cluster_name\""))
    }

    private companion object {
        private val elasticsearchImage: DockerImageName =
            DockerImageName.parse("ghcr.io/openprojectx/dockerhub/library/elasticsearch:8.19.18")
                .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch")

        @Container
        @JvmField
        val elasticsearch: ElasticsearchContainer = ElasticsearchContainer(elasticsearchImage)
            .withEnv("xpack.security.enabled", "false")
            .withEnv("xpack.security.http.ssl.enabled", "false")

        private val httpClient: HttpClient = HttpClient.newHttpClient()
    }
}
