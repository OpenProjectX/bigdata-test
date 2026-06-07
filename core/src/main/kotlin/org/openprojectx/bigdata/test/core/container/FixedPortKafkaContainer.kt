package org.openprojectx.bigdata.test.core.container

import org.testcontainers.containers.InternetProtocol
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName

internal class FixedPortKafkaContainer(image: DockerImageName) : KafkaContainer(image) {
    fun withServicePort(containerPort: Int, hostPort: Int): FixedPortKafkaContainer =
        apply {
            require(hostPort > 0) { "Fixed Kafka host port must be a positive port" }
            addFixedExposedPort(hostPort, containerPort, InternetProtocol.TCP)
            addExposedPort(containerPort)
        }
}
