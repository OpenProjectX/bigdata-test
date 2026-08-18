package org.openprojectx.bigdata.test.example.junit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.openprojectx.bigdata.test.core.BigDataService
import org.openprojectx.bigdata.test.core.BigDataTestKit
import org.openprojectx.bigdata.test.junit5.BigDataTest

@BigDataTest(
    s3 = true,
    config = ["classpath:multi-instance-bigdata-test.toml"],
)
class MultiInstanceBigDataTestExample {
    @Test
    fun exposesAllNamedS3Instances(kit: BigDataTestKit) {
        val endpoints = kit.endpoints(BigDataService.S3)

        assertEquals(setOf("default", "analytics", "archive"), endpoints.keys)
        assertEquals(3, endpoints.values.map { it.port("http") }.toSet().size)
        assertNotEquals(
            kit.endpoint(BigDataService.S3).property("aws.endpoint-url.s3"),
            kit.endpoint(BigDataService.S3, "analytics").property("aws.endpoint-url.s3"),
        )
        assertTrue(
            kit.springProperties().containsKey(
                "bigdata.test.instances.analytics.s3.properties.aws.endpoint-url.s3",
            ),
        )
    }
}
