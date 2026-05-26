package org.openprojectx.bigdata.test.core.container

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.InternetProtocol
import org.testcontainers.utility.DockerImageName

internal class GenericBigDataContainer(image: String) :
    GenericContainer<GenericBigDataContainer>(DockerImageName.parse(image)) {
    fun withServicePort(containerPort: Int, hostPort: Int = 0): GenericBigDataContainer =
        apply {
            require(hostPort >= 0) { "Host port must be 0 for random binding or a positive fixed port" }
            if (hostPort == 0) {
                addExposedPort(containerPort)
            } else {
                addFixedExposedPort(hostPort, containerPort, InternetProtocol.TCP)
            }
        }
}
